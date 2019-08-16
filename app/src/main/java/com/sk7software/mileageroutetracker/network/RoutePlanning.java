package com.sk7software.mileageroutetracker.network;

import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.mileageroutetracker.AppConstants;
import com.sk7software.mileageroutetracker.R;
import com.sk7software.mileageroutetracker.db.DatabaseUtil;
import com.sk7software.mileageroutetracker.model.Route;
import com.sk7software.mileageroutetracker.ui.ActivityUpdateInterface;
import com.sk7software.mileageroutetracker.ui.UpdateUICallback;
import com.sk7software.mileageroutetracker.location.LocationUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Andrew on 04/03/2018
 */

public class RoutePlanning extends AsyncTask<Void, Void, String> {

    private int routeId;
    private String url;
    private Route.RouteType routeType;
    private UpdateUICallback listener;
    private ActivityUpdateInterface uiInterface;
    private Context context;
    private LocationUtil loc;

    private static final String TAG = RoutePlanning.class.getSimpleName();

    public RoutePlanning(int routeId,
                         LatLng origin,
                         LatLng dest,
                         Route.RouteType routeType,
                         Context context,
                         ActivityUpdateInterface uiInterface,
                         UpdateUICallback listener) {
        this.routeId = routeId;
        this.routeType = routeType;
        this.context = context;
        this.listener = listener;
        this.uiInterface = uiInterface;
        loc = LocationUtil.getInstance();
        this.url = getUrl(origin, dest, routeType == Route.RouteType.ROUTE_TAKEN);
    }

    @Override
    protected String doInBackground(Void... params) {

        // For storing data from web service
        String data = "";

        try {
            if (!"".equals(url)) {
                // Fetching the data from web service
                Log.d(TAG, "Directions: " + url.toString());
                data = downloadUrl(url);
                Log.d("Background Task data", data.toString());
                Log.d("Background Task data", "Last bit of data: " + data.substring(data.length()-50));
            } else {
                Log.d(TAG, "Not making call as no URL to fetch");
            }
        } catch (Exception e) {
            Log.d("Background Task", e.toString());
        }
        return data;
    }

    @Override
    protected void onPreExecute() {
        if (uiInterface != null) {
            uiInterface.setProgress(true, "Fetching Routes...");
        }
    }

    @Override
    protected void onPostExecute(String jsonData) {
        super.onPostExecute(jsonData);

        if (jsonData != null && !"".equals(jsonData)) {
            JSONObject jObject;
            List<Route> routes = null;
            boolean success = true;

            try {
                jObject = new JSONObject(jsonData);
                Log.d(TAG, jsonData);
                Log.d(TAG, "JSON Data status: " + jObject.getString("status"));
                DataParser parser = new DataParser();
                Log.d(TAG, parser.toString());

                // Starts parsing data
                routes = parser.parse(jObject, routeType);
                Log.d(TAG, "Executing routes");
                Log.d(TAG, routes.toString());
                Log.d(TAG, "Number of routes: " + routes.size());

            } catch (Exception e) {
                Log.d("ParserTask", e.toString());
                e.printStackTrace();
                success = false;
            } finally {
                wrapUp(success, routes);
            }
        } else {
            wrapUp(false, null);
        }
    }

    private void wrapUp(boolean success, List<Route> routes) {
        if (uiInterface != null) {
            uiInterface.setProgress(false, null);
        }
        if (success) {
            listener.onSuccess(routes);
        } else {
            listener.onFailure();
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

            Log.d(TAG, "Fetching waypoints for route id: " + routeId);
            DatabaseUtil db = DatabaseUtil.getInstance(context);
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
                "&key=" + context.getString(R.string.MAPS_API_KEY);

        // Output format
        String output = "json";

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;

        return url;
    }

    /**
     * A method to download json data from url
     */
    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);

            // Creating an http connection to communicate with url
            urlConnection = (HttpURLConnection) url.openConnection();

            // Connecting to url
            urlConnection.connect();

            // Reading data from url
            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            Log.d(TAG, "************* RETURNED DATA ***************");
            while ((line = br.readLine()) != null) {
                Log.d(TAG, line);
                sb.append(line);
            }
            Log.d(TAG, "************* END OF DATA ***************");

