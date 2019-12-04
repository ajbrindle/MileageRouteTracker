package com.sk7software.mileageroutetracker.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
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
import com.sk7software.mileageroutetracker.BuildConfig;
import com.sk7software.mileageroutetracker.R;
import com.sk7software.mileageroutetracker.db.DatabaseUtil;
import com.sk7software.mileageroutetracker.location.RouteLocationListener;
import com.sk7software.mileageroutetracker.model.DevMessage;
import com.sk7software.mileageroutetracker.model.Route;
import com.sk7software.mileageroutetracker.network.RoutePlanning;
import com.sk7software.mileageroutetracker.network.NetworkCall;
import com.sk7software.mileageroutetracker.location.LocationUtil;
import com.sk7software.mileageroutetracker.util.PreferencesUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import static com.sk7software.mileageroutetracker.AppConstants.MODE_CHOOSE;
import static com.sk7software.mileageroutetracker.AppConstants.MODE_REVIEW;
import static com.sk7software.mileageroutetracker.AppConstants.MODE_START;
import static com.sk7software.mileageroutetracker.AppConstants.MODE_STOP;

public class MapsActivity extends AppCompatActivity
        implements OnMapReadyCallback, EndJourneyDialogFragment.OnDialogDismissListener,
        EnterJourneyDialogFragment.OnDialogDismissListener, ActivityUpdateInterface,
        GoogleApiClient.ConnectionCallbacks {

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private List<Route> routes = new ArrayList<>();
    private LocationUtil locationUtil;
    private RouteLocationListener locationListener;
    private LocationManager locationManager;
    private Button btnStart;
    private Button btnStop;
    private Button btnChoose;
    private Button btnPrev;
    private Button btnCancel;
    private Button btnNext;
    private TextView txtInformation;

    private AlertDialog.Builder progressDialogBuilder;
    private Dialog progressDialog;

    private int reviewIdx;
    private int userId;

    public static final long REFRESH_INTERVAL = 1000 * 60 * 2; // 2 minutes
    private static final int MIN_INTERVAL_MS = 30000;
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
        locationUtil = LocationUtil.getInstance();
        locationUtil.init(getApplicationContext(), mGoogleApiClient, MapsActivity.this);

        // Create progress dialog for use later
        progressDialogBuilder = new AlertDialog.Builder(MapsActivity.this);
        progressDialogBuilder.setView(R.layout.progress);

        if (mode == MODE_REVIEW) {
            // Go back to start mode, as review mode doesn't get restored
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

        // Log debug
        //DatabaseUtil.getInstance(getApplicationContext()).debugLogging(10);

        // Check if there are any developer messages to display
        displayDevMessages();
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
    @SuppressLint("MissingPermission")
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
                        listenForLocationUpdates(routeId, false);
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
        changeIconColour(menu, R.id.action_confirm, Color.WHITE);
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
        boolean actionConfirm = false;

        switch (item.getItemId()) {
            case R.id.action_confirm:
                actionConfirm = true;
            case R.id.action_review:
                // Review routes for date
                if (mode == AppConstants.MODE_START) {
                    // Pick date
                    final Calendar c = Calendar.getInstance();
                    final boolean uploadFailedOnly = actionConfirm;
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
                                        .fetchSavedRoutes(c.getTime(), uploadFailedOnly);
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
                                                    locationUtil.setStoredDate(c.getTime());
                                                    DialogFragment enterJourney = new EnterJourneyDialogFragment();
                                                    enterJourney.show(getFragmentManager(), "journey");
                                                }
                                            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
                                }
                            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
                }
                return true;
            case R.id.action_settings:
                // Open settings dialog
                DialogFragment getUserId = new UserDialogFragment();
                getUserId.show(getFragmentManager(), "user");
                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    private void displayDevMessages() {
        String version = BuildConfig.VERSION_NAME;
        NetworkCall.getDeveloperMessages(MapsActivity.this, userId, version, new NetworkCall.NetworkCallback() {
            @Override
            public void onRequestCompleted(Object callbackData) {
                List<DevMessage> messages = (List<DevMessage>)callbackData;
                if (messages == null || messages.size() == 0) {
                    // Do nothing
                } else {
                    DialogFragment devMessage = new DeveloperMessageDialogFragment();
                    Bundle bundle = new Bundle();
                    for (int i = 0; i < messages.size(); i++) {
                        bundle.putSerializable("message" + i, messages.get(i));
                    }

                    devMessage.setArguments(bundle);
                    devMessage.show(getFragmentManager(), "devmessage");
                }
            }

            @Override
            public void onError(Exception e) {

            }
        });
    }

    private void fetchAndPlotRoutes(final int routeId, final List<LatLng> startEnd) {
        // Checks, whether start and end locations are captured
        if (startEnd.size() == 2) {
            final LatLng origin = startEnd.get(0);
            final LatLng dest = startEnd.get(1);

            // This call will fetch the suggested routes between the start and end points
            new RoutePlanning(routeId, origin, dest, Route.RouteType.ROUTE_SUGGESTION,
                              getApplicationContext(), MapsActivity.this,
                    new UpdateUICallback() {
                        @Override
                        public void onSuccess(List<Route> result) {
                            plotResult(result, true);

                            // Now make a second call, passing in waypoints captured on the journey
                            // This should return something close to the route actually taken
                            new RoutePlanning(routeId, origin, dest, Route.RouteType.ROUTE_TAKEN,
                                              getApplicationContext(), MapsActivity.this,
                                    new UpdateUICallback() {
                                        @Override
                                        public void onSuccess(List<Route> result) {
                                            plotResult(result, false);
                                        }

                                        @Override
                                        public void onFailure() {
                                            // No issue with this call failing, as suggested routes
                                            // have already been provided
                                            Log.d(TAG, "Failed to fetch route taken");
                                        }
                                    }).execute();
                        }

                        @Override
                        public void onFailure() {
                            // Notify user that suggested routes could not be found and an attempt will be
                            // made to recover on next run.  Make sure network is enabled
                            new AlertDialog.Builder(MapsActivity.this)
                                    .setTitle("Route")
                                    .setMessage("There was an error looking up the route. " +
                                            "Select the CHOOSE ROUTE button and save as usual. The missing " +
                                            "information will be filled in and stored next time you run the app. " +
                                            "Please ensure you have a good network connection.")
                                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                        }
                                    })
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .show();

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
        if (result != null) {
            routes.addAll(result);

            // Traversing through all the routes
            for (int i = 0; i < result.size(); i++) {
                plotSingleRoute(result.get(i), false, clear && i == 0);
            }
        } else {
            new AlertDialog.Builder(MapsActivity.this)
                    .setTitle("Route")
                    .setMessage("There was an error looking up the route. " +
                            "Please check your network connection.")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            setup();
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
    }

    private void plotSingleRoute(Route route, boolean zoom, boolean clear) {
        // Plot start/end markers
        List<LatLng> startEnd = new ArrayList<>();
        startEnd.add(route.getPoints().get(0));
        startEnd.add(route.getPoints().get(route.getPoints().size()-1));
        setupMap(startEnd, clear);

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
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

    @Override
    public void setProgress(boolean showProgressDialog, String progressMessage) {
        if (showProgressDialog) {
            progressDialog = progressDialogBuilder
                    .setMessage(progressMessage)
                    .create();
            progressDialog.show();
        } else {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
        }
    }

    @Override
    public void updateMap(String infoText, List<LatLng> startEnd) {
        txtInformation.setText(infoText);
        setupMap(startEnd, true);
    }

    private void showRoutes() {
        if (routes == null || routes.size() == 0) {
            // Show save dialog
            DialogFragment saveJourney = new SaveJourneyDialogFragment();
            saveJourney.show(getFragmentManager(), "journey");
        } else {
            DialogFragment confirmJourney = new EndJourneyDialogFragment();
            Bundle bundle = new Bundle();
            for (int i = 0; i < routes.size(); i++) {
                bundle.putSerializable("routes" + i, routes.get(i));
            }

            confirmJourney.setArguments(bundle);
            confirmJourney.show(getFragmentManager(), "journey");
        }
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.d(TAG, "Google API Connected");

        // Once client API is connected, it is possible to get missing information from previous journeys
        // This is done silently in the background
        NetworkCall.uploadMissingRoutes(getApplicationContext() , MapsActivity.this, new NetworkCall.NetworkCallback() {
            @Override
            public void onRequestCompleted(Object callbackData) {
                Log.d(TAG, "Uploading missing routes - check logs for individual progress");
            }

            @Override
            public void onError(Exception e) {
                // Just log failure.  In future, send diagnostic info to developer
                Log.d(TAG, "Uploading missing routes FAILED");
            }
        });
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.d(TAG, "*** Google API Not Connected");
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
        Date startDate = locationUtil.getStoredDate();
        Location start = locationUtil.getLocationFromPostcode(startPostcode);
        Location end = locationUtil.getLocationFromPostcode(endPostcode);

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
                        .fetchStartEndPoints(routeId).getPoints();
                infoText = "Route id: " + routeId + " ended at: " +
                        PreferencesUtil.getInstance().getStringPreference(AppConstants.PREFERENCE_ADDR_END);
                setupMap(startEnd, true);
                fetchAndPlotRoutes(routeId, startEnd);
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

    @SuppressLint("MissingPermission")
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

        // Initialise location update listener
        listenForLocationUpdates(routeId, true);

        // Amend screen layout
        setup();
    }

    @SuppressLint("MissingPermission")
    private void endJourney() {
        Location location = locationUtil.getLastLocation();

        final int routeId = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_ROUTE_ID);

        long timeSinceLastUpdateMS = (location != null ? (new Date()).getTime() - location.getTime() : MIN_INTERVAL_MS+1);

        if (locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }

        if (timeSinceLastUpdateMS > MIN_INTERVAL_MS) {
            // Last update was over 30s ago so request current location
            setProgress(true, "Acquiring Location");

            Criteria criteria = setUpdateCriteria();
            locationManager.requestSingleUpdate(criteria, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    /****************************************************
                     * TESTING ONLY
                     ****************************************************
                    location.setLatitude(53.4816282);;
                    location.setLongitude(-2.2046857);
                    /****************************************************
                     * TESTING ONLY
                     ****************************************************/
                    setProgress(false, null);
                    storeEndJourney(routeId, location);
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
        // Not implemented
    }

    private void storeEndJourney(int routeId, Location location) {
        PreferencesUtil.getInstance().addPreference(AppConstants.PREFERENCE_MODE,
                MODE_CHOOSE);
        DatabaseUtil.getInstance(getApplicationContext())
                .insertRoutePoint(routeId,
                    location.getLatitude(),
                    location.getLongitude(),
                    AppConstants.POINT_END);
        locationUtil.storeAddress(location, AppConstants.PREFERENCE_ADDR_END);
        setup();
    }

    private void listenForLocationUpdates(int routeId, boolean starting) {
        Criteria criteria = setUpdateCriteria();
        locationListener = new RouteLocationListener(routeId, starting, MapsActivity.this, getApplicationContext());
        locationManager.requestLocationUpdates(REFRESH_INTERVAL, MIN_REFRESH_DISTANCE, criteria, locationListener, null);
    }

    private Criteria setUpdateCriteria() {
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        return criteria;
    }
}
