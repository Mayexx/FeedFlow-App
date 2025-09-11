package com.example.feedflow;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class FeedingStatsFragment extends Fragment {

    private TextView tvLastUpdated, tvTodayFeed, tvFeedLevel, tvAverageFeed, tvFeedEfficiency;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_feeding_stats, container, false);

        tvLastUpdated = view.findViewById(R.id.tvLastUpdated);
        tvTodayFeed = view.findViewById(R.id.tvTodayFeed);
        tvFeedLevel = view.findViewById(R.id.tvFeedLevel);
        tvAverageFeed = view.findViewById(R.id.tvAverageFeed);
        tvFeedEfficiency = view.findViewById(R.id.tvFeedEfficiency);

        loadFeedingData();

        return view;
    }

    private void loadFeedingData() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("FeedData", Context.MODE_PRIVATE);

        int feedLevel = prefs.getInt("feedLevel", -1);
        long lastUpdated = prefs.getLong("lastUpdated", 0);

        // --- NEW: Work with per-day feeding totals ---
        String todayKey = getTodayKey();
        float todayFeed = prefs.getFloat(todayKey, 0f);

        // Sum for weekly feed (last 7 days)
        float weeklyTotal = 0f;
        int daysWithData = 0;
        for (int i = 0; i < 7; i++) {
            String key = getDayKey(i);
            float dailyFeed = prefs.getFloat(key, 0f);
            if (dailyFeed > 0) {
                weeklyTotal += dailyFeed;
                daysWithData++;
            }
        }

        // Cumulative data
        float totalWeightGain = prefs.getFloat("weightGain", 0f);

        // Last updated
        if (lastUpdated > 0) {
            String formattedDate = DateFormat.getDateTimeInstance().format(new Date(lastUpdated));
            tvLastUpdated.setText("Last Updated: " + formattedDate);
        } else {
            tvLastUpdated.setText("Last Updated: --");
        }

        // Today
        tvTodayFeed.setText("Today: " + todayFeed + " kg");
        tvFeedLevel.setText("Feed Level: " + (feedLevel >= 0 ? feedLevel + "%" : "--%"));

        // Average Daily Feed (weekly)
        if (daysWithData > 0) {
            float averageFeed = weeklyTotal / daysWithData;
            tvAverageFeed.setText("Average Daily Feed: " + String.format("%.2f", averageFeed) + " kg");
        } else {
            tvAverageFeed.setText("Average Daily Feed: --");
        }

        // Feed Efficiency (FCR)
        if (totalWeightGain > 0) {
            float fcr = weeklyTotal / totalWeightGain;
            tvFeedEfficiency.setText("Feed Efficiency: " + String.format("%.2f", fcr) + " FCR");
        } else {
            tvFeedEfficiency.setText("Feed Efficiency: --");
        }
    }

    // Helper: unique key for today's feeding
    private String getTodayKey() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return "feed_" + sdf.format(new Date());
    }

    // Helper: key for a past day (0 = today, 1 = yesterday, etc.)
    private String getDayKey(int daysAgo) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return "feed_" + sdf.format(cal.getTime());
    }
}
