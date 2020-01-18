package com.sk7software.mileageroutetracker.network;

import android.content.Context;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sk7software.mileageroutetracker.AppConstants;
import com.sk7software.mileageroutetracker.R;
import com.sk7software.mileageroutetracker.db.DatabaseUtil;
import com.sk7software.mileageroutetracker.model.DevMessage;
import com.sk7software.mileageroutetracker.model.Route;
import com.sk7software.mileageroutetracker.location.LocationUtil;
import com.sk7software.mileageroutetracker.ui.ActivityUpdateInterface;
import com.sk7software.mileageroutetracker.ui.UpdateUICallback;
import com.sk7software.mileageroutetracker.util.PreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Andrew on 29/10/2017
 */

public class NetworkCall {

    private static RequestQueue queue;

    private static final String UPLOAD_URL = "http://www.sk7software.co.uk/mileage/upload.php";
    private static final String USER_URL = "http://www.sk7software.co.uk/mileage/user.php";
    private static final String DEV_MESSAGE_URL = "http://www.sk7software.co.uk/mileage/devmessage.php";
    private static final String DEV_MESSAGE_UPDATE_URL = "http://www.sk7software.co.uk/mileage/devmessageseen.php";
    private static final String GOOGLE_DISTANCE_MATRIX_URL = "https://maps.googleapis.com/maps/api/distancematrix/json";
    private static final String TAG = NetworkCall.class.getSimpleName();

    public interface NetworkCallback {
        public void onRequestCompleted(Object callbackData);
        public void onError(Exception e);
    }

    private synchronized static RequestQueue getQueue(Context context) {
        if (queue == null) {
            queue = Volley.newRequestQueue(context);
        }
        return queue;
    }

