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
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import static com.sk7software.mileageroutetracker.AppConstants.POINT_END;
import static com.sk7software.mileageroutetracker.AppConstants.POINT_START;
import static com.sk7software.mileageroutetracker.AppConstants.POINT_WAYPOINT;

// Database util class

public class DatabaseUtil extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 2;
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
        String createTable;

        if (oldv == 0) {
            createTable =
                    "CREATE TABLE ROUTE (" +
                            "ROUTE_ID INTEGER PRIMARY KEY," +
                            "CREATED_TS INTEGER);";
            db.execSQL(createTable);

            createTable =
                    "CREATE TABLE ROUTE_POINT (" +
                            "ROUTE_ID INTEGER," +
                            "SEQ_NO INTEGER," +
                            "LAT REAL," +
                            "LON REAL," +
                            "POINT_TP INTEGER," +
                            "PRIMARY KEY (ROUTE_ID, SEQ_NO)" +
                            ");";
            db.execSQL(createTable);

            createTable =
                    "CREATE TABLE SAVED_ROUTE (" +
                            "ROUTE_ID INTEGER PRIMARY KEY," +
                            "DESCRIPTION TEXT," +
                            "DISTANCE_M REAL," +
                            "START_ADDR TEXT," +
                            "END_ADDR TEST);";
            db.execSQL(createTable);
        }

        if (oldv <= 1 && newv >= 2) {
            createTable =
                    "CREATE TABLE SAVED_MARKER (" +
                            "ROUTE_ID INTEGER," +
                            "SEQ_NO INTEGER," +
                            "LAT REAL," +
                            "LON REAL," +
                            "PRIMARY KEY (ROUTE_ID, SEQ_NO)" +
                            ");";
            db.execSQL(createTable);
        }
    }

    public int createRoute(double lat, double lon, Date date) {
        String sql = "INSERT INTO ROUTE " +
                "(route_id, created_ts) VALUES (?,?);";
        SQLiteStatement statement = database.compileStatement(sql);

        int routeId = getNextId("ROUTE", "ROUTE_ID");
        statement.bindLong(1, routeId);
        statement.bindLong(2, date.getTime());
        statement.executeInsert();
        statement.close();

        insertRoutePoint(routeId, lat, lon, POINT_START);
        return routeId;
    }

    public int createNewRoute(Date date) {
        String sql = "INSERT INTO ROUTE " +
                "(route_id, created_ts) VALUES (?,?);";
        SQLiteStatement statement = database.compileStatement(sql);

        int routeId = getNextId("ROUTE", "ROUTE_ID");
        statement.bindLong(1, routeId);
        statement.bindLong(2, date.getTime());
        statement.executeInsert();
        statement.close();
        return routeId;
    }

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

    public void saveRoute(Route r) {
        String sql = "INSERT INTO SAVED_ROUTE " +
                "(route_id, description, start_addr, end_addr, distance_m) " +
                "VALUES (?,?,?,?,?);";
        SQLiteStatement statement = database.compileStatement(sql);

        Log.d(TAG, "Saving route: " + r.getId() + ";" +
                r.getSummary() + ";" +
                r.getStartAddress() + ";" +
                r.getEndAddress() + ";" +
                r.getDistance() + ";" +
                r.getPoints().size() + " points");

        int col=1;
        statement.bindLong(col++, r.getId());
        statement.bindString(col++, r.getSummary());
        statement.bindString(col++, r.getStartAddress());
        statement.bindString(col++, r.getEndAddress());
        statement.bindLong(col++, r.getDistance());
        statement.executeInsert();
        statement.close();

        sql = "INSERT INTO SAVED_MARKER " +
                "(route_id, seq_no, lat, lon) " +
                "VALUES (?,?,?,?);";

        int i=1;

        for (LatLng ll : r.getPoints()) {
            statement = database.compileStatement(sql);
            col = 1;
            statement.bindLong(col++, r.getId());
            statement.bindLong(col++, i++);
            statement.bindDouble(col++, ll.latitude);
            statement.bindDouble(col++, ll.longitude);
            statement.executeInsert();
            statement.close();
        }
    }

    // Fetch all points on the route
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
    public Route fetchMarkerPoints(int routeId) {
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

    public List<Route> fetchSavedRoutes(Date date) {
        Cursor cursor = null;
        List<Route> routes = new ArrayList<>();
        Route r = null;

        SimpleDateFormat sdf = new SimpleDateFormat(AppConstants.DATE_TIME_FORMAT);
        Long startDate = date.getTime();
        Long endDate = date.getTime() + AppConstants.DATE_MS_IN_DAY;

        String sql = "SELECT r.route_id, r.created_ts, s.description, s.start_addr, s.end_addr, " +
                "s.distance_m, m.lat, m.lon " +
                "FROM ROUTE r, SAVED_ROUTE s, SAVED_MARKER m " +
                "WHERE r.route_id = s.route_id " +
                "AND s.route_id = m.route_id " +
                "AND r.created_ts >= ? " +
                "AND r.created_ts < ? " +
                "ORDER BY r.created_ts, r.route_id, m.seq_no";
        try {
            int lastId = -1;

            cursor = database.rawQuery(sql, new String[]{String.valueOf(startDate), String.valueOf(endDate)});
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
                }

                LatLng ll = new LatLng(cursor.getDouble(6), cursor.getDouble(7));
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

    public void deleteRoute(int routeId) {
        String sql = "DELETE FROM ROUTE_POINT WHERE ROUTE_ID = ?";
        SQLiteStatement statement = database.compileStatement(sql);

        statement.bindLong(1, routeId);
        statement.executeUpdateDelete();

        sql = "DELETE FROM ROUTE WHERE ROUTE_ID = ?";
        statement = database.compileStatement(sql);

        statement.bindLong(1, routeId);
        statement.executeUpdateDelete();
    }

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

}