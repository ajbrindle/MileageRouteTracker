package com.sk7software.mileageroutetracker.network;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.mileageroutetracker.AppConstants;
import com.sk7software.mileageroutetracker.model.Route;
import com.sk7software.mileageroutetracker.ui.UpdateUICallback;
import com.sk7software.mileageroutetracker.util.LocationUtil;
import com.sk7software.mileageroutetracker.util.PreferencesUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Andrew on 04/03/2018
 */

public class FetchUrl extends AsyncTask<Void, Void, String> {

    private String url;
    private Route.RouteType routeType;
    private UpdateUICallback listener;
    private LocationUtil loc;

    private ProgressDialog progressDialog;

    private static final String TAG = FetchUrl.class.getSimpleName();

    public FetchUrl(String url, Route.RouteType routeType, LocationUtil loc, Activity activity, UpdateUICallback listener) {
        this.url = url;
        this.routeType = routeType;
        this.loc = loc;
        this.listener = listener;
        this.progressDialog = new ProgressDialog(activity);
    }

    @Override
    protected String doInBackground(Void... params) {

        // For storing data from web service
        String data = "";

        try {
            // Fetching the data from web service
            data = downloadUrl(url);
            Log.d("Background Task data", data.toString());
        } catch (Exception e) {
            Log.d("Background Task", e.toString());
        }
        return data;
    }

    @Override
    protected void onPreExecute() {
        progressDialog.setMessage("Fetching Routes");
        progressDialog.show();
    }

    @Override
    protected void onPostExecute(String jsonData) {
        super.onPostExecute(jsonData);

        JSONObject jObject;
        List<Route> routes = null;

        try {
            jObject = new JSONObject(jsonData);
            Log.d(TAG, jsonData);
            DataParser parser = new DataParser();
            Log.d(TAG, parser.toString());

            // Starts parsing data
            routes = parser.parse(jObject, routeType);
            Log.d(TAG,"Executing routes");
            Log.d(TAG, routes.toString());
            Log.d(TAG, "Number of routes: " + routes.size());

        } catch (Exception e) {
            Log.d("ParserTask",e.toString());
            e.printStackTrace();
        }

        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }

        listener.updateUI(routes);
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
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            data = sb.toString();
            Log.d("downloadUrl", data.toString());
            br.close();

        } catch (Exception e) {
            Log.d("Exception", e.toString());
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

                        /** Traversing all steps */
                        for(int k=0;k<jSteps.length();k++){
                            String polyline = "";
                            polyline = (String)((JSONObject)((JSONObject)jSteps.get(k)).get("polyline")).get("points");
                            List<LatLng> list = decodePoly(polyline);
                            path.addAll(list);
                        }
                    }
                    Route r = new Route();
                    r.setPoints(path);
                    r.setDistance(distance);
                    r.setType(routeType);
                    r.setSummary(summary);
                    r.setColour(getColour(r));
                    r.lookupAddresses(loc);

                    long routeTimestamp = PreferencesUtil.getInstance()
                            .getLongPreference(AppConstants.PREFERENCE_ROUTE_START_TIME);
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

