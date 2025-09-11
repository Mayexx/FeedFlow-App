package com.example.feedflow;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AlertsActivity extends AppCompatActivity {

    LinearLayout alertsContainer;
    Switch switchMarkAll;

    @SuppressLint({"NonConstantResourceId", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts);

        alertsContainer = findViewById(R.id.alertsContainer);
        switchMarkAll = findViewById(R.id.switchMarkAll);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        // Add sample alerts
        addAlert("High Water Temperature",
                "Water temperature in Pond #1 is 32°C. Optimal range is 25–30°C.",
                "7:32 AM");

        addAlert("Low Feed Level",
                "Feed storage level is below 20%. Estimated to last 3 more days.",
                "9:45 AM");

        // Switch logic
        switchMarkAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (int i = 0; i < alertsContainer.getChildCount(); i++) {
                // Use CardView since alert_item root is CardView
                androidx.cardview.widget.CardView card =
                        (androidx.cardview.widget.CardView) alertsContainer.getChildAt(i);

                TextView status = card.findViewById(R.id.alertStatus);
                if (isChecked) {
                    status.setText("Read");
                    status.setTextColor(0xFF4CAF50); // Green
                } else {
                    status.setText("Warning");
                    status.setTextColor(0xFFFF8800); // Orange
                }
            }
        });

        setupBottomNavigation(bottomNav);
    }

    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_alerts); // highlight Alerts tab

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                startActivity(new Intent(AlertsActivity.this, HomeActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_stats) {
                startActivity(new Intent(AlertsActivity.this, StatsActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_notes) {
                startActivity(new Intent(AlertsActivity.this, NotesActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_alerts) {
                return true; // already here
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(AlertsActivity.this, SettingsActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            }
            return false;
        });
    }

    private void addAlert(String title, String description, String time) {
        // Inflate as CardView instead of LinearLayout
        androidx.cardview.widget.CardView alertView = (androidx.cardview.widget.CardView)
                getLayoutInflater().inflate(R.layout.alert_item, alertsContainer, false);

        // Bind views
        TextView titleView = alertView.findViewById(R.id.alertTitle);
        TextView descView = alertView.findViewById(R.id.alertDescription);
        TextView timeView = alertView.findViewById(R.id.alertTime);
        TextView statusView = alertView.findViewById(R.id.alertStatus);

        // Set values
        titleView.setText(title);
        descView.setText(description);
        timeView.setText(time);
        statusView.setText("Warning");

        alertsContainer.addView(alertView);
    }
}
