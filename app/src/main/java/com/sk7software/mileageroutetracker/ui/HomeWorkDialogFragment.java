package com.sk7software.mileageroutetracker.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.sk7software.mileageroutetracker.AppConstants;
import com.sk7software.mileageroutetracker.R;
import com.sk7software.mileageroutetracker.network.NetworkCall;
import com.sk7software.mileageroutetracker.util.PreferencesUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeWorkDialogFragment extends DialogFragment {

    private Context context;
    private EditText txtUserHome;
    private EditText txtUserWork;
    private TextView txtError;
    private DialogFragment parent;

    private static final String TAG = HomeWorkDialogFragment.class.getSimpleName();

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.content_home_work, null);
        builder.setView(view);

        Bundle b = getArguments();
        if (b.containsKey("parent")) {
            parent = (DialogFragment)b.getSerializable("parent");
        }

        txtUserHome = (EditText)view.findViewById(R.id.txtUserHome);
        txtUserWork = (EditText)view.findViewById(R.id.txtUserWork);
        txtError = (TextView)view.findViewById(R.id.txtError);
        final Button btnOK = (Button)view.findViewById(R.id.btnOK);
        final Button btnCancel = (Button)view.findViewById(R.id.btnCancel);

        builder.setMessage("Home and Work Locations");

        final String savedHome = PreferencesUtil.getInstance().getStringPreference(AppConstants.PREFERENCE_USER_HOME);
        final String savedWork = PreferencesUtil.getInstance().getStringPreference(AppConstants.PREFERENCE_USER_WORK);
        txtUserHome.setText(savedHome);
        txtUserWork.setText(savedWork);

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String homePostcode = txtUserHome.getText().toString().trim().toUpperCase();
                String workPostcode = txtUserWork.getText().toString().trim().toUpperCase();
                if ((homePostcode.length() > 0 && workPostcode.length() > 0) &&
                    (!homePostcode.equals(savedHome) || !workPostcode.equals(savedWork))) {
                    lookupDistance(homePostcode, workPostcode);
                } else {
                    // No change, so cancel
                    HomeWorkDialogFragment.this.getDialog().cancel();
                }
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                HomeWorkDialogFragment.this.getDialog().cancel();
            }
        });

        return builder.create();
    }

    private void lookupDistance(final String homePostcode, final String workPostcode) {
        // Use Google distance matrix API to get distance between home and work
        // https://maps.googleapis.com/maps/api/distancematrix/json?origins=<start postcode>&destinations=<end postcode>&units=metric&mode=driving&key=<API Key>
        NetworkCall.fetchRouteDistance(context, homePostcode, workPostcode,
                new NetworkCall.NetworkCallback() {
                    @Override
                    public void onRequestCompleted(Object callbackData) {
                        Integer distanceM = Integer.parseInt(callbackData.toString());
                        Log.d(TAG, "Home-work distance: " + distanceM + "m");

                        PreferencesUtil.getInstance()
                                .addPreference(AppConstants.PREFERENCE_USER_HOME, homePostcode);
                        PreferencesUtil.getInstance()
                                .addPreference(AppConstants.PREFERENCE_USER_WORK, workPostcode);
                        if (distanceM > 0) {
                            PreferencesUtil.getInstance()
                                    .addPreference(AppConstants.PREFERENCE_USER_WORK_DISTANCE_M, distanceM);
                            txtError.setText("");

                            // Update user dialog if it launched this
                            if (parent instanceof DialogCloseListener) {
                                ((DialogCloseListener)parent).handleDialogClose();
                            }

                            HomeWorkDialogFragment.this.getDialog().cancel();
                        } else {
                            txtError.setText("Distance calculated as zero.  Please check postcodes and retry.");
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Error looking up distance: " + e.getMessage());
                        txtError.setText("Error occurred. Check network connection and postcodes and retry.  " +
                                "If error persists please try later.");
                    }
                });
    }
}
