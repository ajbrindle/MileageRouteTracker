package com.sk7software.mileageroutetracker;

import com.sk7software.mileageroutetracker.db.DatabaseUtil;
import com.sk7software.mileageroutetracker.model.Route;
import com.sk7software.mileageroutetracker.model.RouteAddress;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import static com.sk7software.mileageroutetracker.TestUtilities.countRows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by Andrew on 07/03/2018.
 */

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class)
public class DatabaseUtilTest {

    private DatabaseUtil db;
    private int routeId;

    @Before
    public void setup() {
        RuntimeEnvironment.application.deleteDatabase(DatabaseUtil.DATABASE_NAME);
        db = DatabaseUtil.getInstance(RuntimeEnvironment.application);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void testCreateTables() {
        assertTrue(countRows("ROUTE", db) == 0);
        assertTrue(countRows("ROUTE_POINT", db) == 0);
        assertTrue(countRows("SAVED_ROUTE", db) == 0);
        assertTrue(countRows("SAVED_MARKER", db) == 0);
    }

    @Test
    public void testCreateRoute() {
        routeId = db.createRoute(54.01234, -2.08744, new Date());
        assertTrue(countRows("ROUTE", db) == 1);
        assertTrue(countRows("ROUTE_POINT", db) == 1);
        assertEquals(1, routeId);
    }

    @Test
    public void testInsertPoint() {
        routeId = db.createRoute(54.01234, -2.08744, new Date());
        db.insertRoutePoint(routeId, 54.12345, -2.98765, 2);
        assertTrue(countRows("ROUTE_POINT", db) == 2);
    }

    @Test
    public void testInsertFullRoute() {
        routeId = TestUtilities.insertFullRoute(db, new Date());
        assertTrue(countRows("ROUTE_POINT", db) == 5);
    }

    @Test
    public void testFetchRoute() {
        routeId = TestUtilities.insertFullRoute(db, new Date());
        Route r = db.fetchRoute(routeId);
        assertEquals(5, r.getPoints().size());
        assertEquals(54.01234, r.getPoints().get(0).latitude, 0.001);
        assertEquals(-2.65432, r.getPoints().get(4).longitude, 0.001);
    }

    @Test
    public void testSaveRoute() {
        routeId = TestUtilities.insertFullRoute(db, new Date());
        Route r = db.fetchRoute(routeId);
        r.setSummary("Test");
        r.setStartAddress(new RouteAddress("Start", "AB1 2CD"));
        r.setEndAddress(new RouteAddress("End", "WX9 8YZ"));
        r.setDistance(10000);
        r.setPassenger(true);
        db.saveRoute(r);
        assertTrue(countRows("SAVED_ROUTE", db) == 1);
        assertTrue(countRows("SAVED_MARKER", db) == 5);
    }

    @Test
    public void testUpdateRoute() {
        Date now = new Date();
        routeId = TestUtilities.insertFullRoute(db, now);
        Route r = db.fetchRoute(routeId);
        r.setSummary("Test");
        r.setStartAddress(new RouteAddress("Start", "AB1 2CD"));
        r.setEndAddress(new RouteAddress("End", "WX9 8YZ"));
        r.setDistance(10000);
        r.setPassenger(false);
        db.saveRoute(r);

        // Check that it is saved initially with uploaded N
        List<Route> routes = db.fetchSavedRoutes(now, true);
        r = routes.get(0);
        assertEquals("N", r.getUploaded());

        // Check that route upload is updated to Y
        db.updateUploadedRoute(r);
        routes = db.fetchSavedRoutes(now, false);
        r = routes.get(0);
        assertEquals("Y", r.getUploaded());
    }

    @Test
    public void testFetchWaypoints() {
        routeId = TestUtilities.insertFullRoute(db, new Date());
        Route r = db.fetchWaypoints(routeId);
        assertEquals(3, r.getPoints().size());
    }

    @Test
    public void testDeleteRoute() {
        routeId = TestUtilities.insertFullRoute(db, new Date());
        routeId = TestUtilities.insertFullRoute(db, new Date());
        db.deleteRoute(routeId);
        assertTrue(countRows("ROUTE_POINT", db) == 5);
        assertTrue(countRows("ROUTE", db) == 1);
    }

    @Test
    public void testFetchMarkers() {
        routeId = TestUtilities.insertFullRoute(db, new Date());
        Route r = db.fetchMarkerPoints(routeId);
        assertTrue(r.getPoints().size() == 2);
        assertEquals(54.01234, r.getPoints().get(0).latitude, 0.0001);
        assertEquals(-2.65432, r.getPoints().get(1).longitude, 0.0001);
    }

    @Test
    public void testFetchSavedRoutes() throws Exception {
        // Create 3 routes with dates yesterday, today, tomorrow
        Date date = new Date();
        date.setTime(date.getTime() - AppConstants.DATE_MS_IN_DAY);

        for (int i=0; i<3; i++) {
            routeId = TestUtilities.insertFullRoute(db, date);

            Route r = db.fetchRoute(routeId);
            r.setSummary("Test" + i);
            r.setStartAddress(new RouteAddress("Start", "AB1 2CD"));
            r.setEndAddress(new RouteAddress("End", "WX9 8YZ"));
            r.setDistance(10000);
            r.setPassenger(true);
            db.saveRoute(r);
            date.setTime(date.getTime() + AppConstants.DATE_MS_IN_DAY);
        }

        SimpleDateFormat sdf = new SimpleDateFormat(AppConstants.DATE_FORMAT);
        Date today = sdf.parse(sdf.format(new Date()));
        List<Route> routes = db.fetchSavedRoutes(today, false);
        System.out.println(sdf.format(routes.get(0).getStartTime()));
        assertEquals(1, routes.size());
        assertEquals(5, routes.get(0).getPoints().size());
        assertEquals("Test1", routes.get(0).getSummary());
    }

    @Test
    public void testDeleteRoutes() throws Exception {
        // Create 3 routes with dates 5, 4, 3 days ago
        Date date = new Date();
        date.setTime(date.getTime() - (5 * AppConstants.DATE_MS_IN_DAY));

        for (int i=0; i<3; i++) {
            routeId = TestUtilities.insertFullRoute(db, date);

            Route r = db.fetchRoute(routeId);
            r.setSummary("Test" + i);
            r.setStartAddress(new RouteAddress("Start", "AB1 2CD"));
            r.setEndAddress(new RouteAddress("End", "WX9 8YZ"));
            r.setDistance(10000);
            r.setPassenger(false);
            db.saveRoute(r);
            date.setTime(date.getTime() + AppConstants.DATE_MS_IN_DAY);
        }

        // Delete anything older than 3.5 days (should leave 1 route only in DB)
        Date cutoffDate = new Date();
        cutoffDate.setTime(cutoffDate.getTime() - (long)(3.5 * AppConstants.DATE_MS_IN_DAY));
        db.deleteRoutes(cutoffDate);
        assertEquals(5, countRows("ROUTE_POINT", db));
        assertEquals(1, countRows("SAVED_ROUTE", db));
        assertEquals(5, countRows("SAVED_MARKER", db));
        assertEquals(1, countRows("ROUTE", db));
    }
}