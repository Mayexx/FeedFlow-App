package com.example.feedflow;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class StatsPagerAdapter extends FragmentStateAdapter {

    private TemperatureStatsFragment temperatureStatsFragment;
    private FeedingStatsFragment feedingStatsFragment;

    public StatsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            if (temperatureStatsFragment == null) {
                temperatureStatsFragment = new TemperatureStatsFragment();
            }
            return temperatureStatsFragment;
        } else {
            if (feedingStatsFragment == null) {
                feedingStatsFragment = new FeedingStatsFragment();
            }
            return feedingStatsFragment;
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    // Add public getter methods to access fragments

    public TemperatureStatsFragment getTemperatureStatsFragment() {
        return temperatureStatsFragment;
    }

    public FeedingStatsFragment getFeedingStatsFragment() {
        return feedingStatsFragment;
    }
}
