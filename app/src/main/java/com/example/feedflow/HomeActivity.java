package com.example.feedflow;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private TextView txtTemperature, txtFeedLevel, txtFeedLevelStatus, txtDeviceName, txtBtStatus, txtFeedAmount;
    private Button btnFeedNow, btnIncrease, btnDecrease;
    private LinearLayout feedProgressCard;  // <-- move to class level
    private ProgressBar progressFeed;


    private FirebaseFirestore db;
    private BluetoothSerial btSerial;

    private int feedAmount = 5;
    private static final int FEED_MAX = 10;
    private static final int FEED_MIN = 1;
    private float currentWeight = 0.0f;


    private enum ConnectionState {DISCONNECTED, CONNECTING, CONNECTED}
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;

    private final String ESP_MAC = "B4:88:08:B4:03:6A";  // ← Only ONE MAC address

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        LinearLayout feedProgressCard = findViewById(R.id.feedProgressCard);

        initViews();
        db = FirebaseFirestore.getInstance();
        requestBluetoothPermissions();

        // ------------------- Initialize Bluetooth -------------------
        btSerial = new BluetoothSerial(this);

        // Callbacks (only ONCE)
        btSerial.setCallbacks(new BluetoothSerial.DataCallback() {
            @Override
            public void onDataReceived(byte[] rawData) {
                String data = new String(rawData).trim();
                Log.d("BT_RAW", "Received: " + data);
                handleBluetoothData(data);
            }

            @Override
            public void onConnectionFailed(Exception e) {
                Log.e("BT_CONNECT", "Failed: " + e.getMessage());
                Toast.makeText(HomeActivity.this, "Bluetooth Failed", Toast.LENGTH_SHORT).show();
                setConnectionState(ConnectionState.DISCONNECTED);
            }

            @Override
            public void onDisconnected() {
                Log.w("BT_CONNECT", "Disconnected");
                Toast.makeText(HomeActivity.this, "Bluetooth Disconnected", Toast.LENGTH_SHORT).show();
                setConnectionState(ConnectionState.DISCONNECTED);
            }
        });

        setupButtons();
        setupBottomNavigation(findViewById(R.id.bottomNavigation));

        // ------------------- Start Connection -------------------
        connectBluetoothWithRetry(ESP_MAC, 3);
    }

    // ------------------------------------------------------------
    // Initialize UI
    // ------------------------------------------------------------
    private void initViews() {
        txtTemperature = findViewById(R.id.txtTemperature);
        txtFeedLevel = findViewById(R.id.txtFeedLevel);
        txtFeedLevelStatus = findViewById(R.id.txtFeedLevelStatus);
        txtFeedAmount = findViewById(R.id.txtFeedAmount);
        txtDeviceName = findViewById(R.id.txtDeviceName);
        txtBtStatus = findViewById(R.id.txtBtStatus);

        // Assign to class-level variables, not local ones!
        feedProgressCard = findViewById(R.id.feedProgressCard);
        progressFeed = findViewById(R.id.progressFeed);

        progressFeed.setMax(FEED_MAX); // safe default
        progressFeed.setProgress(0);

        btnFeedNow = findViewById(R.id.btnFeedNow);
        btnIncrease = findViewById(R.id.btnIncrease);
        btnDecrease = findViewById(R.id.btnDecrease);
    }


    // ------------------------------------------------------------
    // Buttons
    // ------------------------------------------------------------
    private void setupButtons() {
        btnIncrease.setOnClickListener(v -> {
            if (feedAmount < FEED_MAX) feedAmount++;
            else Toast.makeText(this, "Max " + FEED_MAX + " kg", Toast.LENGTH_SHORT).show();
            txtFeedAmount.setText(feedAmount + " kg");
        });

        btnDecrease.setOnClickListener(v -> {
            if (feedAmount > FEED_MIN) feedAmount--;
            else Toast.makeText(this, "Min " + FEED_MIN + " kg", Toast.LENGTH_SHORT).show();
            txtFeedAmount.setText(feedAmount + " kg");
        });

        btnFeedNow.setOnClickListener(v -> {
            if (connectionState == ConnectionState.CONNECTED) {
                String cmd = "FEED_NOW:" + feedAmount + "\n"; // send desired feed amount
                Log.d("BT_SEND", "Sending: " + cmd);
                btSerial.send(cmd.getBytes());

                // Show progress card and reset
                feedProgressCard.setVisibility(View.VISIBLE);
                progressFeed.setMax(feedAmount);
                progressFeed.setProgress(0);

                Toast.makeText(this, "Feeding now…", Toast.LENGTH_SHORT).show();

                // Save to Firestore feed logs
                Map<String, Object> feedLog = new HashMap<>();
                feedLog.put("feedAmount", feedAmount);
                feedLog.put("timestamp", new Date());
                feedLog.put("status", "Started");

                db.collection("FeedFlow")
                        .document("Device001")
                        .collection("FeedLogs")
                        .add(feedLog)
                        .addOnSuccessListener(documentReference ->
                                Log.d("FIRESTORE", "Feed log saved with ID: " + documentReference.getId()))
                        .addOnFailureListener(e ->
                                Log.e("FIRESTORE", "Error saving feed log", e));

                // Optional: Simulated progress (replace with actual feed status from Bluetooth)
                new Thread(() -> {
                    for (int i = 1; i <= feedAmount; i++) {
                        int progress = i;
                        runOnUiThread(() -> progressFeed.setProgress(progress));
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    // Hide progress card when done
                    runOnUiThread(() -> feedProgressCard.setVisibility(View.GONE));

                    // Update feed log status to 'Done' (optional)
                    Map<String, Object> updateStatus = new HashMap<>();
                    updateStatus.put("status", "Done");
                    updateStatus.put("completedAt", new Date());

                    db.collection("FeedFlow")
                            .document("Device001")
                            .collection("FeedLogs")
                            .add(updateStatus)
                            .addOnSuccessListener(doc -> Log.d("FIRESTORE", "Feed log updated to Done"))
                            .addOnFailureListener(e -> Log.e("FIRESTORE", "Error updating feed log", e));
                }).start();

            } else {
                Toast.makeText(this, "ESP32 not connected!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------------------------------------------------
    // Bluetooth Permissions
    // ------------------------------------------------------------
    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    101);
        }
    }

    // ------------------------------------------------------------
    // Connection
    // ------------------------------------------------------------
    private void connectBluetoothWithRetry(String mac, int retries) {
        setConnectionState(ConnectionState.CONNECTING);

        new Thread(() -> {
            for (int i = 0; i < retries; i++) {
                try {
                    Log.d("BT_CONNECT", "Connecting to " + mac + " (Attempt " + (i + 1) + ")");
                    btSerial.connect(mac);

                    runOnUiThread(() -> {
                        Log.d("BT_CONNECT", "Connected!");
                        setConnectionState(ConnectionState.CONNECTED);
                    });

                    startBluetoothListener();
                    return;

                } catch (Exception e) {
                    Log.e("BT_CONNECT", "Attempt " + (i + 1) + " failed: " + e.getMessage());
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }

            runOnUiThread(() -> {
                Log.e("BT_CONNECT", "All retries failed.");
                setConnectionState(ConnectionState.DISCONNECTED);
            });
        }).start();
    }

    // ------------------------------------------------------------
    // Listen for Bytes
    // ------------------------------------------------------------
    private void startBluetoothListener() {
        new Thread(() -> {
            try {
                InputStream in = btSerial.getInputStream();
                byte[] buffer = new byte[256];

                while (connectionState == ConnectionState.CONNECTED) {
                    if (in.available() > 0) {
                        int len = in.read(buffer);
                        String msg = new String(buffer, 0, len).trim();
                        Log.d("BT_STREAM", "Stream Data: " + msg);
                        handleBluetoothData(msg);
                    }
                }
            } catch (Exception e) {
                Log.e("BT_STREAM", "Listener error: " + e.getMessage());
            }
        }).start();
    }

    // ------------------------------------------------------------
    // Update connection state UI
    // ------------------------------------------------------------
    private void setConnectionState(ConnectionState state) {
        connectionState = state;
        txtDeviceName.setText("ESP32: " + state.name());

        switch (state) {
            case CONNECTED:
                txtBtStatus.setText("Connected");
                txtBtStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                break;
            case CONNECTING:
                txtBtStatus.setText("Connecting…");
                txtBtStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                break;
            case DISCONNECTED:
                txtBtStatus.setText("Disconnected");
                txtBtStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                break;
        }
    }

    // ------------------------------------------------------------
    // Process incoming Bluetooth Data
    // ------------------------------------------------------------
    private void handleBluetoothData(String received) {
        if (received == null || received.isEmpty()) return;

        Log.d("BT_PARSE", "Raw packet: " + received);

        String[] p = received.split(",");

        if (p.length < 4) {
            Log.e("BT_PARSE", "Invalid data: " + received);
            return;
        }

        try {
            float temp = Float.parseFloat(p[0]);
            float weight = Float.parseFloat(p[1]);
            int servo = Integer.parseInt(p[2]);
            boolean feeding = p[3].equals("1");

            currentWeight = weight;

            // UI Update
            runOnUiThread(() -> {
                txtTemperature.setText(temp + " °C");
                txtFeedLevel.setText(weight + " kg");
                txtFeedLevelStatus.setText(feeding ? "Feeding…" : "Idle");
            });

            // Firebase Save
            Map<String, Object> data = new HashMap<>();
            data.put("temperature", temp);
            data.put("weight", weight);
            data.put("servo", servo);
            data.put("feedingActive", feeding);
            data.put("timestamp", new Date());

            db.collection("FeedFlow")
                    .document("Device001")
                    .collection("Readings")
                    .add(data)
                    .addOnSuccessListener(r -> Log.d("FIREBASE", "Saved"))
                    .addOnFailureListener(e -> Log.e("FIREBASE", "Error: " + e));

        } catch (Exception e) {
            Log.e("BT_PARSE", "Error: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------
    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        bottomNav.setSelectedItemId(R.id.nav_home);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) return true;

            if (id == R.id.nav_stats) {
                startActivity(new Intent(this, StatsActivity.class));
                finish();
                return true;
            }

            if (id == R.id.nav_notes) {
                startActivity(new Intent(this, NotesActivity.class));
                finish();
                return true;
            }

            if (id == R.id.nav_alerts) {
                startActivity(new Intent(this, AlertsActivity.class));
                finish();
                return true;
            }

            return false;
        });
    }
}
