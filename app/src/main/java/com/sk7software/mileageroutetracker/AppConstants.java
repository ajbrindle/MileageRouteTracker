package com.sk7software.mileageroutetracker;

import java.text.DecimalFormat;

/**
 * Created by Andrew on 29/10/2017
 */

public class AppConstants {
    // Preferences constants
    public static final String APP_PREFERENCES_KEY = "SK7_MILEAGE_TRACKER_PREFS";
    public static final String PREFERENCE_ADDR_START = "PREF_ADDR_START";
    public static final String PREFERENCE_ADDR_END = "PREF_ADDR_END";
    public static final String PREFERENCE_ROUTE_ID = "PREF_ROUTE_ID";
    public static final String PREFERENCE_ROUTE_START_TIME = "PREF_ROUTE_START_TIME";
    public static final String PREFERENCE_MODE = "PREF_ROUTE_MODE";
    public static final String PREFERENCE_USER_ID = "PREF_USER";
    public static final String PREFERENCE_USER_NAME = "PREF_USER_NAME";
    public static final String PREFERENCE_USER_HOME = "PREF_USER_HOME";
    public static final String PREFERENCE_USER_WORK = "PREF_USER_WORK";
    public static final String PREFERENCE_USER_WORK_DISTANCE_M = "PREF_WORK_DISTANCE_M";

    public static final int POINT_START = 1;
    public static final int POINT_WAYPOINT = 2;
    public static final int POINT_END = 99;

    public static final int MODE_START = 0;
    public static final int MODE_STOP = 1;
    public static final int MODE_CHOOSE = 2;
    public static final int MODE_REVIEW = 3;

    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final long DATE_MS_IN_DAY = 24*60*60*1000;
    public static final String ENCODING = "UTF-8";
    public static final double METRES_TO_MILES = 0.000621371;
    public static final DecimalFormat DEC_PL_1 = new DecimalFormat(".#");
}
