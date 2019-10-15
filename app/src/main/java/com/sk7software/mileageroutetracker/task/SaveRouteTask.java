package com.sk7software.mileageroutetracker.task;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.Log;

import com.sk7software.mileageroutetracker.db.DatabaseUtil;
import com.sk7software.mileageroutetracker.model.Route;
import com.sk7software.mileageroutetracker.network.NetworkCall;
import com.sk7software.mileageroutetracker.ui.ActivityUpdateInterface;
import com.sk7software.mileageroutetracker.ui.SaveJourneyDialogFragment;

import java.util.List;

public class SaveRouteTask extends AsyncTask<Route, String, String> {

    private static final String TAG = SaveRouteTask.class.getSimpleName();

    ActivityUpdateInterface uiUpdate;
    Context context;

    public SaveRouteTask(ActivityUpdateInterface uiUpdate, Context context) {
        this.uiUpdate = uiUpdate;
        this.context = context;
    }

    @Override
    protected void onPostExecute(String result) {
    }

    @Override
    protected String doInBackground(Route... routes) {
        Route route = routes[0];
        DatabaseUtil.getInstance(context).saveRoute(route);

        // Only attempt upload if route has been calculated
        if (route.getDistance() >= 0) {

            // Upload to server
            NetworkCall.uploadRoute(context, route,
                    uiUpdate, new NetworkCall.NetworkCallback() {
                        @Override
                        public void onRequestCompleted(Object callbackData) {
                            Log.d(TAG, "Route uploaded");
                        }

                        @Override
                        public void onError(Exception e) {
                            Log.d(TAG, "Route upload failed: " + e.getMessage());
                            showRouteUploadError();
                        }
                    });
        }
        return "Done";
    }

    private void showRouteUploadError() {
        new AlertDialog.Builder(context)
                .setTitle("Route Upload")
                .setMessage("There was an error saving the route. " +
                        "It has been stored on your device and will be saved to the server " +
                        "next time you launch the app.")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

}