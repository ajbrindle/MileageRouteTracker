package com.sk7software.mileageroutetracker.model;

import java.io.Serializable;

/**
 * Created by Andrew on 09/03/2018
 */

public class RouteAddress implements Serializable {
    private String line1;
    private String postCode;

    private static final String UNKNOWN_ADDRESS = "Unknown";

    public RouteAddress(String line1, String postCode) {
        this.line1 = line1;
        this.postCode = postCode;
    }

    public RouteAddress() {
        this.line1 = UNKNOWN_ADDRESS;
        this.postCode = UNKNOWN_ADDRESS;
    }

    public boolean isUnknown() {
        return (UNKNOWN_ADDRESS.equals(line1) && UNKNOWN_ADDRESS.equals(postCode))
                 || (UNKNOWN_ADDRESS.equals(line1) && "".equals(postCode))
                 || ("".equals(line1) && UNKNOWN_ADDRESS.equals(postCode));
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

    public String getAddressToUse() {
        if (postCode == null || postCode.trim().length() <= 4) {
            return line1;
        } else {
            return postCode;
        }
    }
}
