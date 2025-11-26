package com.example.feedflow;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class AlertsActivity extends AppCompatActivity {

    LinearLayout alertsContainer;
    Switch switchMarkAll;

    // Firestore reference
    private FirebaseFirestore db;
    private CollectionReference alertsRef;

    @SuppressLint({"NonConstantResourceId", "MissingInflatedId"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts);

        alertsContainer = findViewById(R.id.alertsContainer);
        switchMarkAll = findViewById(R.id.switchMarkAll);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        // Firestore init
        db = FirebaseFirestore.getInstance();
        alertsRef = db.collection("FeedFlow").document("Device001").collection("alerts");

        // 🔹 Fetch alerts in real-time
        fetchAlerts();

        // 🔹 Switch logic to mark all as Read/Warning
        switchMarkAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String newStatus = isChecked ? "Read" : "Warning";
            alertsRef.get().addOnSuccessListener(querySnapshot -> {
                for (DocumentSnapshot doc : querySnapshot) {
                    alertsRef.document(doc.getId()).update("status", newStatus);
                }
            });
        });
        setupBottomNavigation(bottomNav);
    }
    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        bottomNav.setSelectedItemId(R.id.nav_alerts);

        bottomNav.setOnItemSelectedListener(item -> {
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
                return true;
            }
            return false;
        });
    }
    // 🔹 Method to add a new alert into Firestore (example only)

    // Fetch alerts from Firestore in real-time
    private void fetchAlerts() {
        alertsRef.addSnapshotListener((querySnapshot, e) -> {
            if (e != null || querySnapshot == null) return;

            alertsContainer.removeAllViews(); // clear old alerts
            for (DocumentSnapshot doc : querySnapshot) {
                String title = doc.getString("title");
                String desc = doc.getString("description");
                String time = doc.getString("time");
                String status = doc.getString("status");

                addAlert(title, desc, time, status);
            }
        });
    }

    // 🔹 Display alert card in UI
    private void addAlert(String title, String description, String time, String status) {
        CardView alertView = (CardView)
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
        statusView.setText(status);

        // Change color based on status
        if ("Read".equals(status)) {
            statusView.setTextColor(0xFF0000FF); // Bright Blue
        } else {
            statusView.setTextColor(0xFFFF0000); // Bright Red
        }

        alertsContainer.addView(alertView);
    }
}
