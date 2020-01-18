package com.sk7software.mileageroutetracker.model;

import android.graphics.Color;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.mileageroutetracker.AppConstants;
import com.sk7software.mileageroutetracker.location.LocationUtil;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.sk7software.mileageroutetracker.AppConstants.DEC_PL_1;
import static com.sk7software.mileageroutetracker.AppConstants.METRES_TO_MILES;

/**
 * Created by Andrew on 03/03/2018
 */

public class Route implements Serializable {

    private static int ID = 1;

    public enum RouteType {
        ROUTE_TAKEN,
        ROUTE_SUGGESTION;
    }

    private int userId;
    private int id;
    private Date startTime;
    private transient List<LatLng> points;
    private int distance;
    private RouteType type;
    private String summary;
    private int colour;
    private RouteAddress startAddress;
    private RouteAddress endAddress;
    private String passenger;
    private String uploaded;
    private int adjustedDistance;
    private LatLng start;
    private LatLng end;

    public Route() {
        synchronized(this) {
            id = ID++;
        }
        points = new ArrayList<>();
        colour = Color.RED;
    }

    public int getUserId() { return userId; }

    public void setUserId(int userId) { this.userId = userId; }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public List<LatLng> getPoints() {
        return points;
    }

    public void setPoints(List<LatLng> points) {
        this.points = points;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public RouteType getType() {
        return type;
    }

    public void setType(RouteType type) {
        this.type = type;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public int getColour() {
        return colour;
    }

    public void setColour(int colour) {
        this.colour = colour;
    }

    public void setStartAddress(RouteAddress address) {
        startAddress = address;
    }

    public void setEndAddress(RouteAddress address) {
        endAddress = address;
    }

    public String getStartAddress() {
        return startAddress.getAddressToUse();
    }

    public String getEndAddress() {
        return endAddress.getAddressToUse();
    }

    public String getPassenger() {
        return passenger;
    }

    public void setPassenger(boolean hasPassenger) {
        this.passenger = (hasPassenger ? "Y" : "N");
    }

    public String getUploaded() {
        return uploaded;
    }

    public void setUploaded(String uploaded) {
        this.uploaded = uploaded;
    }

    public boolean isStartUnknown() {
        return startAddress.isUnknown();
    }

    public boolean isEndUnknown() {
        return endAddress.isUnknown();
    }

    public int getAdjustedDistance() {
        return adjustedDistance;
    }

    public void setAdjustedDistance(int adjustedDistance) {
        this.adjustedDistance = adjustedDistance;
    }

    public void addPoint(LatLng ll) {
        points.add(ll);
    }

    public String getFormattedDistance() {
        if (distance >= 0) {
            double miles;
            miles = distance * METRES_TO_MILES;
            return DEC_PL_1.format(miles) + " miles";
        } else {
            return "unknown distance";
        }
    }

    public void lookupAddresses(LocationUtil loc) {
        if (points.size() > 0) {
            LatLng start = new LatLng(points.get(0).latitude,
                    points.get(0).longitude);
            LatLng end = new LatLng(points.get(points.size()-1).latitude,
                    points.get(points.size()-1).longitude);
            startAddress = loc.getAddress(start);
            endAddress = loc.getAddress(end);
        }
    }

    public void setStartEnd() {
        if (points.size() > 0) {
            start = points.get(0);
            end = points.get(points.size()-1);
        }
    }
}
