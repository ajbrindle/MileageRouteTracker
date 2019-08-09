package com.sk7software.mileageroutetracker.network;

import android.app.Activity;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sk7software.mileageroutetracker.AppConstants;
import com.sk7software.mileageroutetracker.db.DatabaseUtil;
import com.sk7software.mileageroutetracker.model.DevMessage;
import com.sk7software.mileageroutetracker.model.Route;
import com.sk7software.mileageroutetracker.ui.DeveloperMessageDialogFragment;
import com.sk7software.mileageroutetracker.util.LocationUtil;
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

    private static ProgressDialog progressDialog;
    private static RequestQueue queue;

    private static final String UPLOAD_URL = "http://www.sk7software.co.uk/mileage/upload.php";
    private static final String USER_URL = "http://www.sk7software.co.uk/mileage/user.php";
    private static final String DEV_MESSAGE_URL = "http://www.sk7software.co.uk/mileage/devmessage.php";
    private static final String DEV_MESSAGE_UPDATE_URL = "http://www.sk7software.co.uk/mileage/devmessageseen.php";
    private static final String TAG = NetworkCall.class.getSimpleName();

    public interface NetworkCallback {
        public void onRequestCompleted(Map<String, Integer>callbackData);
        public void onError(Exception e);
    }

    private synchronized static RequestQueue getQueue(Context context) {
        if (queue == null) {
            queue = Volley.newRequestQueue(context);
        }
        return queue;
    }

    public static void uploadRoute(final Context context, final Route route, boolean showProgress, final NetworkCallback callback) {
        Gson gson = new GsonBuilder()
                .setDateFormat(AppConstants.DATE_TIME_FORMAT)
                .create();
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
                                    if (progressDialog.isShowing()) {
                                        progressDialog.dismiss();
                                    }
                                    callback.onRequestCompleted(null);
                                }
                            },
                            new Response.ErrorListener() {
                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    Log.d(TAG, "Error => " + error.toString());
                                    if (progressDialog.isShowing()) {
                                        progressDialog.dismiss();
                                    }
                                    callback.onError(error);
                                }
                            }
                    );
            jsObjRequest.setRetryPolicy(new DefaultRetryPolicy(5000, 4, 1));
            getQueue(context).add(jsObjRequest);
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage("Saving Route");
            if (showProgress) {
                progressDialog.show();
            }
        } catch (JSONException e) {
            Log.d(TAG, "Error uploading route: " + e.getMessage());
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        }
    }

    public static void checkUser(final Context context, final String userName, final NetworkCallback callback) {
        StringRequest request = new StringRequest(USER_URL + "?name=" + userName,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Map<String, Integer> callbackData = new HashMap<>();
                        callbackData.put("id", Integer.parseInt(response));
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

    public static void uploadMissingRoutes(final Context context, final LocationUtil loc, final NetworkCallback callback) {
        List<Route> routes = DatabaseUtil.getInstance(context).fetchRoutesNotUploaded();

        if (routes.size() > 0) {
            Log.d(TAG, "Attempting to upload " + routes.size() + " routes");
            for (Route r : routes) {
                final Route route = r;

                // Set user id
                route.setUserId(PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_USER_ID));

                // Determine if start or end address needs to be populated
                if (route.isStartUnknown() || route.isEndUnknown()) {
                    // Try lookup of start location again
                    Route startEnd = DatabaseUtil.getInstance(context).fetchMarkerPoints(route.getId());

                    if (startEnd.getPoints().size() == 2) {
                        route.setStartAddress(loc.getAddress(startEnd.getPoints().get(0)));
                        route.setEndAddress(loc.getAddress(startEnd.getPoints().get(1)));
                    }
                }

                // Attempt to upload route
                uploadRoute(context, route, false, new NetworkCallback() {
                    @Override
                    public void onRequestCompleted(Map<String, Integer> callbackData) {
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
        } else {
            Log.d(TAG, "No routes to upload");
        }
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
                                        Log.d(TAG, messages.toString());

                                        // Popup fragment showing latest message
                                        showMessage(context, messages);
                                        callback.onRequestCompleted(null);
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

    private static void showMessage(Context context, List<DevMessage> messages) {
        if (messages == null || messages.size() == 0) {
            return;
        }
        DialogFragment devMessage = new DeveloperMessageDialogFragment();
        Bundle bundle = new Bundle();
        for (int i=0; i<messages.size(); i++) {
            bundle.putSerializable("message" + i, messages.get(i));
        }

        devMessage.setArguments(bundle);
        devMessage.show(((Activity)context).getFragmentManager(), "devmessage");
    }
}
