package com.sk7software.mileageroutetracker.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.sk7software.mileageroutetracker.R;
import com.sk7software.mileageroutetracker.model.Route;

import java.util.List;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Created by Andrew on 04/03/2018
 */

public class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.RouteViewHolder>{
    private List<Route> routes;
    private LayoutInflater inflater;
    private int selectedItem = SELECTED_NONE;

    public static final int SELECTED_NONE = -1;
    private static final String TAG = RouteAdapter.class.getSimpleName();

    public RouteAdapter(List<Route> routes, LayoutInflater inflater) {
        this.routes = routes;
        this.inflater = inflater;
    }

    public void deselectRoute(int id) {
        selectedItem = SELECTED_NONE;
        notifyDataSetChanged();
    }

    public int getIdAtPosition(int position) {
        return routes.get(position).getId();
    }

    @Override
    public RouteViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View rowView = inflater.from(parent.getContext()).inflate(R.layout.list_item, parent, false);
        return new RouteViewHolder(rowView);
    }

    @Override
    public void onBindViewHolder(RouteViewHolder holder, int position) {
        holder.bindData(routes.get(position));
        holder.itemView.setSelected(selectedItem == position);
    }

    @Override
    public int getItemCount() {
        if (routes != null) {
            return routes.size();
        } else {
            return 0;
        }
    }

    public void toggleSelection(int pos) {
        selectedItem = pos;
        notifyDataSetChanged();
    }

    public void clearSelections() {
        selectedItem = SELECTED_NONE;
        notifyDataSetChanged();
    }

    public int getSelectedItem() {
        return selectedItem;
    }

    public void removeItem(int position) {
        routes.remove(position);
        notifyItemRemoved(position);
        notifyItemRangeChanged(position, routes.size());
        notifyDataSetChanged();
    }

    public class RouteViewHolder extends RecyclerView.ViewHolder {
        final TextView summary;
        final TextView distance;
        final TextView routeId;
        final View line;

        public int getRouteId() {
            return Integer.parseInt(routeId.getText().toString());
        }

        public RouteViewHolder(View itemView) {
            super(itemView);
            summary = (TextView)itemView.findViewById(R.id.txtRouteSummary);
            distance = (TextView)itemView.findViewById(R.id.txtDistance);
            line = (View)itemView.findViewById(R.id.imgLine);
            routeId = (TextView)itemView.findViewById(R.id.txtRouteId);
        }

        public void bindData(Route r) {
            summary.setText(r.getSummary());
            line.setBackgroundColor(r.getColour());
            distance.setText(r.getFormattedDistance());
            routeId.setText(String.valueOf(r.getId()));
        }
    }
}
