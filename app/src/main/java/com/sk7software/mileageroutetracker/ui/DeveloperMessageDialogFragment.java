package com.sk7software.mileageroutetracker.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import com.sk7software.mileageroutetracker.AppConstants;
import com.sk7software.mileageroutetracker.R;
import com.sk7software.mileageroutetracker.model.DevMessage;
import com.sk7software.mileageroutetracker.network.NetworkCall;
import com.sk7software.mileageroutetracker.util.PreferencesUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeveloperMessageDialogFragment extends DialogFragment {

    private static final String TAG = DeveloperMessageDialogFragment.class.getSimpleName();
    private static final int UPGRADE_MESSAGE = -1;

    private CheckBox chkShowAgain;
    private int messageId;
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
        final View view = inflater.inflate(R.layout.content_developer_message, null);
        builder.setView(view);

        Bundle bundle = getArguments();
        final List<DevMessage> messages = new ArrayList<>();
        for (String key : bundle.keySet()) {
            Log.d(TAG, "Deserialize: " + key);
            messages.add((DevMessage) bundle.getSerializable(key));
        }

        builder.setMessage("Message")
                .setPositiveButton("Continue to app", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

        TextView txtMessage = (TextView) view.findViewById(R.id.txtMessage);
        messageId = messages.get(0).getId();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            txtMessage.setText(Html.fromHtml(messages.get(0).getText(), Html.FROM_HTML_MODE_COMPACT));
        } else {
            txtMessage.setText(Html.fromHtml(messages.get(0).getText()));
        }

        chkShowAgain = (CheckBox) view.findViewById(R.id.chkNeverAgain);

        if (messageId == UPGRADE_MESSAGE) {
            chkShowAgain.setVisibility(View.GONE);
            txtMessage.setMovementMethod(LinkMovementMethod.getInstance());
        }

        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        boolean noShowAgain = chkShowAgain.isChecked();
        String showAgain = (noShowAgain ? "N" : "Y");
        int userId = PreferencesUtil.getInstance().getIntPreference(AppConstants.PREFERENCE_USER_ID);

        Log.d(TAG, "Calling: [user:" + userId + ", message: " + messageId + ", showAgain: " + showAgain + "]");
        NetworkCall.updateDevMessage(context, userId, messageId, showAgain, new NetworkCall.NetworkCallback() {
            @Override
            public void onRequestCompleted(Object callbackData) {

            }

            @Override
            public void onError(Exception e) {

            }
        });
    }
}