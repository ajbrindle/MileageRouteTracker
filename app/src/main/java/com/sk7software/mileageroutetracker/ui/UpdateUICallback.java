package com.sk7software.mileageroutetracker.ui;

import com.sk7software.mileageroutetracker.model.Route;

import java.util.List;

/**
 * Created by Andrew on 04/03/2018
 */

public interface UpdateUICallback {
    void updateUI(List<Route> routes);
}
