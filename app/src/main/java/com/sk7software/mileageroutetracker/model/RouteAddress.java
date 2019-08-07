package com.sk7software.mileageroutetracker.model;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;

/**
 * Created by Andrew on 09/03/2018
 */

public class RouteAddress implements Serializable {
    private String line1;
    private String postCode;
    private LatLng location;

    public RouteAddress(String line1, String postCode) {
        this.line1 = line1;
        this.postCode = postCode;
    }

    public String getLine1() {
        return line1;
    }

    public void setLine1(String line1) {
        this.line1 = line1;
    }

    public String getPostCode() {
        return postCode;
    }

    public void setPostCode(String postCode) {
        this.postCode = postCode;
    }

    public LatLng getLocation() {
        return location;
    }

    public void setLocation(LatLng location) {
        this.location = location;
    }

    public String getAddressToUse() {
        if (postCode == null || postCode.trim().length() <= 4) {
            return line1;
        } else {
            return postCode;
        }
    }
}
