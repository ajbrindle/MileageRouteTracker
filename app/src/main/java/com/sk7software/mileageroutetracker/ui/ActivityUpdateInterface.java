package com.sk7software.mileageroutetracker.ui;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

public interface ActivityUpdateInterface {
    public void setProgress(boolean showProgressDialog, String progressMessage);
    public void updateMap(String infoText, List<LatLng> startEnd);
}
