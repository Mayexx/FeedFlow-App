package com.example.feedflow;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class StatsPagerAdapter extends FragmentStateAdapter {
    public StatsPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return (position == 0)
                ? new TemperatureStatsFragment()
                : new FeedingStatsFragment();
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
