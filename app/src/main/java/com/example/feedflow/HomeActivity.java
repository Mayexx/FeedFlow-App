package com.example.feedflow;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    private TextView txtTemperature, txtFeedLevel, txtFeedLevelStatus, txtDeviceName, txtBtStatus, txtFeedAmount;
    private Button btnFeedNow, btnIncrease, btnDecrease;
    private final Map<String, String> pendingFeedEvents = new HashMap<>();
    private FirebaseFirestore db;
    private float currentWeight = 0f;

    private BluetoothSerial btSerial;

    private int feedAmount = 5;
    private static final int FEED_MAX = 10;
    private static final int FEED_MIN = 1;

    private enum ConnectionState {DISCONNECTED, CONNECTING, CONNECTED}
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;

    private static final String ESP32_MAC_ADDRESS = "B4:88:08:B4:03:6A"; // your ESP32 mac

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initViews();
        db = FirebaseFirestore.getInstance();
        requestBluetoothPermissions();

        btSerial = new BluetoothSerial(this);

        btSerial.setCallbacks(new BluetoothSerial.DataCallback() {
            @Override
            public void onDataReceived(String data) {
                Log.d("BT_RAW", "Received: '" + data + "'");
                runOnUiThread(() -> handleBluetoothData(data));
            }

            @Override
            public void onConnectionFailed(Exception e) {
                runOnUiThread(() -> {
                    setConnectionState(ConnectionState.DISCONNECTED);
                    Toast.makeText(HomeActivity.this, "Bluetooth Failed", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onDisconnected() {
                runOnUiThread(() -> {
                    setConnectionState(ConnectionState.DISCONNECTED);
                    Toast.makeText(HomeActivity.this, "Bluetooth Disconnected", Toast.LENGTH_SHORT).show();
                });
            }
        });


        setupButtons();
        connectBluetoothWithRetry(ESP32_MAC_ADDRESS, 3);

        // Setup bottom navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        setupBottomNavigation(bottomNav);
    }
    private void initViews() {
        txtTemperature = findViewById(R.id.txtTemperature);
        txtFeedLevel = findViewById(R.id.txtFeedLevel);
        txtFeedAmount = findViewById(R.id.txtFeedAmount);
        txtFeedLevelStatus = findViewById(R.id.txtFeedLevelStatus);
        txtDeviceName = findViewById(R.id.txtDeviceName);
        txtBtStatus = findViewById(R.id.txtBtStatus);
        btnFeedNow = findViewById(R.id.btnFeedNow);
        btnIncrease = findViewById(R.id.btnIncrease);
        btnDecrease = findViewById(R.id.btnDecrease);
        txtDeviceName.setText("ESP32_Test");
    }
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
            if (connectionState != ConnectionState.CONNECTED) {
                Toast.makeText(this, "ESP32 not connected!", Toast.LENGTH_SHORT).show();
                return;
            }

            // ❗ Prevent feeding if THERE IS NO WEIGHT
            if (currentWeight <= 0.0f) {
                Toast.makeText(this, "No weight detected! Check load cell.", Toast.LENGTH_LONG).show();
                return;
            }

            // Prevent invalid feed amount
            if (feedAmount <= 0) {
                Toast.makeText(this, "Enter a valid feed amount!", Toast.LENGTH_SHORT).show();
                return;
            }

            String cmd = "FEED_NOW:" + feedAmount + "\n";

            btnFeedNow.setEnabled(false);

            try {
                btSerial.send(cmd);   // <-- FIXED: now always send a string
                Toast.makeText(this, "Feed request sent: " + feedAmount + " kg", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to send feed command!", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                return;
            }

            // Log feed event in Firestore and store doc ID
            Map<String, Object> feedEvent = new HashMap<>();
            feedEvent.put("amount", feedAmount);
            feedEvent.put("timestamp", System.currentTimeMillis());
            feedEvent.put("deviceName", txtDeviceName.getText().toString());
            feedEvent.put("status", "sent"); // initial status

            db.collection("FeedFlow")
                    .document("Device001")
                    .collection("FeedLogs")
                    .add(feedEvent)
                    .addOnSuccessListener(docRef -> {
                        Log.d("FEED_EVENT", "Feed event logged!");
                        // Keep track of this pending event
                        pendingFeedEvents.put("FEED_NOW", docRef.getId());
                    })
                    .addOnFailureListener(e -> Log.e("FEED_EVENT", "Failed to log feed event", e));
        });

    }
    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    101);
        }
    }
    private void connectBluetoothWithRetry(String macAddress, int retries) {
        setConnectionState(ConnectionState.CONNECTING);

        new Thread(() -> {
            for (int i = 0; i < retries; i++) {
                try {
                    btSerial.connect(macAddress);
                    runOnUiThread(() -> setConnectionState(ConnectionState.CONNECTED));
                    return;
                } catch (Exception e) {
                    Log.e("BT-CONNECT", "Attempt " + (i + 1) + " failed: " + e.getMessage());
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
            runOnUiThread(() -> setConnectionState(ConnectionState.DISCONNECTED));
        }).start();
    }

    private void setConnectionState(ConnectionState state) {
        connectionState = state;

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
            default:
                txtBtStatus.setText("Disconnected");
                txtBtStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                break;
        }
    }

    private void handleBluetoothData(String received) {
        if (received.isEmpty()) return;

        // Handle status lines like FEEDING_STARTED, FEEDING_DONE
        if (!received.contains(",")) {
            Toast.makeText(this, received, Toast.LENGTH_SHORT).show();
            txtFeedLevelStatus.setText(received);

            // Re-enable feed button when feeding finishes
            if (received.equals("FEEDING_DONE") || received.equals("FEED_TIMEOUT")) {
                btnFeedNow.setEnabled(true);
            }

            if (pendingFeedEvents.containsKey("FEED_NOW")) {
                String docId = pendingFeedEvents.get("FEED_NOW");

                // Update Firestore doc status to received status
                db.collection("FeedFlow")
                        .document("Device001")
                        .collection("FeedLogs")
                        .document(docId)
                        .update("status", received)  // e.g., FEEDING_STARTED or FEEDING_DONE
                        .addOnSuccessListener(aVoid -> Log.d("FEED_EVENT", "Feed event updated: " + received))
                        .addOnFailureListener(e -> Log.e("FEED_EVENT", "Failed to update feed event", e));

                // Remove from pending map
                pendingFeedEvents.remove("FEED_NOW");
            }

            return;
        }

        // Normal telemetry: temperature, weight, servo, feedingActive
        String[] parts = received.split(",");
        if (parts.length != 4) return;

        try {
            float temp = Float.parseFloat(parts[0]);
            float weight = Float.parseFloat(parts[1]);
            int servo = Integer.parseInt(parts[2]);
            boolean feedingActive = parts[3].equals("1");

            // Update UI
            txtTemperature.setText(temp + " °C");
            txtFeedLevel.setText(weight + " kg");
            txtFeedLevelStatus.setText(feedingActive ? "Feeding" : "Idle");

            // Update current weight for feed validation
            currentWeight = weight;

            // Firestore telemetry
            Map<String, Object> feedData = new HashMap<>();
            feedData.put("temperature", temp);
            feedData.put("weight", weight);
            feedData.put("servo", servo);
            feedData.put("feedingActive", feedingActive);
            feedData.put("timestamp", new Date());

            db.collection("FeedFlow")
                    .document("Device001")
                    .collection("Readings")
                    .add(feedData)
                    .addOnSuccessListener(aVoid -> Log.d("FIREBASE", "Telemetry uploaded"))
                    .addOnFailureListener(e -> Log.e("FIREBASE", "Upload failed", e));

        } catch (NumberFormatException e) {
            Log.e("BT-DATA", "Parsing error: " + e.getMessage());
        }
    }




    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        bottomNav.setSelectedItemId(R.id.nav_home); // highlight current tab

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                startActivity(new Intent(this, HomeActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_stats) {
                startActivity(new Intent(this, StatsActivity.class));
                overridePendingTransition(0,0);
                return true;
            } else if (id == R.id.nav_notes) {
                startActivity(new Intent(this, NotesActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_alerts) {
                startActivity(new Intent(this, AlertsActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            }
            return false;
        });
    }
}