package com.sk7software.mileageroutetracker.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.core.view.GestureDetectorCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;

import com.sk7software.mileageroutetracker.R;
import com.sk7software.mileageroutetracker.model.Route;

import java.util.ArrayList;
import java.util.List;

public class EndJourneyDialogFragment extends DialogFragment
        implements RecyclerView.OnItemTouchListener, View.OnClickListener {

    private RecyclerView routesView;
    private RouteAdapter routeAdapter;
    private GestureDetectorCompat gestureDetector;
    private int routeToShow = -1;
    private Context context;

    private static final String TAG = EndJourneyDialogFragment.class.getSimpleName();

    public interface OnDialogDismissListener {
        public void onDismiss(boolean update, int selectedRoute);
    }

    @Override
    public void onAttach(Context context){
        super.onAttach(context);
        this.context = context;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle bundle = getArguments();
        final List<Route> routes = new ArrayList<>();
        for (String key : bundle.keySet()) {
            Log.d(TAG, "Deserialize: " + key);
            routes.add((Route)bundle.getSerializable(key));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.content_end_journey, null);

        builder.setView(view);

        routeAdapter = new RouteAdapter(routes, LayoutInflater.from(context));

        routesView = (RecyclerView)view.findViewById(R.id.lstRoutes);
        routesView.addOnItemTouchListener(this);
        routesView.setLayoutManager(new LinearLayoutManager(context));
        routesView.setAdapter(routeAdapter);
        gestureDetector = new GestureDetectorCompat(context, new RoutesOnGestureDetectListener());

        builder.setMessage("Confirm Journey")
                .setPositiveButton("Save Route", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Save selected route
                        int selectedRoute = routeAdapter.getSelectedItem();
                        if (selectedRoute != RouteAdapter.SELECTED_NONE) {
                            Log.d(TAG, "Selected route: " + selectedRoute);
                            boolean tooLong = isActualRouteTooLong(routes, selectedRoute);

                            DialogFragment saveJourney = new SaveJourneyDialogFragment();
                            Bundle bundle = new Bundle();
                            bundle.putSerializable("route", routes.get(selectedRoute));
                            bundle.putBoolean("warn", tooLong);
                            saveJourney.setArguments(bundle);
                            saveJourney.show(getFragmentManager(), "journey");
                            EndJourneyDialogFragment.this.getDialog().cancel();
                        }
                    }
                })
                .setNegativeButton("View Map", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        routeToShow = routeAdapter.getSelectedItem();
                        EndJourneyDialogFragment.this.getDialog().cancel();
                    }
                });

        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public void onClick(View view) {
        if (view == null) return;
        Log.d(TAG, "onClick: view id: " + view.getId());

        if (view.getId() == R.id.list_item) {
            int idx = routesView.getChildAdapterPosition(view);
            myToggleSelection(idx);
        }
    }

    private void myToggleSelection(int idx) {
        routeAdapter.toggleSelection(idx);
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        Log.d(TAG, "onInterceptTouchEvent");
        gestureDetector.onTouchEvent(e);
        return false;
    }

    @Override
    public void onTouchEvent(RecyclerView rv, MotionEvent e) {

    }

    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {

    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        Activity activity = getActivity();
        if (activity instanceof EndJourneyDialogFragment.OnDialogDismissListener) {
            ((EndJourneyDialogFragment.OnDialogDismissListener)activity).onDismiss(false, routeToShow);
        }
    }

    private boolean isActualRouteTooLong(List<Route> routes, int selectedRoute) {
        if (routes.get(selectedRoute).getType() != Route.RouteType.ROUTE_TAKEN) {
            return false;
        }

        // Check whether route taken is reasonable based on best suggestion
        int suggestedDistance = routes.get(0).getDistance();
        int takenDistance = routes.get(selectedRoute).getDistance();

        return (double)takenDistance > (double)suggestedDistance * 1.2;
    }

    private class RoutesOnGestureDetectListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            View view = routesView.findChildViewUnder(e.getX(), e.getY());
            onClick(view);
            return super.onSingleTapConfirmed(e);
        }

        public void onLongPress(MotionEvent e) {
            super.onLongPress(e);
        }
    }
}