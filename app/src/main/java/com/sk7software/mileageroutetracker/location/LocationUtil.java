package com.sk7software.mileageroutetracker.location;

import android.app.Activity;
import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.model.LatLng;
import com.sk7software.mileageroutetracker.model.RouteAddress;
import com.sk7software.mileageroutetracker.util.PreferencesUtil;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Created by Andrew on 27/10/2017.
 */

public class LocationUtil implements Serializable {

    private static final String TAG = LocationUtil.class.getSimpleName();

    private Geocoder geocoder;
    private GoogleApiClient googleApiClient;
    private Context context;

    private Location lastLocation;
    private Date storedDate;

    private static LocationUtil INSTANCE;

    public static LocationUtil getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new LocationUtil();
        }

        return INSTANCE;
    }

    private LocationUtil() {}

    public void init(Context context, GoogleApiClient googleApiClient, Activity activity) {
        this.context = context;
        this.geocoder = new Geocoder(context, Locale.getDefault());
        this.googleApiClient = googleApiClient;
    }

    public RouteAddress getAddress(LatLng location) {
        try {
            if (!googleApiClient.isConnected()) {
                Log.d(TAG, "Can't fetch addresses - Google API client not initialised");
                return new RouteAddress();
            }

            List<Address> addresses = null;

            addresses = geocoder.getFromLocation(
                    location.latitude, location.longitude,1);

            // Handle case where no address was found.
            if (addresses == null || addresses.size()  == 0) {
                return new RouteAddress();
            } else {
                Address address = addresses.get(0);
                return new RouteAddress(address.getAddressLine(0),
                                        address.getPostalCode());
            }
        } catch (SecurityException se) {
            Log.d(TAG, "Unable to lookup location as permissions have not been granted");
            return new RouteAddress();
        } catch (IOException ioException) {
            // Catch network or other I/O problems.
            Log.d(TAG,"Unable to lookup location - network error");
            return new RouteAddress();
        } catch (IllegalArgumentException illegalArgumentException) {
            // Catch invalid latitude or longitude values.
            Log.d(TAG, "Unable to lookup location - location error");
            return new RouteAddress();
        }
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public void setLastLocation(Location location) {
        lastLocation = location;
    }

    public void storeAddress(Location location, String addressType) {
        RouteAddress address = getAddress(new LatLng(location.getLatitude(), location.getLongitude()));
        PreferencesUtil.getInstance().addPreference(addressType,
                address.getAddressToUse());
    }

    public Location getLocationFromPostcode(String locationName) {
        try {
            Location location;
            List<Address> addresses = geocoder.getFromLocationName(locationName, 1);
            if (addresses.size() > 0) {
                location = new Location("MANUAL");
                location.setLatitude(addresses.get(0).getLatitude());
                location.setLongitude(addresses.get(0).getLongitude());
                return location;
            }
        } catch (Exception e) {
            Log.d(TAG, "Error looking up location: " + e.getMessage());
        }
        return null;
    }

    public void setStoredDate(Date date) {
        storedDate = date;
    }

    public Date getStoredDate() {
        return storedDate;
    }
}
