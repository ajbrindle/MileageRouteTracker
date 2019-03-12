package com.sk7software.mileageroutetracker.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.sk7software.mileageroutetracker.AppConstants;
import com.sk7software.mileageroutetracker.db.DatabaseUtil;
import com.sk7software.mileageroutetracker.util.LocationUtil;
import com.sk7software.mileageroutetracker.R;
import com.sk7software.mileageroutetracker.model.Route;
import com.sk7software.mileageroutetracker.network.FetchUrl;
import com.sk7software.mileageroutetracker.util.PreferencesUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static com.sk7software.mileageroutetracker.AppConstants.MODE_CHOOSE;
import static com.sk7software.mileageroutetracker.AppConstants.MODE_REVIEW;
import static com.sk7software.mileageroutetracker.AppConstants.MODE_START;
import static com.sk7software.mileageroutetracker.AppConstants.MODE_STOP;

public class MapsActivity extends AppCompatActivity
        implements OnMapReadyCallback, EndJourneyDialogFragment.OnDialogDismissListener,
        EnterJourneyDialogFragment.OnDialogDismissListener {

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private List<Route> routes = new ArrayList<>();
    private LocationUtil loc;
    private RouteLocationListener locationListener;
    private LocationManager locationManager;
    private Button btnStart;
    private Button btnStop;
    private Button btnChoose;
    private Button btnPrev;
    private Button btnCancel;
    private Button btnNext;
    private TextView txtInformation;

    private int reviewIdx;
    private int userId;

    public static final long REFRESH_INTERVAL = 1000 * 60 * 2; // 2 minutes
    public static final int MIN_REFRESH_DISTANCE = 50;

    private static final String TAG = MapsActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // Check permission granted to use GPS
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        // Initialise preferences
        PreferencesUtil.init(getApplicationContext());
        int mode = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_MODE);
        userId = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_USER_ID);

        // Set up location services
        buildGoogleApiClient();
        locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        loc = new LocationUtil(getApplicationContext(), mGoogleApiClient, MapsActivity.this);

        if (mode == MODE_REVIEW) {
            // Go back to start mode
            PreferencesUtil.getInstance().addPreference(AppConstants.PREFERENCE_MODE, AppConstants.MODE_START);
        }

        btnStart = (Button)findViewById(R.id.btnStart);
        btnStop = (Button)findViewById(R.id.btnStop);
        btnChoose = (Button)findViewById(R.id.btnChoose);
        btnPrev = (Button)findViewById(R.id.btnPrev);
        btnNext = (Button)findViewById(R.id.btnNext);
        btnCancel = (Button)findViewById(R.id.btnCancel);
        txtInformation = (TextView) findViewById(R.id.txtInformation);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startJourney();
            }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                endJourney();
            }
        });
        btnChoose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) { showRoutes(); }
        });
        btnPrev.setOnClickListener(new View.OnClickListener(){
          @Override
            public void onClick(View view) {
              if (reviewIdx > 0) {
                  mMap.clear();
                  plotSingleRoute(routes.get(--reviewIdx), true, true);
              }
          }
        });
        btnNext.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (reviewIdx < routes.size()-1) {
                    mMap.clear();
                    plotSingleRoute(routes.get(++reviewIdx), true, true);
                }
            }
        });
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                reset();
            }
        });

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                setup();

                // Prompt for user if one has not been entered
                if (userId < 1) {
                    // User not selected so pop dialog to choose it
                    DialogFragment getUserId = new UserDialogFragment();
                    getUserId.show(getFragmentManager(), "user");
                } else {
                    int mode = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_MODE);

                    // If app was resumed while tracking a journey, restart the listener
                    if (mode == MODE_STOP) {
                        // App was aborted while tracking a journey, so resume from here
                        int routeId = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_ROUTE_ID);
                        Criteria criteria = setUpdateCriteria();
                        locationListener = new RouteLocationListener(routeId, false);
                        locationManager.requestLocationUpdates(REFRESH_INTERVAL, MIN_REFRESH_DISTANCE, criteria, locationListener, null);
                    }
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.action_menu, menu);
        changeIconColour(menu, R.id.action_manual, Color.WHITE);
        changeIconColour(menu, R.id.action_review, Color.WHITE);
        return true;
    }

    private void changeIconColour(Menu menu, int id, int color) {
        Drawable drawable = menu.findItem(id).getIcon();
        if (drawable != null) {
            drawable.mutate();
            drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int mode = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_MODE);

        switch (item.getItemId()) {
            case R.id.action_review:
                // Review routes for date
                if (mode == AppConstants.MODE_START) {
                    // Pick date
                    final Calendar c = Calendar.getInstance();
                    new DatePickerDialog(this,
                        new DatePickerDialog.OnDateSetListener() {

                            @Override
                            public void onDateSet(DatePicker view, int year,
                                                  int monthOfYear, int dayOfMonth) {
                                Calendar dateToReview = Calendar.getInstance();
                                c.set(year, monthOfYear, dayOfMonth, 0, 0, 0);
                                Log.d(TAG, "Looking up for date: " +
                                        new SimpleDateFormat(AppConstants.DATE_TIME_FORMAT).format(c.getTime()));
                                routes = DatabaseUtil.getInstance(getApplicationContext())
                                        .fetchSavedRoutes(c.getTime());
                                Log.d(TAG, "Fetched routes: " + routes.size());

                                // Go into review mode
                                if (routes.size() > 0) {
                                    PreferencesUtil.getInstance()
                                            .addPreference(AppConstants.PREFERENCE_MODE, MODE_REVIEW);
                                    setup();
                                }
                            }
                        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
                }
                return true;
            case R.id.action_manual:
                // Enter route manually (specify date/time and start/end postcodes)
                if (mode == AppConstants.MODE_START) {
                    final Calendar c = Calendar.getInstance();
                    new DatePickerDialog(this,
                            new DatePickerDialog.OnDateSetListener() {

                                @Override
                                public void onDateSet(DatePicker view, int year,
                                                      int monthOfYear, int dayOfMonth) {
                                    c.set(year, monthOfYear, dayOfMonth);
                                    new TimePickerDialog(MapsActivity.this,
                                            new TimePickerDialog.OnTimeSetListener() {
                                                @Override
                                                public void onTimeSet(TimePicker timePicker, int hour, int minute) {
                                                    // Show journey dialog
                                                    c.set(Calendar.HOUR_OF_DAY, hour);
                                                    c.set(Calendar.MINUTE, minute);
                                                    loc.setStoredDate(c.getTime());
                                                    DialogFragment enterJourney = new EnterJourneyDialogFragment();
                                                    enterJourney.show(getFragmentManager(), "journey");
                                                }
                                            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
                                }
                            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
                }
                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    private void plotRoutes(int routeId, List<LatLng> startEnd) {
        // Checks, whether start and end locations are captured
        if (startEnd.size() == 2) {
            final LatLng origin = startEnd.get(0);
            final LatLng dest = startEnd.get(1);

            // Getting URL to the Google Directions API
            String url = getUrl(origin, dest, false);

            // This call will fetch the suggested routes between the start and end points
            new FetchUrl(url, Route.RouteType.ROUTE_SUGGESTION, loc, MapsActivity.this,
                    new UpdateUICallback() {
                        @Override
                        public void updateUI(List<Route> result) {
                            plotResult(result, true);

                            // Now make a second call, passing in waypoints captured on the journey
                            // This should return something close to the route actually taken
                            String url = getUrl(origin, dest, true);

                            // Only do lookup if there are some waypoints that might make a difference
                            // getUrl returns empty string if there are no waypoints to add
                            if (url.length() > 0) {
                                new FetchUrl(url, Route.RouteType.ROUTE_TAKEN, loc, MapsActivity.this,
                                        new UpdateUICallback() {
                                            @Override
                                            public void updateUI(List<Route> result) {
                                                plotResult(result, false);
                                            }
                                        }).execute();

                            }
                        }
                    }).execute();

            // move map camera
            Route fullRoute = DatabaseUtil.getInstance(getApplicationContext()).fetchRoute(routeId);
            zoomToRoute(fullRoute.getPoints());
        }
    }

    private void zoomToRoute(List<LatLng> points) {
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (LatLng ll : points) {
            builder.include(ll);
        }
        LatLngBounds bounds = builder.build();

        int padding = 200; // offset from edges of the map in pixels
        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, padding);
        mMap.animateCamera(cu);
    }

    private void plotResult(List<Route> result, boolean clear) {
        routes.addAll(result);

        // Traversing through all the routes
        for (int i = 0; i < result.size(); i++) {
            plotSingleRoute(result.get(i), false, clear && i==0);
        }
    }

    private void plotSingleRoute(Route route, boolean zoom, boolean clear) {
        // Plot start/end markers
        List<LatLng> startEnd = new ArrayList<>();
        startEnd.add(route.getPoints().get(0));
        startEnd.add(route.getPoints().get(route.getPoints().size()-1));
        setupMap(startEnd, clear);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        String info = sdf.format(route.getStartTime()) + " " +
                route.getStartAddress() + " to " + route.getEndAddress() +
                " (" + route.getFormattedDistance() + ")";
        txtInformation.setText(info);

        // Plot route polyline
        PolylineOptions lineOptions = new PolylineOptions();
        lineOptions.addAll(route.getPoints());
        lineOptions.width(10);
        lineOptions.color(route.getColour());
        mMap.addPolyline(lineOptions);

        if (zoom) {
            zoomToRoute(route.getPoints());
        }
    }

    private String getUrl(LatLng origin, LatLng dest, boolean useWayPoints) {

        // Origin of route
        String strOrigin = "origin=" + origin.latitude + "," + origin.longitude;

        // Destination of route
        String strDest = "destination=" + dest.latitude + "," + dest.longitude;

        String strWaypoints = "";

        if (useWayPoints) {
            List<LatLng> waypoints = new ArrayList<>();
            int routeId = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_ROUTE_ID);

            Log.d(TAG, "Fetching waypoints for route id: " + routeId);
            DatabaseUtil db = DatabaseUtil.getInstance(getApplicationContext());
            Route r = db.fetchWaypoints(routeId);

            if (r.getPoints().size() == 0) {
                // No waypoints, so nothing to look up
                return "";
            }

            for (LatLng ll : r.getPoints()) {
                waypoints.add(ll);
                if (waypoints.size() > 15) break;
            }
//            waypoints.clear();
//            waypoints.add(new LatLng(53.418506, -2.170879));
//            DatabaseUtil.getInstance(getApplicationContext()).insertRoutePoint(routeId, 53.418506, -2.170879, 2);
//            waypoints.add(new LatLng(53.445965, -2.179805));
//            DatabaseUtil.getInstance(getApplicationContext()).insertRoutePoint(routeId, 53.445965, -2.179805, 2);
//            waypoints.add(new LatLng(53.466201, -2.180203));
//            DatabaseUtil.getInstance(getApplicationContext()).insertRoutePoint(routeId, 53.466201, -2.180203, 2);
//            Log.d(TAG, "Added " + waypoints.size() + " waypoints");

            // Waypoints
            StringBuilder waypointLocations = new StringBuilder();
            for (LatLng ll : waypoints) {
                if (waypointLocations.length() > 0) {
                    waypointLocations.append("|");
                }
                waypointLocations.append(ll.latitude);
                waypointLocations.append(",");
                waypointLocations.append(ll.longitude);
            }

            try {
                strWaypoints = "&waypoints=" +
                        URLEncoder.encode(waypointLocations.toString(), AppConstants.ENCODING);
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Unable to encode waypoints data: " + e.getMessage());
                return "";
            }
        } else {
            strWaypoints = "&alternatives=true";
        }

        // Sensor enabled
        String sensor = "sensor=false";

        // Building the parameters to the web service
        String parameters = strOrigin + "&" + strDest +
                strWaypoints + "&" + sensor +
                "&key=" + getString(R.string.MAPS_API_KEY);

        // Output format
        String output = "json";

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;

        return url;
    }


    private void showRoutes() {
        DialogFragment confirmJourney = new EndJourneyDialogFragment();
        Bundle bundle = new Bundle();
        for (int i=0; i<routes.size(); i++) {
            bundle.putSerializable("routes" + i, routes.get(i));
        }

        confirmJourney.setArguments(bundle);
        confirmJourney.show(getFragmentManager(), "journey");
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }


    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    public boolean checkLocationPermission(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Asking user if explanation is needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                //Prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted. Do the
                    // contacts-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        if (mGoogleApiClient == null) {
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }

                } else {

                    // Permission denied, Disable the functionality that depends on this permission.
                    Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    private void createManualRoute(String startPostcode, String endPostcode) {
        Date startDate = loc.getStoredDate();
        Location start = loc.getLocationFromPostcode(startPostcode);
        Location end = loc.getLocationFromPostcode(endPostcode);

        if (start != null && end != null) {
            PreferencesUtil.getInstance().addPreference(AppConstants.PREFERENCE_ROUTE_START_TIME,
                    startDate.getTime());
            PreferencesUtil.getInstance().addPreference(AppConstants.PREFERENCE_MODE,
                    MODE_CHOOSE);

            // Create route in database and get id
            int routeId = DatabaseUtil.getInstance(getApplicationContext())
                    .createNewRoute(startDate);
            PreferencesUtil.getInstance().addPreference(AppConstants.PREFERENCE_ROUTE_ID,
                    routeId);

            // Store start point
            DatabaseUtil.getInstance(getApplicationContext())
                    .insertRoutePoint(routeId,
                            start.getLatitude(),
                            start.getLongitude(),
                            AppConstants.POINT_START);
            PreferencesUtil.getInstance().addPreference(AppConstants.PREFERENCE_ADDR_START,
                    startPostcode);

            // Store end point
            storeEndJourney(routeId, end);
        }

    }

    @Override
    public void onDismiss(boolean update, int selectedItem) {
        if (update) {
            // Reset everything
            PreferencesUtil.getInstance().addPreference(AppConstants.PREFERENCE_MODE, MODE_START);
            setup();
        } else if (selectedItem >= 0) {
            plotSingleRoute(routes.get(selectedItem), true, true);
        }
    }

    public void onDismiss(String startPostcode, String endPostcode) {
        if (null != startPostcode && startPostcode.length() >= 5 &&
                null != endPostcode && endPostcode.length() >= 5) {
            // Geocode locations and use as start and finish
            routes.clear();
            createManualRoute(startPostcode, endPostcode);
        }
    }

    private void reset() {
        int mode = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_MODE);
        if (mode != AppConstants.MODE_REVIEW) {
            int routeId = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_ROUTE_ID);
            DatabaseUtil db = DatabaseUtil.getInstance(getApplicationContext());
            db.deleteRoute(routeId);

            if (locationListener != null) {
                locationManager.removeUpdates(locationListener);
            }
        }
        PreferencesUtil.getInstance().addPreference(AppConstants.PREFERENCE_MODE, MODE_START);
        setup();
    }

    private void setup() {
        int mode = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_MODE);
        int routeId = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_ROUTE_ID);
        String infoText = "";
        List<LatLng> startEnd;

        mMap.clear();

        switch (mode) {
            case MODE_START:
                btnStart.setVisibility(View.VISIBLE);
                btnStop.setVisibility(View.GONE);
                btnChoose.setVisibility(View.GONE);
                btnCancel.setVisibility(View.GONE);
                btnPrev.setVisibility(View.GONE);
                btnNext.setVisibility(View.GONE);
                break;
            case MODE_STOP:
                btnStart.setVisibility(View.GONE);
                btnStop.setVisibility(View.VISIBLE);
                btnChoose.setVisibility(View.GONE);
                btnCancel.setVisibility(View.VISIBLE);
                btnPrev.setVisibility(View.GONE);
                btnNext.setVisibility(View.GONE);
                routes.clear();
                break;
            case MODE_CHOOSE:
                btnStart.setVisibility(View.GONE);
                btnStop.setVisibility(View.GONE);
                btnChoose.setVisibility(View.VISIBLE);
                btnCancel.setVisibility(View.VISIBLE);
                btnPrev.setVisibility(View.GONE);
                btnNext.setVisibility(View.GONE);
                startEnd = DatabaseUtil.getInstance(getApplicationContext())
                        .fetchMarkerPoints(routeId).getPoints();
                infoText = "Route id: " + routeId + " ended at: " +
                        PreferencesUtil.getInstance().getStringPreference(AppConstants.PREFERENCE_ADDR_END);
                setupMap(startEnd, true);
                plotRoutes(routeId, startEnd);
                break;
            case MODE_REVIEW:
                btnStart.setVisibility(View.GONE);
                btnStop.setVisibility(View.GONE);
                btnChoose.setVisibility(View.GONE);
                btnCancel.setVisibility(View.VISIBLE);
                btnPrev.setVisibility(View.VISIBLE);
                btnNext.setVisibility(View.VISIBLE);
                reviewIdx = 0;
                plotSingleRoute(routes.get(reviewIdx), true, true);
        }

        if (mode != MODE_REVIEW) {
            txtInformation.setText(infoText);
        }
    }

    public void setupMap(List<LatLng> startEnd, boolean clear) {
        // Creating MarkerOptions
        MarkerOptions options = new MarkerOptions();
        int pointNo = 1;

        if (clear) mMap.clear();

        for (LatLng point : startEnd) {
            // Setting the position of the marker
            options.position(point);

            /**
             * For the start location, the color of marker is GREEN and
             * for the end location, the color of marker is RED.
             */
            if (pointNo == 1) {
                pointNo++;
                options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));

                if (startEnd.size() == 1) {
                    CameraUpdate cu = CameraUpdateFactory.newLatLngZoom(point, 15);
                    mMap.animateCamera(cu);
                }
            } else {
                options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
            }
            mMap.addMarker(options);
        }
    }

    private void startJourney() {
        Date startDate = new Date();

        // Update preferences
        PreferencesUtil.getInstance().addPreference(AppConstants.PREFERENCE_ROUTE_START_TIME,
                startDate.getTime());
        PreferencesUtil.getInstance().addPreference(AppConstants.PREFERENCE_MODE,
                MODE_STOP);

        // Create route in database and get id
        int routeId = DatabaseUtil.getInstance(getApplicationContext())
                .createNewRoute(startDate);
        PreferencesUtil.getInstance().addPreference(AppConstants.PREFERENCE_ROUTE_ID,
                routeId);

        // Set up criteria for location updates
        Criteria criteria = setUpdateCriteria();

        // Set up listener and start listening for location updates
        locationListener = new RouteLocationListener(routeId, true);
        locationManager.requestLocationUpdates(REFRESH_INTERVAL, MIN_REFRESH_DISTANCE, criteria, locationListener, null);

        // Amend screen layout
        setup();
    }

    @SuppressLint("MissingPermission")
    private void endJourney() {
        final ProgressDialog progressDialog = new ProgressDialog(MapsActivity.this);
        Location location = loc.getLastLocation();

        final int routeId = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_ROUTE_ID);

        long timeSinceLastUpdateMS = (location != null ? (new Date()).getTime() - location.getTime() : 30001);

        if (locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }

        if (timeSinceLastUpdateMS > 30000) {
            // Last update was over 30s ago so request current location
            progressDialog.setMessage("Acquiring Location");
            progressDialog.show();

            Criteria criteria = setUpdateCriteria();

            locationManager.requestSingleUpdate(criteria, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    storeEndJourney(routeId, location);

                    if (progressDialog.isShowing()) {
                        progressDialog.cancel();
                    }
                }

                @Override
                public void onStatusChanged(String s, int i, Bundle bundle) {
                }

                @Override
                public void onProviderEnabled(String s) {
                }

                @Override
                public void onProviderDisabled(String s) {
                }
            }, null);
        } else {
            storeEndJourney(routeId, location);
        }
    }

    private void storeStartJourney(int routeId, Location location) {

    }

    private void storeEndJourney(int routeId, Location location) {
        PreferencesUtil.getInstance().addPreference(AppConstants.PREFERENCE_MODE,
                MODE_CHOOSE);
        DatabaseUtil.getInstance(getApplicationContext())
                .insertRoutePoint(routeId,
                    location.getLatitude(),
                    location.getLongitude(),
                    AppConstants.POINT_END);
        loc.storeAddress(location, AppConstants.PREFERENCE_ADDR_END);
        setup();
    }

    private Criteria setUpdateCriteria() {
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        return criteria;
    }

    public class RouteLocationListener implements LocationListener {

        private int routeId;
        private boolean first;
        private int pointType;
        private ProgressDialog progressDialog;

        public RouteLocationListener(int routeId, boolean starting) {
            this.routeId = routeId;
            first = starting;
            pointType = AppConstants.POINT_WAYPOINT;

            if (first) {
                pointType = AppConstants.POINT_START;
            }

            progressDialog = new ProgressDialog(MapsActivity.this);
            progressDialog.setMessage("Acquiring Location");
            progressDialog.show();
        }

        @Override
        public void onLocationChanged(Location location) {
            // Update running record of last location
            Log.d(TAG, "Location changed: " + location);
            loc.setLastLocation(location);

            // Store point in database
            DatabaseUtil.getInstance(getApplicationContext())
                    .insertRoutePoint(routeId,
                        location.getLatitude(),
                        location.getLongitude(),
                        pointType);

            // On first update, set start address and set up map with marker
            if (first) {
                loc.storeAddress(location, AppConstants.PREFERENCE_ADDR_START);
                List<LatLng> startEnd = DatabaseUtil.getInstance(getApplicationContext())
                        .fetchMarkerPoints(routeId).getPoints();
                String infoText = "Route id: " + routeId + " started at: " +
                        PreferencesUtil.getInstance().getStringPreference(AppConstants.PREFERENCE_ADDR_START);
                txtInformation.setText(infoText);
                setupMap(startEnd, true);
            }

            first = false;
            pointType = AppConstants.POINT_WAYPOINT;

            if (progressDialog.isShowing()) {
                progressDialog.cancel();
            }
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
        }

        @Override
        public void onProviderEnabled(String s) {
        }

        @Override
        public void onProviderDisabled(String s) {
        }
    }
}
