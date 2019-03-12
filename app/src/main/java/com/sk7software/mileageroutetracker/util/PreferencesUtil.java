package com.sk7software.mileageroutetracker.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.sk7software.mileageroutetracker.AppConstants;

/**
 * Created by andre_000 on 06/07/2017.
 */

public class PreferencesUtil {

    private static PreferencesUtil instance;
    private final SharedPreferences prefs;

    private PreferencesUtil(Context context) {
        prefs = context.getSharedPreferences(AppConstants.APP_PREFERENCES_KEY, Context.MODE_PRIVATE);
    }

    public synchronized static void init(Context context) {
        if (instance == null) {
            instance = new PreferencesUtil(context);
        }
    }

    public static PreferencesUtil getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Preferences not initialised");
        } else {
            return instance;
        }
    }

    public void addPreference(String name, String value) {
        prefs.edit().putString(name, value).commit();
    }

    public void addPreference(String name, int value) {
        prefs.edit().putInt(name, value).commit();
    }

    public void addPreference(String name, long value) {
        prefs.edit().putLong(name, value).commit();
    }

    public void addPreference(String name, boolean value) {
        prefs.edit().putBoolean(name, value).commit();
    }

    public void addPreference(String name, float value) {
        prefs.edit().putFloat(name, value).commit();
    }

    public String getStringPreference(String name) {
        return prefs.getString(name, "");
    }

    public int getIntPreference(String name) {
        return prefs.getInt(name, 0);
    }

    public long getLongPreference(String name) {
        return prefs.getLong(name, 0);
    }

    public float getFloatPreference(String name) { return prefs.getFloat(name, 0); }

    public void clearAllPreferences() {
        prefs.edit().clear().commit();
    }

    public static void reset() {
        instance = null;
    }

    public boolean getBooleanPreference(String name) {
        return prefs.getBoolean(name, false);
    }

    public void clearStringPreference(String name) {
        prefs.edit().putString(name, "").commit();
    }
}
