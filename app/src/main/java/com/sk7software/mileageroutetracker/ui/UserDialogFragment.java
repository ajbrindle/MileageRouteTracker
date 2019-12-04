package com.sk7software.mileageroutetracker.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.graphics.Color;
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
import java.util.Map;

public class UserDialogFragment extends DialogFragment {

    private Context context;
    private EditText txtUserHome;
    private EditText txtUserWork;
    private TextView txtUserPrompt;
    private EditText txtUserName;

    private static final String TAG = UserDialogFragment.class.getSimpleName();
    private static final int MODE_USER_ID_LOOKUP = 0;
    private static final int MODE_USER_NAME_LOOKUP = 1;

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
        View view = inflater.inflate(R.layout.content_user, null);
        builder.setView(view);

        txtUserPrompt = (TextView)view.findViewById(R.id.txtUserPrompt);
        txtUserHome = (EditText)view.findViewById(R.id.txtUserHome);
        txtUserWork = (EditText)view.findViewById(R.id.txtUserWork);
        txtUserName = (EditText)view.findViewById(R.id.txtUserName);
        final Button btnOK = (Button)view.findViewById(R.id.btnOK);

        builder.setMessage("User Name");

        txtUserHome.setText(PreferencesUtil.getInstance().getStringPreference(AppConstants.PREFERENCE_USER_HOME));
        txtUserWork.setText(PreferencesUtil.getInstance().getStringPreference(AppConstants.PREFERENCE_USER_WORK));

        // Set user name in field
        setUserNameText();

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String userName = txtUserName.getText().toString();
                if (userName.length() > 0) {
                    // Check if it has changed
                    String savedUserName = PreferencesUtil.getInstance()
                            .getStringPreference(AppConstants.PREFERENCE_USER_NAME);
                    if (!userName.equals(savedUserName)) {
                        Map<String, String> param = new HashMap<>();
                        param.put("name", userName);
                        PreferencesUtil.getInstance()
                                .addPreference(AppConstants.PREFERENCE_USER_NAME, userName);
                        lookupUser(param, MODE_USER_ID_LOOKUP);
                    } else {
                        // No change, so cancel
                        UserDialogFragment.this.getDialog().cancel();
                    }
                }
            }
        });
        return builder.create();
    }

    private void setHomeWorkDistance() {
        String homePostCode = txtUserHome.getText().toString();
        String workPostCode = txtUserWork.getText().toString();

        // Use Google distance matrix API to get distance between home and work
        // https://maps.googleapis.com/maps/api/distancematrix/json?origins=<start postcode>&destinations=<end postcode>&units=metric
    }

    private void setUserNameText() {
        Integer userId = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_USER_ID);
        String userName = PreferencesUtil.getInstance().getStringPreference(AppConstants.PREFERENCE_USER_NAME);
        int mode = 0;

        if (userName != null && userName.length() > 0) {
            txtUserName.setText(userName);
        } else if (userId != null && userId > 0) {
            Map<String, String> param = new HashMap<>();
            param.put("id", String.valueOf(userId));
            lookupUser(param, MODE_USER_NAME_LOOKUP);
        }
    }

    private void lookupUser(Map<String, String> user, final int mode) {
        NetworkCall.checkUser(context, user,
                new NetworkCall.NetworkCallback() {
                    @Override
                    public void onRequestCompleted(Object callbackData) {
                        Map<String, String> userIdMap = (Map<String, String>)callbackData;
                        if (userIdMap.containsKey("data")) {
                            String id = userIdMap.get("data");
                            if (!"0".equals(id)) {
                                setResult(id, mode);
                            } else {
                                txtUserPrompt.setText("User name not found, please try again");
                                txtUserPrompt.setTextColor(Color.RED);
                            }
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.d(TAG, "User lookup error: " + e.getMessage());
                        txtUserPrompt.setText("There was an error looking up your user. Please try again later.");
                        txtUserPrompt.setTextColor(Color.RED);
                    }
                });

    }

    private void setResult(String id, int mode) {
        switch (mode) {
            case MODE_USER_ID_LOOKUP:
                PreferencesUtil.getInstance()
                        .addPreference(AppConstants.PREFERENCE_USER_ID, Integer.parseInt(id));
                UserDialogFragment.this.getDialog().cancel();
                break;
            case MODE_USER_NAME_LOOKUP:
                PreferencesUtil.getInstance()
                        .addPreference(AppConstants.PREFERENCE_USER_NAME, id);
                txtUserName.setText(id);
                break;
            default:
                // Do nothing
        }
    }
}
