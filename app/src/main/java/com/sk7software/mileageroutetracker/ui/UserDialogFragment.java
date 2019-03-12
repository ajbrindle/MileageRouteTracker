package com.sk7software.mileageroutetracker.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.sk7software.mileageroutetracker.AppConstants;
import com.sk7software.mileageroutetracker.R;
import com.sk7software.mileageroutetracker.network.NetworkCall;
import com.sk7software.mileageroutetracker.util.PreferencesUtil;

import java.util.Map;

public class UserDialogFragment extends DialogFragment {

    private Context context;

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

        final TextView txtUserPrompt = (TextView)view.findViewById(R.id.txtUserPrompt);
        final EditText txtUserName = (EditText)view.findViewById(R.id.txtUserName);
        final Button btnOK = (Button)view.findViewById(R.id.btnOK);

        builder.setMessage("User Name");

        btnOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String userName = txtUserName.getText().toString();
                if (userName.length() > 0) {
                    NetworkCall.checkUser(context, userName,
                            new NetworkCall.NetworkCallback() {
                                @Override
                                public void onRequestCompleted(Map<String, Integer> callbackData) {
                                    if (callbackData.containsKey("id")) {
                                        int id = callbackData.get("id");
                                        if (id > 0) {
                                            PreferencesUtil.getInstance()
                                                    .addPreference(AppConstants.PREFERENCE_USER_ID, id);
                                            UserDialogFragment.this.getDialog().cancel();
                                        } else {
                                            txtUserPrompt.setText("User name not found, please try again");
                                            txtUserPrompt.setTextColor(Color.RED);
                                        }
                                    }
                                }

                                @Override
                                public void onError(Exception e) {
                                    txtUserPrompt.setText("There was an error looking up your user. Please try again later.");
                                    txtUserPrompt.setTextColor(Color.RED);
                                }
                            });
                }
            }
        });
//                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
//                    @Override
//                    public void onClick(DialogInterface dialog, int id) {
//        });

        return builder.create();
    }
}
