package com.example.feedflow;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;

public class StatsActivity extends AppCompatActivity {

    TabLayout tabLayout;
    ViewPager2 viewPager;
    StatsPagerAdapter pagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        // Set up ViewPager and Tabs
        pagerAdapter = new StatsPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        tabLayout.addTab(tabLayout.newTab().setText("Temperature"));
        tabLayout.addTab(tabLayout.newTab().setText("Feeding"));

        // Link tab selection with ViewPager
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            public void onTabUnselected(TabLayout.Tab tab) {}
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            public void onPageSelected(int position) {
                tabLayout.selectTab(tabLayout.getTabAt(position));
            }
        });

        setCustomFontOnTabs(tabLayout);
        setupBottomNavigation(bottomNav);
    }

    private void setCustomFontOnTabs(TabLayout tabLayout) {
        Typeface typeface = ResourcesCompat.getFont(this, R.font.poppins_bold);

        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = tabLayout.getTabAt(i);
            if (tab != null && tab.getText() != null) {
                TextView tabTextView = new TextView(this);
                String tabText = tab.getText().toString();
                tabTextView.setText(tabText);
                tabTextView.setTextSize(14);
                tabTextView.setTypeface(typeface, Typeface.BOLD);
                tabTextView.setTextColor(Color.BLACK);
                tabTextView.setGravity(android.view.Gravity.CENTER);

                // Add content description for accessibility
                tabTextView.setContentDescription(tabText);
                tab.setCustomView(tabTextView);
            }
        }
    }

    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        bottomNav.setSelectedItemId(R.id.nav_stats);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_stats) {
                return true; // Already here
            } else if (id == R.id.nav_notes) {
                startActivity(new Intent(this, NotesActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_alerts) {
                startActivity(new Intent(this, AlertsActivity.class));
                overridePendingTransition(0, 0);
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }

            return false;
        });
    }
}
