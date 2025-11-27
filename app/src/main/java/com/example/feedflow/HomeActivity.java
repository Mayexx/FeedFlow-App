package com.example.feedflow;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
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
    private ProgressBar progressTemperature, progressFeed;
    private Button btnFeedNow, btnIncrease, btnDecrease;

    private FirebaseFirestore db;

    private BluetoothSerial btSerial;

    private int feedAmount = 5;
    private static final int FEED_MAX = 10;
    private static final int FEED_MIN = 1;
    private float currentWeight = 0.0f;

    private enum ConnectionState {DISCONNECTED, CONNECTING, CONNECTED}
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initViews();
        db = FirebaseFirestore.getInstance();
        requestBluetoothPermissions();
        btSerial = new BluetoothSerial(this);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        setupBottomNavigation(bottomNav);
        startBluetoothListener();

        // Initialize BluetoothSerial
        btSerial = new BluetoothSerial(this);
        btSerial.setCallbacks(new BluetoothSerial.DataCallback() {
            @Override
            public void onDataReceived(byte[] rawData) {
                String data = new String(rawData).trim();
                Log.d("BT_RAW", "Received: '" + data + "'");
                runOnUiThread(() -> handleBluetoothData(data));
            }
            @Override
            public void onConnectionFailed(Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(HomeActivity.this, "Bluetooth Failed", Toast.LENGTH_SHORT).show()
                );
            }
            @Override
            public void onDisconnected() {
                runOnUiThread(() ->
                        Toast.makeText(HomeActivity.this, "Bluetooth Disconnected", Toast.LENGTH_SHORT).show()
                );
            }
        });
        setupButtons();
        // Replace with your ESP32 MAC address
        connectBluetoothWithRetry("B4:88:08:B4:03:6A", 3);

        btSerial.setCallbacks(new BluetoothSerial.DataCallback() {
            @Override
            public void onDataReceived(byte[] rawData) {
                String data = new String(rawData).trim();
                runOnUiThread(() -> handleBluetoothData(data));
            }
            @Override
            public void onConnectionFailed(Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(HomeActivity.this, "Bluetooth Failed", Toast.LENGTH_SHORT).show()
                );
            }
            @Override
            public void onDisconnected() {
                runOnUiThread(() ->
                        Toast.makeText(HomeActivity.this, "Bluetooth Disconnected", Toast.LENGTH_SHORT).show()
                );
            }
        });
        btSerial.connect("B4:8A:0A:B4:03:6A");
    }
    private void initViews() {
        txtTemperature = findViewById(R.id.txtTemperature);
        txtFeedLevel = findViewById(R.id.txtFeedLevel);
        txtFeedLevelStatus = findViewById(R.id.txtFeedLevelStatus);
        txtFeedAmount = findViewById(R.id.txtFeedAmount);
        txtDeviceName = findViewById(R.id.txtDeviceName);
        txtBtStatus = findViewById(R.id.txtBtStatus);
        progressTemperature = findViewById(R.id.progressTemperature);
        progressFeed = findViewById(R.id.progressFeed);
        progressTemperature.setMax(50);
        btnFeedNow = findViewById(R.id.btnFeedNow);
        btnIncrease = findViewById(R.id.btnIncrease);
        btnDecrease = findViewById(R.id.btnDecrease);
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
            if (connectionState == ConnectionState.CONNECTED) {
                // send current weight measured from load cell
                String cmd = "FEED_NOW:" + currentWeight + "\n";
                btSerial.send(cmd.getBytes());
                Toast.makeText(this,
                        "Feed request sent: " + currentWeight + " kg",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "ESP32 not connected!", Toast.LENGTH_SHORT).show();
            }
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

    private void startBluetoothListener() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;
            while (connectionState == ConnectionState.CONNECTED) {
                try {
                    InputStream inStream = btSerial.getInputStream(); // make sure your btSerial exposes InputStream
                    if (inStream.available() > 0) {
                        bytes = inStream.read(buffer);
                        String msg = new String(buffer, 0, bytes).trim();

                        // Parse CSV: TEMP,WEIGHT,SERVO,FEEDING
                        String[] parts = msg.split(",");
                        if (parts.length >= 2) {
                            try {
                                float weight = Float.parseFloat(parts[1]);
                                currentWeight = weight;  // update global variable
                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    break;
                }
            }
        }).start();
    }


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
            default:
                txtBtStatus.setText("Disconnected");
                txtBtStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                break;
        }
    }

    private void handleBluetoothData(String received) {
        if (received == null || received.isEmpty()) return;

        // Format: temperature,weight,servo,feedingActive
        String[] parts = received.split(",");
        if (parts.length < 4) {
            Log.e("BT-DATA", "Invalid packet: " + received);
            return;
        }

        try {
            float temp = Float.parseFloat(parts[0]);
            float weight = Float.parseFloat(parts[1]);
            int servo = Integer.parseInt(parts[2]);
            boolean feedingActive = parts[3].trim().equals("1");

            // ----------- UPDATE UI -----------
            runOnUiThread(() -> {
                txtTemperature.setText(temp + " °C");
                txtFeedLevel.setText(weight + " kg");
                txtFeedLevelStatus.setText(feedingActive ? "Feeding" : "Idle");
            });

            // ----------- SAVE EVERY ENTRY TO FIREBASE -----------
            Map<String, Object> feedData = new HashMap<>();
            feedData.put("temperature", temp);
            feedData.put("weight", weight);
            feedData.put("servo", servo);
            feedData.put("feedingActive", feedingActive);
            feedData.put("timestamp", new Date());

            db.collection("FeedFlow")
                    .document("Device001")
                    .collection("Readings")  // ← this keeps all readings
                    .add(feedData)            // ← auto-ID, saves every entry
                    .addOnSuccessListener(ref -> Log.d("FIREBASE", "Reading saved"))
                    .addOnFailureListener(e -> Log.e("FIREBASE", "Upload failed", e));

        } catch (Exception e) {
            Log.e("BT-DATA", "Parse error: " + e.getMessage() + " | Raw: " + received);
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
