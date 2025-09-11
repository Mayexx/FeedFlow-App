package com.example.feedflow;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class TemperatureStatsFragment extends Fragment {

    // Declare views
    private TextView tvLastUpdated, tvTempCurrent, tvTempAverage;
    private TextView tvOptimalTime, tvBelowOptimal, tvAboveOptimal;
    private ImageView ivTempGraph;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate layout
        return inflater.inflate(R.layout.fragment_temperature_stats, container, false);
    }
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind views
        tvLastUpdated = view.findViewById(R.id.tvLastUpdated);
        tvTempCurrent = view.findViewById(R.id.tvTempCurrent);
        tvTempAverage = view.findViewById(R.id.tvTempAverage);
        tvOptimalTime = view.findViewById(R.id.tvOptimalTime);
        tvBelowOptimal = view.findViewById(R.id.tvBelowOptimal);
        tvAboveOptimal = view.findViewById(R.id.tvAboveOptimal);
        ivTempGraph = view.findViewById(R.id.ivTempGraph);

        SharedPreferences prefs = requireActivity().getSharedPreferences("FeedFlowPrefs", getContext().MODE_PRIVATE);

        String latestTempStr = prefs.getString("latestTemperature", "N/A");

        // Update views
        if (!latestTempStr.equals("N/A")) {
            tvTempCurrent.setText("Current: " + latestTempStr + "°C");
            tvLastUpdated.setText("Last Updated: Just now");
        } else {
            tvTempCurrent.setText("Current: --°C");
            tvLastUpdated.setText("Last Updated: --");
        }
        tvTempAverage.setText("Average: --°C");
        tvOptimalTime.setText("Time in optimal range: --%");
        tvBelowOptimal.setText("Time below optimal: --%");
        tvAboveOptimal.setText("Time above optimal: --%");
    }

}
