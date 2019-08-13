package com.sk7software.mileageroutetracker.location;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.mileageroutetracker.AppConstants;
import com.sk7software.mileageroutetracker.db.DatabaseUtil;
import com.sk7software.mileageroutetracker.ui.MapsActivityUpdateInterface;
import com.sk7software.mileageroutetracker.util.PreferencesUtil;

import java.util.List;

public class RouteLocationListener implements LocationListener {

    private int routeId;
    private boolean first;
    private int pointType;
    private MapsActivityUpdateInterface uiInterface;
    private Context context;

    private static final String TAG = RouteLocationListener.class.getSimpleName();

    public RouteLocationListener(int routeId, boolean starting,
                                 MapsActivityUpdateInterface uiInterface, Context context) {
        this.routeId = routeId;
        this.uiInterface = uiInterface;
        this.context = context;

        // Determine which type of point is being listened for (waypoint or start point)
        first = starting;
        this.pointType = AppConstants.POINT_WAYPOINT;

        if (first) {
            pointType = AppConstants.POINT_START;
        }

        uiInterface.setProgress(true, "Acquiring Location...");
    }

    @Override
    public void onLocationChanged(Location location) {
        // Update running record of last location
        Log.d(TAG, "Location changed: " + location);

        LocationUtil loc = LocationUtil.getInstance();

        /****************************************************
         * TESTING ONLY
         ****************************************************
        if (first) {
            location.setLatitude(53.3874803);;
            location.setLongitude(-2.1505159);
        } else {
            location.setLatitude(53.4816282);;
            location.setLongitude(-2.2046857);
        }
        /****************************************************
         * TESTING ONLY
         *****************************************************/

        loc.setLastLocation(location);

        // Store point in database
        DatabaseUtil.getInstance(context)
                .insertRoutePoint(routeId,
                        location.getLatitude(),
                        location.getLongitude(),
                        pointType);

        // On first update, set start address and set up map with marker
        if (first) {
            loc.storeAddress(location, AppConstants.PREFERENCE_ADDR_START);
            List<LatLng> startEnd = DatabaseUtil.getInstance(context)
                    .fetchStartEndPoints(routeId).getPoints();
            String infoText = "Route id: " + routeId + " started at: " +
                    PreferencesUtil.getInstance().getStringPreference(AppConstants.PREFERENCE_ADDR_START);

            uiInterface.updateMap(infoText, startEnd);
        }

        first = false;
        pointType = AppConstants.POINT_WAYPOINT;

        uiInterface.setProgress(false, null);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
    }

    @Override
    public void onProviderEnabled(String s) {
    }

    @Override
    public void onProviderDisabled(String s) {
    }
}