            data = sb.toString();
            Log.d("downloadUrl", data.toString());
            Log.d(TAG, "Last bit of data: " + data.substring(data.length()-50));
            br.close();

        } catch (Exception e) {
            Log.d("Exception", e.toString());
            data = "";
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    public class DataParser {
        /** Receives a JSONObject and returns a list of lists containing latitude and longitude */
        public List<Route> parse(JSONObject jObject, Route.RouteType routeType){

            List<Route> routes = new ArrayList<>() ;
            JSONArray jRoutes;
            JSONArray jLegs;
            JSONArray jSteps;

            try {

                jRoutes = jObject.getJSONArray("routes");
                Log.d(TAG, jRoutes.toString(2));
                Log.d(TAG, "Number of routes in JSON Array: " + jRoutes.length());

                /** Traversing all routes */
                for(int i=0;i<jRoutes.length();i++){
                    int distance = 0;
                    String summary = ((JSONObject)jRoutes.get(i)).getString("summary");
                    Log.d(TAG, "Route: " + summary);
                    jLegs = ( (JSONObject)jRoutes.get(i)).getJSONArray("legs");
                    List<LatLng> path = new ArrayList<>();

                    /** Traversing all legs */
                    for(int j=0;j<jLegs.length();j++){
                        distance += ((JSONObject)jLegs.get(j)).getJSONObject("distance").getInt("value");
                        Log.d(TAG, "Leg: " + (j+1) + ", length: " + ((JSONObject)jLegs.get(j)).getJSONObject("distance").getString("text"));
                        jSteps = ( (JSONObject)jLegs.get(j)).getJSONArray("steps");
                        Log.d(TAG, "Leg: " + (j+1) + ", steps: " + jSteps.length());

                        /** Traversing all steps */
                        for(int k=0;k<jSteps.length();k++){
                            Log.d(TAG, "Step: " + k + "; " + ((JSONObject)jSteps.get(k)).get("html_instructions"));
                            String polyline = "";
                            polyline = (String)((JSONObject)((JSONObject)jSteps.get(k)).get("polyline")).get("points");
                            List<LatLng> list = decodePoly(polyline);
                            path.addAll(list);
                            Log.d(TAG, "Step: " + k + " completed");
                        }
                    }

                    // Create route object using information from the route planning
                    Route r = new Route();
                    r.setPoints(path);
                    r.setDistance(distance);
                    r.setSummary(summary);

                    // Set locally required fields
                    r.setType(routeType);
                    r.setColour(getColour(r));
                    r.lookupAddresses(loc);

                    long routeTimestamp = DatabaseUtil.getInstance(context).fetchRouteTimestamp(routeId);
                    r.setStartTime(new Date(routeTimestamp));
                    routes.add(r);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }catch (Exception e){
                Log.d(TAG, "Error parsing route: " + e.getMessage());
            }

            return routes;
        }


        /**
         * Method to decode polyline points
         * Courtesy : http://jeffreysambells.com/2010/05/27/decoding-polylines-from-google-maps-direction-api-with-java
         * */
        private List<LatLng> decodePoly(String encoded) {

            List<LatLng> poly = new ArrayList<>();
            int index = 0, len = encoded.length();
            int lat = 0, lng = 0;

            while (index < len) {
                int b, shift = 0, result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lat += dlat;

                shift = 0;
                result = 0;
                do {
                    b = encoded.charAt(index++) - 63;
                    result |= (b & 0x1f) << shift;
                    shift += 5;
                } while (b >= 0x20);
                int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
                lng += dlng;

                LatLng p = new LatLng((((double) lat / 1E5)),
                        (((double) lng / 1E5)));
                poly.add(p);
            }

            return poly;
        }
    }

    private int getColour(Route r) {
        if (r.getType() == Route.RouteType.ROUTE_TAKEN) {
            return Color.RED;
        } else {
            switch(r.getId() % 3) {
                case 0:
                    return Color.BLUE;
                case 1:
                    return Color.GREEN;
                case 2:
                    return Color.MAGENTA;
                default:
                    return Color.GRAY;
            }
        }
    }
}

