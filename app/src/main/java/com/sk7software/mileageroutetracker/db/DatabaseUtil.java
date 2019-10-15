package com.sk7software.mileageroutetracker.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.mileageroutetracker.AppConstants;
import com.sk7software.mileageroutetracker.model.Route;
import com.sk7software.mileageroutetracker.model.RouteAddress;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static com.sk7software.mileageroutetracker.AppConstants.POINT_END;
import static com.sk7software.mileageroutetracker.AppConstants.POINT_START;
import static com.sk7software.mileageroutetracker.AppConstants.POINT_WAYPOINT;

// Database util class

public class DatabaseUtil extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 3;
    public static final String DATABASE_NAME = "com.sk7software.mileageroutetracker.db";
    private static final String TAG = DatabaseUtil.class.getSimpleName();
    private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat(AppConstants.DATE_TIME_FORMAT);
    private static DatabaseUtil dbInstance;

    private SQLiteDatabase database;

    public static synchronized DatabaseUtil getInstance(Context context) {
        if (dbInstance == null) {
            dbInstance = new DatabaseUtil(context);
            dbInstance.database = dbInstance.getSQLiteDatabase(context);
        }

        return dbInstance;
    }

    private DatabaseUtil(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Log.d(TAG, "DB constructor");
    }


    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "DB onCreate()");
        initialise(db, 0, DATABASE_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldv, int newv) {
        Log.d(TAG, "DB onUpgrade()");
        initialise(db, oldv, newv);
    }

    @Override
    public void close() {
        super.close();
        dbInstance = null;
        Log.d(TAG, "DB close()");
    }

    private void initialise(SQLiteDatabase db, int oldv, int newv) {
        Log.d(TAG, "DB initialise()");
        String sqlStr;

        if (oldv == 0) {
            // Master table that records all routes that are started by the user
            sqlStr =
                    "CREATE TABLE ROUTE (" +
                            "ROUTE_ID INTEGER PRIMARY KEY," +
                            "CREATED_TS INTEGER);";
            db.execSQL(sqlStr);

            // This records points on the route as they happen i.e. start, end, waypoints
            sqlStr =
                    "CREATE TABLE ROUTE_POINT (" +
                            "ROUTE_ID INTEGER," +
                            "SEQ_NO INTEGER," +
                            "LAT REAL," +
                            "LON REAL," +
                            "POINT_TP INTEGER," +
                            "PRIMARY KEY (ROUTE_ID, SEQ_NO)" +
                            ");";
            db.execSQL(sqlStr);

            // This stores the information that will be used when the route is recalled in future,
            // or is uploaded.
            sqlStr =
                    "CREATE TABLE SAVED_ROUTE (" +
                            "ROUTE_ID INTEGER PRIMARY KEY," +
                            "DESCRIPTION TEXT," +
                            "DISTANCE_M REAL," +
                            "START_ADDR TEXT," +
                            "END_ADDR TEST);";
            db.execSQL(sqlStr);
        }

        if (oldv <= 1 && newv >= 2) {
            Log.d(TAG, "Creating version: " + newv);

            // This stores all the marker points on the route, as given by the route planning
            // call.  It is only populated once the route has been selected and saved
            sqlStr =
                    "CREATE TABLE SAVED_MARKER (" +
                            "ROUTE_ID INTEGER," +
                            "SEQ_NO INTEGER," +
                            "LAT REAL," +
                            "LON REAL," +
                            "PRIMARY KEY (ROUTE_ID, SEQ_NO)" +
                            ");";
            db.execSQL(sqlStr);
        }

        if (oldv <= 2 && newv >= 3) {
            Log.d(TAG, "Creating version: " + newv);

            // Add a field to indicate whether the route has been uploaded
            sqlStr =
                    "ALTER TABLE SAVED_ROUTE " +
                            "ADD UPLOAD_IN VARCHAR;";
            db.execSQL(sqlStr);

            // Add a field to store whether there was a passenger recorded for the route
            sqlStr =
                    "ALTER TABLE SAVED_ROUTE " +
                            "ADD PASSENGER_IN VARCHAR;";
            db.execSQL(sqlStr);

            sqlStr =
                    "UPDATE SAVED_ROUTE " +
                            "SET UPLOAD_IN = 'Y', PASSENGER_IN = 'N';";
            db.execSQL(sqlStr);

            sqlStr =
                    "CREATE INDEX uploaded " +
                            "ON SAVED_ROUTE(UPLOAD_IN);";
            db.execSQL(sqlStr);
        }
    }

    // Create a route with the next available id, with the specified start time and location
    public int createRoute(double lat, double lon, Date date) {
        String sql = "INSERT INTO ROUTE " +
                "(route_id, created_ts) VALUES (?,?);";
        SQLiteStatement statement = database.compileStatement(sql);

        int routeId = getNextIdFromSaved();
        statement.bindLong(1, routeId);
        statement.bindLong(2, date.getTime());
        statement.executeInsert();
        statement.close();

        insertRoutePoint(routeId, lat, lon, POINT_START);
        return routeId;
    }

    // Create a new route with the next available id and the specified start time
    // Start point will be supplied later
    public int createNewRoute(Date date) {
        String sql = "INSERT INTO ROUTE " +
                "(route_id, created_ts) VALUES (?,?);";
        SQLiteStatement statement = database.compileStatement(sql);

        int routeId = getNextIdFromSaved();
        statement.bindLong(1, routeId);
        statement.bindLong(2, date.getTime());
        statement.executeInsert();
        statement.close();
        return routeId;
    }

    // Add a route point as it is captured (i.e. start point, end point or waypoint)
    public void insertRoutePoint(int routeId, double lat, double lon, int pointType) {
        String sql = "INSERT INTO ROUTE_POINT " +
                "(route_id, seq_no, lat, lon, point_tp) " +
                "VALUES (?,?,?,?,?);";
        SQLiteStatement statement = database.compileStatement(sql);
        int seqNo = 1;

        if (pointType != POINT_START) {
            seqNo = getNextId("ROUTE_POINT", "SEQ_NO");
        }

        int col=1;
        statement.bindLong(col++, routeId);
        statement.bindLong(col++, seqNo);
        statement.bindDouble(col++, lat);
        statement.bindDouble(col++, lon);
        statement.bindLong(col++, pointType);
        statement.executeInsert();
        statement.close();;
    }

    // Save the route
    public void saveRoute(Route r) {
        String sql = "INSERT INTO SAVED_ROUTE " +
                "(route_id, description, start_addr, end_addr, distance_m, upload_in, passenger_in) " +
                "VALUES (?,?,?,?,?,?,?);";
        SQLiteStatement statement = database.compileStatement(sql);

        Log.d(TAG, "Saving route: " + r.getId() + ";" +
                r.getSummary() + ";" +
                r.getStartAddress() + ";" +
                r.getEndAddress() + ";" +
                r.getDistance() + ";" +
                r.getPoints().size() + " points");

        int col = 1;
        statement.bindLong(col++, r.getId());
        statement.bindString(col++, r.getSummary());
        statement.bindString(col++, r.getStartAddress());
        statement.bindString(col++, r.getEndAddress());
        statement.bindLong(col++, r.getDistance());
        statement.bindString(col++, "N");
        statement.bindString(col++, r.getPassenger());
        statement.executeInsert();
        statement.close();

        saveMarkers(r);
    }

    // Save the marker points on the route
    public void saveMarkers(Route r) {
        String sql = "INSERT INTO SAVED_MARKER " +
                "(route_id, seq_no, lat, lon) " +
                "VALUES (?,?,?,?);";
        SQLiteStatement statement = database.compileStatement(sql);
        int col = 1;
        int i=1;

        for (LatLng ll : r.getPoints()) {
            statement = database.compileStatement(sql);
            col = 1;
            statement.bindLong(col++, r.getId());
            statement.bindLong(col++, i++);
            statement.bindDouble(col++, ll.latitude);
            statement.bindDouble(col++, ll.longitude);

            try {
                statement.executeInsert();
            } catch (Exception e) {
                Log.d(TAG, "Database exception: " + e.getMessage());
                statement.close();
                break;
            }
            statement.close();
        }
    }

    // Update saved route details if they were not available at original time of save
    public void updateSavedRoute(Route r) {
        String sql = "UPDATE SAVED_ROUTE " +
                "SET distance_m = ?, " +
                "    start_addr = ?," +
                "    end_addr = ? " +
                "WHERE route_id = ?;";
        SQLiteStatement statement = database.compileStatement(sql);

        Log.d(TAG, "Updating route: " + r.getId());

        statement.bindLong(1, r.getDistance());
        statement.bindString(2, r.getStartAddress());
        statement.bindString(3, r.getEndAddress());
        statement.bindLong(4, r.getId());
        statement.executeUpdateDelete();
        statement.close();
    }

    // Update uploaded indicator on saved route
    public void updateUploadedRoute(Route r) {
        String sql = "UPDATE SAVED_ROUTE " +
                "SET upload_in = 'Y' " +
                "WHERE route_id = ?;";
        SQLiteStatement statement = database.compileStatement(sql);

        Log.d(TAG, "Updating route: " + r.getId());

        statement.bindLong(1, r.getId());
        statement.executeUpdateDelete();
        statement.close();
    }

    // Fetch all points captured while recording the route
    public Route fetchRoute(int routeId) {
        Cursor cursor = null;
        Route route = new Route();
        route.setId(routeId);
        try {
            cursor = database.query("ROUTE_POINT", new String[]{"SEQ_NO", "LAT", "LON", "POINT_TP"},
                    "ROUTE_ID=?", new String[] {String.valueOf(routeId)}, null, null, "SEQ_NO", null);
            while (cursor.moveToNext()) {
                LatLng ll = new LatLng(cursor.getDouble(1), cursor.getDouble(2));
                route.addPoint(ll);
            }
        } finally {
            cursor.close();
        }
        return route;
    }

    // Fetch timestamp of start of thenroute
    public long fetchRouteTimestamp(int routeId) {
        Cursor cursor = null;
        long timestamp = 0;

        try {
            cursor = database.query("ROUTE", new String[]{"CREATED_TS"},
                    "ROUTE_ID=?", new String[] {String.valueOf(routeId)}, null, null, null, null);
            while (cursor.moveToNext()) {
                timestamp = cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }
        return timestamp;
    }

    // Fetch all waypoints captured for the route
    public Route fetchWaypoints(int routeId) {
        Cursor cursor = null;
        Route route = new Route();
        route.setId(routeId);
        try {
            cursor = database.query("ROUTE_POINT", new String[]{"SEQ_NO", "LAT", "LON", "POINT_TP"},
                    "ROUTE_ID=? AND POINT_TP=" + POINT_WAYPOINT, new String[] {String.valueOf(routeId)}, null, null, "SEQ_NO", null);
            while (cursor.moveToNext()) {
                LatLng ll = new LatLng(cursor.getDouble(1), cursor.getDouble(2));
                route.addPoint(ll);
            }
        } finally {
            cursor.close();
        }
        return route;
    }

    // Returns just the start and end points from a route
    public Route fetchStartEndPoints(int routeId) {
        Cursor cursor = null;
        Route route = new Route();
        route.setId(routeId);
        try {
            cursor = database.query("ROUTE_POINT", new String[]{"SEQ_NO", "LAT", "LON", "POINT_TP"},
                    "ROUTE_ID=? AND (POINT_TP=" + POINT_START + " OR POINT_TP=" + POINT_END + ")",
                     new String[] {String.valueOf(routeId)}, null, null, "SEQ_NO", null);
            while (cursor.moveToNext()) {
                LatLng ll = new LatLng(cursor.getDouble(1), cursor.getDouble(2));
                route.addPoint(ll);
            }
        } finally {
            cursor.close();
        }
        return route;
    }

    // Fetch all routes saved on/from a particular date
    // If just requesting ones that have failed upload, get all from the date
    public List<Route> fetchSavedRoutes(Date date, boolean uploadFailedOnly) {
        Cursor cursor = null;
        List<Route> routes = new ArrayList<>();
        Route r = null;

        SimpleDateFormat sdf = new SimpleDateFormat(AppConstants.DATE_TIME_FORMAT);
        Long startDate = date.getTime();
        Long endDate = date.getTime() + AppConstants.DATE_MS_IN_DAY;

        if (uploadFailedOnly) {
            // Check for a year from selected date
            endDate = date.getTime() + (365 * AppConstants.DATE_MS_IN_DAY);
        }

        String sql = "SELECT r.route_id, r.created_ts, s.description, s.start_addr, s.end_addr, " +
                "s.distance_m, s.upload_in, m.lat, m.lon " +
                "FROM ROUTE r, SAVED_ROUTE s, SAVED_MARKER m " +
                "WHERE r.route_id = s.route_id " +
                "AND s.route_id = m.route_id " +
                "AND r.created_ts >= ? " +
                "AND r.created_ts < ? " +
                (uploadFailedOnly ? "AND upload_in = 'N' " : "") +
                "ORDER BY r.created_ts, r.route_id, m.seq_no";
        try {
            int lastId = -1;

            cursor = database.rawQuery(sql, new String[]{String.valueOf(startDate), String.valueOf(endDate)});
            while (cursor.moveToNext()) {
                int routeId = cursor.getInt(0);
                if (routeId != lastId) {
                    // This is a new route so add the previous one to the list
                    if (r != null) {
                        routes.add(r);
                    }

                    // Create new route
                    r = new Route();
                    lastId = routeId;
                    r.setId(cursor.getInt(0));
                    r.setStartTime(new Date(cursor.getLong(1)));
                    r.setSummary(cursor.getString(2));
                    r.setStartAddress(new RouteAddress(cursor.getString(3), ""));
                    r.setEndAddress(new RouteAddress(cursor.getString(4), ""));
                    r.setDistance(cursor.getInt(5));
                    r.setUploaded(cursor.getString(6));
                }

                LatLng ll = new LatLng(cursor.getDouble(7), cursor.getDouble(8));
                r.addPoint(ll);
            }
        } finally {
            cursor.close();
            if (r != null) {
                routes.add(r);
            }
        }
        return routes;
    }

    // Get all the routes that haven't been uploaded
    public List<Route> fetchRoutesNotUploaded() {
        Cursor cursor = null;
        List<Route> routes = new ArrayList<>();
        Route r = null;

        String sql = "SELECT r.route_id, r.created_ts, s.description, s.start_addr, s.end_addr, " +
                "s.distance_m, s.passenger_in " +
                "FROM ROUTE r, SAVED_ROUTE s " +
                "WHERE r.route_id = s.route_id " +
                "AND s.upload_in = 'N' " +
                "ORDER BY r.created_ts";
        try {
            int lastId = -1;

            cursor = database.rawQuery(sql, null);
            while (cursor.moveToNext()) {
                int routeId = cursor.getInt(0);
                if (routeId != lastId) {
                    // This is a new route
                    if (r != null) {
                        routes.add(r);
                    }
                    r = new Route();
                    lastId = routeId;
                    r.setId(cursor.getInt(0));
                    r.setStartTime(new Date(cursor.getLong(1)));
                    r.setSummary(cursor.getString(2));
                    r.setStartAddress(new RouteAddress(cursor.getString(3), ""));
                    r.setEndAddress(new RouteAddress(cursor.getString(4), ""));
                    r.setDistance(cursor.getInt(5));

                    String passengerIn = cursor.getString(6);
                    r.setPassenger("Y".equals(passengerIn));
                }
            }
        } finally {
            cursor.close();
            if (r != null) {
                routes.add(r);
            }
        }
        return routes;
    }

    // Find next available id from routes and saved routes
    private int getNextIdFromSaved() {
        int maxId1 = getNextId("ROUTE", "ROUTE_ID");
        int maxId2 = getNextId("SAVED_ROUTE", "ROUTE_ID");
        if (maxId2 > maxId1) {
            return maxId2;
        } else {
            return maxId1;
        }
    }

    // Find next available id
    private int getNextId(String table, String column) {
        int maxId = 1;
        Cursor cursor = null;
        try {
            cursor = database.query(table, new String[]{"MAX(" + column + ") AS max_id"},
                    null, null, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                cursor.moveToFirst();
                int index = cursor.getColumnIndex("max_id");
                maxId = cursor.getInt(index);
                maxId++;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return maxId;
    }

    // Remove all saved markers for a route (allows them to be replaced if the route is
    // subsequently calculated)
    public void deleteSavedMarkers(int routeId) {
        String sql = "DELETE FROM SAVED_MARKER WHERE ROUTE_ID = ?";
        SQLiteStatement statement = database.compileStatement(sql);

        statement.bindLong(1, routeId);
        statement.executeUpdateDelete();
        statement.close();
    }


    // Delete route and points
    public void deleteRoute(int routeId) {
        String sql = "DELETE FROM ROUTE_POINT WHERE ROUTE_ID = ?";
        SQLiteStatement statement = database.compileStatement(sql);

        statement.bindLong(1, routeId);
        statement.executeUpdateDelete();
        statement.close();

        sql = "DELETE FROM ROUTE WHERE ROUTE_ID = ?";
        statement = database.compileStatement(sql);

        statement.bindLong(1, routeId);
        statement.executeUpdateDelete();
        statement.close();
    }

    // Delete saved route and points
    public void deleteSavedRoute(int routeId) {
        deleteSavedMarkers(routeId);

        String sql = "DELETE FROM SAVED_ROUTE WHERE ROUTE_ID = ?";
        SQLiteStatement statement = database.compileStatement(sql);

        statement.bindLong(1, routeId);
        statement.executeUpdateDelete();
        statement.close();
    }

    // Only used in test at the moment
    public void deleteRoutes(Date date) {
        String sql = "DELETE FROM ROUTE_POINT WHERE ROUTE_ID IN " +
                "(SELECT ROUTE_ID FROM ROUTE WHERE CREATED_TS <= ?)";
        SQLiteStatement statement = database.compileStatement(sql);

        statement.bindLong(1, date.getTime());
        statement.executeUpdateDelete();

        sql = "DELETE FROM SAVED_MARKER WHERE ROUTE_ID IN " +
                "(SELECT ROUTE_ID FROM ROUTE WHERE CREATED_TS <= ?)";
        statement = database.compileStatement(sql);

        statement.bindLong(1, date.getTime());
        statement.executeUpdateDelete();

        sql = "DELETE FROM SAVED_ROUTE WHERE ROUTE_ID IN " +
                "(SELECT ROUTE_ID FROM ROUTE WHERE CREATED_TS <= ?)";
        statement = database.compileStatement(sql);

        statement.bindLong(1, date.getTime());
        statement.executeUpdateDelete();

        sql = "DELETE FROM ROUTE WHERE CREATED_TS <= ?";
        statement = database.compileStatement(sql);

        statement.bindLong(1, date.getTime());
        statement.executeUpdateDelete();
    }

    private SQLiteDatabase getSQLiteDatabase(Context context) {
        return getInstance(context).getWritableDatabase();
    }

    public SQLiteDatabase getDatabase() {
        return database;
    }

    public void debugLogging(int numRows) {
        Cursor cursor = null;
        List<Route> routes = new ArrayList<>();
        Route r = null;

        String sql = "SELECT r.route_id, r.created_ts " +
                "FROM ROUTE r " +
                "ORDER BY r.route_id DESC LIMIT " + numRows;
        try {
            cursor = database.rawQuery(sql, null);
            while (cursor.moveToNext()) {
                Log.d(TAG, "ROUTE: " + cursor.getInt(0) + " (" + new Date(cursor.getLong(1)) + ")");
            }

            cursor.close();
            sql = "SELECT s.route_id, s.description, s.start_addr, s.end_addr, " +
                    "s.distance_m, s.passenger_in " +
                    "FROM SAVED_ROUTE s " +
                    "ORDER BY s.route_id DESC LIMIT " + numRows;

            cursor = database.rawQuery(sql, null);
            while (cursor.moveToNext()) {
                Log.d(TAG, "SAVED_ROUTE: " + cursor.getInt(0) + " - " +
                        cursor.getString(1) + "; " +
                        cursor.getString(2) + "; " +
                        cursor.getString(3) + "; " +
                        cursor.getInt(4) + "m");
            }
            cursor.close();
            sql = "SELECT s.route_id, count(*) " +
                    "FROM SAVED_MARKER s " +
                    "GROUP BY s.route_id " +
                    "ORDER BY s.route_id DESC LIMIT " + numRows;

            cursor = database.rawQuery(sql, null);
            while (cursor.moveToNext()) {
                Log.d(TAG, "SAVED_MARKER: " + cursor.getInt(0) + " - " +
                        cursor.getInt(1) + "; " + " points");
            }
        } finally {
            cursor.close();
        }
    }
}