    public static void uploadRoute(final Context context, final Route route,
                                   final ActivityUpdateInterface uiUpdate, final NetworkCallback callback) {
        Gson gson = new GsonBuilder()
                .setDateFormat(AppConstants.DATE_TIME_FORMAT)
                .create();

        // Set start and end points before serialising and removing other points
        route.setStartEnd();
        String json = gson.toJson(route);
        Log.d(TAG, "Uploading: " + json);
        try {
            JSONObject routeData = new JSONObject(json);
            routeData.remove("points");

            JsonObjectRequest jsObjRequest = new JsonObjectRequest
                    (Request.Method.POST, UPLOAD_URL, routeData,
                            new Response.Listener<JSONObject>() {
                                @Override
                                public void onResponse(JSONObject response) {
                                    // Update database to show route is uploaded
                                    DatabaseUtil.getInstance(context).updateUploadedRoute(route);
                                    uiUpdate.setProgress(false, null);
                                    callback.onRequestCompleted(null);
                                }
                            },
                            new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    Log.d(TAG, "Error => " + error.toString());
                                    uiUpdate.setProgress(false, null);
                                    callback.onError(error);
                                }
                            }
                    );
            jsObjRequest.setRetryPolicy(new DefaultRetryPolicy(5000, 4, 1));
            getQueue(context).add(jsObjRequest);
        } catch (JSONException e) {
            Log.d(TAG, "Error uploading route: " + e.getMessage());
            uiUpdate.setProgress(false, null);
        }
    }

    public static void checkUser(final Context context, final Map<String, String> params, final NetworkCallback callback) {
        StringBuilder sb = new StringBuilder(USER_URL + "?");
        for (Map.Entry<String, String> param : params.entrySet()) {
            sb.append(param.getKey() + "=" + param.getValue());
        }
        StringRequest request = new StringRequest(sb.toString(),
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Map<String, String> callbackData = new HashMap<>();
                        callbackData.put("data", response);
                        callback.onRequestCompleted(callbackData);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        callback.onError(error);
                    }
                }
        );
        request.setRetryPolicy(new DefaultRetryPolicy(5000, 4, 1));
        getQueue(context).add(request);
    }

    public static void uploadMissingRoutes(final Context context, final ActivityUpdateInterface uiUpdate,
                                           final NetworkCallback callback) {
        final LocationUtil loc = LocationUtil.getInstance();
        List<Route> missingRoutes = DatabaseUtil.getInstance(context).fetchRoutesNotUploaded();

        if (missingRoutes.size() > 0) {
            Log.d(TAG, "Attempting to upload " + missingRoutes.size() + " routes");
            for (Route r : missingRoutes) {
                // Copy to final var so it can be used in inner block
                final Route route = r;

                // Set user id
                route.setUserId(PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_USER_ID));

                // Fetch the recorded start/end points for the route
                Route startEnd = DatabaseUtil.getInstance(context).fetchStartEndPoints(route.getId());
                route.setPoints(startEnd.getPoints());

                // Ensure start and end have been recorded
                if (startEnd.getPoints().size() == 2) {
                    LatLng start = startEnd.getPoints().get(0);
                    LatLng end = startEnd.getPoints().get(r.getPoints().size() - 1);

                    // Determine if start or end address needs to be populated
                    Log.d(TAG, "Start: [" + route.getStartAddress() + "] End: [" + route.getEndAddress() + "]");
                    if (route.isStartUnknown() || route.isEndUnknown()) {
                        // Try lookup of start/end location again
                        route.setStartAddress(loc.getAddress(start));
                        route.setEndAddress(loc.getAddress(end));
                        Log.d(TAG, "Start: [" + route.getStartAddress() + "] End: [" + route.getEndAddress() + "]");
                    }

                    // Determine if route was calculated, or if this needs to be looked up now
                    if (route.getDistance() < 0) {
                        // Need to calculate route
                        new RoutePlanning(route.getId(),
                                start,
                                end,
                                Route.RouteType.ROUTE_SUGGESTION,
                                context, null,
                                new UpdateUICallback() {
                                    @Override
                                    public void onSuccess(List<Route> result) {
                                        if (result != null && result.size() > 0) {
                                            // Save the markers for the route (this requires the previously
                                            // saved ones to be deleted first as the start/end were already
                                            // saved when the route was set up
                                            Route routeToUse = result.get(0);
                                            routeToUse.setId(route.getId());

                                            // Update the missing route with distance and start/end address
                                            route.setDistance(routeToUse.getDistance());

                                            if (route.getAdjustedDistance() == -999) {
                                                // Need to adjust it
                                                int homeWorkDistance = PreferencesUtil.getInstance()
                                                        .getIntPreference(AppConstants.PREFERENCE_USER_WORK_DISTANCE_M);
                                                int adjustedDistance = route.getDistance() - homeWorkDistance;
                                                if (adjustedDistance < 0) {
                                                    adjustedDistance = 0;
                                                }
                                                route.setAdjustedDistance(adjustedDistance);
                                            } else {
                                                route.setAdjustedDistance(routeToUse.getDistance());
                                            }

                                            DatabaseUtil.getInstance(context).updateSavedRoute(route);

                                            // Save marker points for the route to use
                                            DatabaseUtil.getInstance(context).deleteSavedMarkers(route.getId());
                                            DatabaseUtil.getInstance(context).saveMarkers(routeToUse);

                                            // Attempt to upload the completed route
                                            doRouteUpload(context, uiUpdate, route);
                                        }
                                    }

                                    @Override
                                    public void onFailure() {
                                        // There was an issue fetching the route so log this.  A further
                                        // attempt will be made next time
                                        Log.d(TAG, "Failed to fetch route for id: " + route.getId());
                                    }
                                }).execute();
                    } else {
                        // Attempt to upload route
                        doRouteUpload(context, uiUpdate, route);
                    }
                } else {
                    // Start and end not recorded so this isn't a valid route.  Delete it
                    Log.d(TAG, "No valid start/end points so deleting route: " + route.getId());
                    DatabaseUtil.getInstance(context).deleteRoute(route.getId());
                    DatabaseUtil.getInstance(context).deleteSavedRoute(route.getId());
                }
            }
        } else {
            Log.d(TAG, "No routes to upload");
        }
    }

    private static void doRouteUpload(final Context context, final ActivityUpdateInterface uiUpdate, final Route route) {
        uploadRoute(context, route, uiUpdate, new NetworkCallback() {
            @Override
            public void onRequestCompleted(Object callbackData) {
                // Update indicator to show route is uploaded
                Log.d(TAG, "Route: " + route.getId() + " (" + route.getSummary() + ") uploaded");
            }

            @Override
            public void onError(Exception e) {
                Log.d(TAG, "Route: " + route.getId() + " (" + route.getSummary() + ") upload FAILED");
                Log.d(TAG, "ERROR: " + e.getMessage());
            }
        });
    }

    public static void getDeveloperMessages (final Context context, final int userId, final String version, final NetworkCallback callback) {
        try {
            final List<DevMessage> messages = new ArrayList<>();
            final Gson gson = new GsonBuilder()
                    .create();

            JsonArrayRequest jsObjRequest = new JsonArrayRequest
                    (Request.Method.GET, DEV_MESSAGE_URL + "?user=" + userId + "&version=" + version,
                            null,
                            new Response.Listener<JSONArray>() {
                                @Override
                                public void onResponse(JSONArray response) {
                                    try {
                                        for (int i = 0; i < response.length(); i++) {
                                            JSONObject message = (JSONObject) response.get(i);
                                            DevMessage m = gson.fromJson(message.toString(), DevMessage.class);
                                            messages.add(m);
                                        }

                                        // Pass message list back to UI
                                        Log.d(TAG, messages.toString());
                                        callback.onRequestCompleted(messages);
                                    } catch (JSONException e) {
                                        Log.d(TAG, "Error getting dev messages: " + e.getMessage());
                                    }
                                }
                            },
                            new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    Log.d(TAG, "Error => " + error.toString());
                                    callback.onError(error);
                                }
                            }
                    );
            jsObjRequest.setRetryPolicy(new DefaultRetryPolicy(5000, 1, 1));
            getQueue(context).add(jsObjRequest);
        } catch (Exception e) {
            Log.d(TAG, "Error fetching dev messages: " + e.getMessage());
        }
    }

    public static void updateDevMessage(final Context context, final int userId,
                                        final int messageId, final String showAgain,
                                        final NetworkCallback callback) {
        StringRequest request = new StringRequest(DEV_MESSAGE_UPDATE_URL +
                                                    "?user=" + userId +
                                                    "&message=" + messageId +
                                                    "&show=" + showAgain,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d(TAG, "User message update completed");
                        callback.onRequestCompleted(null);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d(TAG, "Error updating user message: " + error.getMessage());
                        callback.onError(error);
                    }
                }
        );
        request.setRetryPolicy(new DefaultRetryPolicy(5000, 4, 1));
        getQueue(context).add(request);
    }

    public static void fetchRouteDistance(final Context context, final String startPostCode,
                                          final String endPostCode,
                                          final NetworkCallback callback) {
        final Gson gson = new GsonBuilder()
                .create();

        JsonObjectRequest jsObjRequest = new JsonObjectRequest
                (Request.Method.GET, GOOGLE_DISTANCE_MATRIX_URL + "?origins=" + startPostCode +
                                            "&destinations=" + endPostCode +
                                            "&mode=driving" +
                                            "&key=" + context.getString(R.string.MAPS_API_KEY),
                        null,
                        new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {
                                try {
                                    JSONObject row = (JSONObject)response.getJSONArray("rows").get(0);
                                    JSONObject element = (JSONObject)row.getJSONArray("elements").get(0);
                                    JSONObject distance = (JSONObject)element.getJSONObject("distance");
                                    Integer distanceM = Integer.parseInt(distance.get("value").toString());

                                    // Pass message list back to UI
                                    callback.onRequestCompleted(distanceM);
                                } catch (JSONException e) {
                                    Log.d(TAG, "Error getting distance matrix: " + e.getMessage());
                                    callback.onError(e);
                                }
                            }
                        },
                        new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                Log.d(TAG, "Error => " + error.toString());
                                callback.onError(error);
                            }
                        }
                );
        jsObjRequest.setRetryPolicy(new DefaultRetryPolicy(5000, 1, 1));
        getQueue(context).add(jsObjRequest);
    }
}
