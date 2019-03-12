package com.sk7software.mileageroutetracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import com.sk7software.mileageroutetracker.R;

import java.util.Map;

/**
 * Created by Andrew on 30/03/2018.
 */

public class EnterJourneyDialogFragment extends DialogFragment {

    private String startPostcode;
    private String endPostcode;

    private static final String TAG = EnterJourneyDialogFragment.class.getSimpleName();

    public interface OnDialogDismissListener {
        public void onDismiss(String startPostcode, String endPostcode);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View view = inflater.inflate(R.layout.content_enter_journey, null);
        builder.setView(view);

        builder.setMessage("Save")
                .setPositiveButton("Save Route", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        startPostcode = ((EditText)view.findViewById(R.id.txtStart)).getText().toString();
                        endPostcode = ((EditText)view.findViewById(R.id.txtEnd)).getText().toString();
                        Log.d(TAG, "Journey from: " + startPostcode + " to " + endPostcode);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });

        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        Activity activity = getActivity();
        if (activity instanceof EnterJourneyDialogFragment.OnDialogDismissListener) {
            ((EnterJourneyDialogFragment.OnDialogDismissListener)activity).onDismiss(startPostcode, endPostcode);
        }
    }
}
