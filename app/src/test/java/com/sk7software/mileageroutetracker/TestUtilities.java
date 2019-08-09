package com.sk7software.mileageroutetracker;

import android.database.Cursor;

import com.sk7software.mileageroutetracker.db.DatabaseUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

/**
 * Created by Andrew on 07/03/2018.
 */

public class TestUtilities {
    public static final long TEST_TIME = 1501022845003L;

    public static int countRows(String tableName, DatabaseUtil db) {
        Cursor c = null;
        try {
            c = db.getDatabase().rawQuery("SELECT COUNT(*) FROM " + tableName, null);
            if (c != null) {
                c.moveToFirst();
                return c.getInt(0);
            }

            return -1;

        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    public static int insertFullRoute(DatabaseUtil db, Date date) {
        int routeId = db.createRoute(54.01234, -2.08744, date);
        db.insertRoutePoint(routeId, 54.12345, -2.98765, 2);
        db.insertRoutePoint(routeId, 54.23456, -2.87655, 2);
        db.insertRoutePoint(routeId, 54.34567, -2.76543, 2);
        db.insertRoutePoint(routeId, 54.45678, -2.65432, 99);
        return routeId;
    }

    public static JSONObject fetchJSON(InputStream in) {
        try {
            BufferedReader streamReader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder responseStrBuilder = new StringBuilder();

            String inputStr;
            while ((inputStr = streamReader.readLine()) != null)
                responseStrBuilder.append(inputStr);

            return new JSONObject(responseStrBuilder.toString());
        } catch (IOException ie) {
            return null;
        } catch (JSONException je) {
            return null;
        }
    }
}
