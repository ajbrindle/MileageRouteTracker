package com.sk7software.mileageroutetracker.util;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.sk7software.mileageroutetracker.AppConstants;
import com.sk7software.mileageroutetracker.db.DatabaseUtil;
import com.sk7software.mileageroutetracker.model.RouteAddress;
import com.sk7software.mileageroutetracker.ui.MapsActivity;

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

    private Location storedLocation;
    private Location lastLocation;
    private String storedAddress;
    private Date storedDate;

    private static final RouteAddress UNKNOWN_ADDRESS = new RouteAddress("Unknown", "Unknown");

    public LocationUtil(Context context, GoogleApiClient googleApiClient, Activity activity) {
        this.context = context;
        this.geocoder = new Geocoder(context, Locale.getDefault());
        this.googleApiClient = googleApiClient;
    }

    public RouteAddress getAddress(double lat, double lon) {
        try {
            if (!googleApiClient.isConnected()) return UNKNOWN_ADDRESS;

            List<Address> addresses = null;

            addresses = geocoder.getFromLocation(
                    lat, lon,1);

            // Handle case where no address was found.
            if (addresses == null || addresses.size()  == 0) {
                return UNKNOWN_ADDRESS;
            } else {
                Address address = addresses.get(0);
                return new RouteAddress(address.getAddressLine(0),
                                        address.getPostalCode());
            }
        } catch (SecurityException se) {
            Log.d(TAG, "Unable to lookup location as permissions have not been granted");
            return UNKNOWN_ADDRESS;
        } catch (IOException ioException) {
            // Catch network or other I/O problems.
            Log.d(TAG,"Unable to lookup location - network error");
            return UNKNOWN_ADDRESS;
        } catch (IllegalArgumentException illegalArgumentException) {
            // Catch invalid latitude or longitude values.
            Log.d(TAG, "Unable to lookup location - location error");
            return UNKNOWN_ADDRESS;
        }
    }

    public Location getLastLocation() {
        return lastLocation;
    }

    public void setLastLocation(Location location) {
        lastLocation = location;
    }

    public void storeAddress(Location location, String addressType) {
        RouteAddress address = getAddress(location.getLatitude(), location.getLongitude());
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

    public Location getStoredLocation() {
        return storedLocation;
    }

    public String getStoredAddress() {
        return storedAddress;
    }

    public void setStoredDate(Date date) {
        storedDate = date;
    }

    public Date getStoredDate() {
        return storedDate;
    }
}
