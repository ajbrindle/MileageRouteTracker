package com.sk7software.mileageroutetracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.gms.maps.model.LatLng;
import com.sk7software.mileageroutetracker.AppConstants;
import com.sk7software.mileageroutetracker.R;
import com.sk7software.mileageroutetracker.db.DatabaseUtil;
import com.sk7software.mileageroutetracker.location.LocationUtil;
import com.sk7software.mileageroutetracker.model.Route;
import com.sk7software.mileageroutetracker.network.NetworkCall;
import com.sk7software.mileageroutetracker.task.SaveRouteTask;
import com.sk7software.mileageroutetracker.util.PreferencesUtil;

import java.util.List;

/**
 * Created by Andrew on 09/03/2018.
 */

public class SaveJourneyDialogFragment extends DialogFragment implements ActivityUpdateInterface {

    private boolean resetDisplay = false;
    private Context context;
    private AlertDialog.Builder progressDialogBuilder;
    private Dialog progressDialog;

    private static final String TAG = SaveJourneyDialogFragment.class.getSimpleName();

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle bundle = getArguments();
        Route chosenRoute = new Route();
        boolean routeCalculated = false;

        if (bundle != null && bundle.containsKey("route")) {
            // Populate chosen route from data passed in
            chosenRoute = (Route) bundle.getSerializable("route");
            routeCalculated = true;
        } else {
            // Set chosen route from data captured on current session
            chosenRoute.setPoints(DatabaseUtil.getInstance(context)
                    .fetchStartEndPoints(PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_ROUTE_ID))
                    .getPoints());
            chosenRoute.lookupAddresses(LocationUtil.getInstance());

            // Use negative distance to indicate that route still needs to be calculated
            chosenRoute.setDistance(-99);
        }

        Boolean showWarning = false;

        if (bundle != null && bundle.containsKey("warn")) {
            showWarning = bundle.getBoolean("warn");
        }

        final Route route = chosenRoute;

        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.content_save_route, null);
        builder.setView(view);

        final RadioGroup options = (RadioGroup)view.findViewById(R.id.optJourneyType);
        final EditText txtDescription = (EditText)view.findViewById(R.id.txtDescription);
        final TextView txtLabel = (TextView)view.findViewById(R.id.txtLabelOther);
        final RadioButton btnOther = (RadioButton)view.findViewById(R.id.optJourneyOther);
        final CheckBox chkPassenger = (CheckBox)view.findViewById(R.id.chkPassenger);
        final CheckBox chkDeduct = (CheckBox)view.findViewById(R.id.chkDeduct);

        txtDescription.setVisibility(View.GONE);
        txtLabel.setVisibility(View.GONE);

        if (showWarning) {
            TextView txtWarn = (TextView)view.findViewById(R.id.txtWarning);
            txtWarn.setText("Please double-check this route before saving. It is a lot longer than " +
                    "the one recommended by Google. To check it, select CANCEL below and review.");
            txtWarn.setVisibility(View.VISIBLE);
        }

        btnOther.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                txtDescription.setVisibility(View.VISIBLE);
                txtLabel.setVisibility(View.VISIBLE);
                txtDescription.requestFocus();
                InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(txtDescription, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        builder.setMessage("Save")
        .setPositiveButton("Save Route", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // Save route
                int checkedId = options.getCheckedRadioButtonId();
                RadioButton checkedOption = (RadioButton)options.findViewById(checkedId);

                int userId = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_USER_ID);
                route.setUserId(userId);

                int routeId = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_ROUTE_ID);
                route.setId(routeId);

                boolean hasPassenger = chkPassenger.isChecked();
                route.setPassenger(hasPassenger);

                // Deduct home-work mileage if required
                if (chkDeduct.isChecked()) {
                    route.setAdjustedDistance(getAdjustedDistance(route.getDistance()));
                } else {
                    route.setAdjustedDistance(route.getDistance());
                }

                if (checkedOption.getId() == R.id.optJourneyOther) {
                    route.setSummary(txtDescription.getText().toString());
                } else {
                    String summary = checkedOption.getText().toString();
                    route.setSummary(summary);
                }

                // Kick off background task to store route and upload
                progressDialogBuilder = new AlertDialog.Builder(context);
                progressDialogBuilder.setView(R.layout.progress);
                setProgress(true, "Saving Route");

                // Create task to save route in the background
                SaveRouteTask task = new SaveRouteTask(SaveJourneyDialogFragment.this, context);
                task.execute(route);
                resetDisplay = true;
            }
        })
        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                resetDisplay = false;
            }
        });

        // Create the AlertDialog object and return it
        return builder.create();
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

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        Activity activity = getActivity();
        if (activity instanceof EndJourneyDialogFragment.OnDialogDismissListener) {
            ((EndJourneyDialogFragment.OnDialogDismissListener)activity).onDismiss(resetDisplay, -1);
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
        // Not implemented
    }

    private int getAdjustedDistance(int originalDistance) {
        int homeWorkDistance = PreferencesUtil.getInstance()
                .getIntPreference(AppConstants.PREFERENCE_USER_WORK_DISTANCE_M);

        if (originalDistance < 0) {
            // Original distance has not been worked out so return a dummy value that
            // will indicate that an adjusted distance should be calculated later
            return -999;
        } else {
            int adjustedDistance = originalDistance - homeWorkDistance;
            if (adjustedDistance < 0) {
                adjustedDistance = 0;
            }

            return adjustedDistance;
        }
    }
}
