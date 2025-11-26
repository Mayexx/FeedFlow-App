package com.example.feedflow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class HomeActivity extends AppCompatActivity {
    // Firebase
    private FirebaseFirestore db;

    // UI
    private TextView txtTemperature, txtFeedLevel, txtFeedAmount, txtDeviceName;
    private Button btnFeedNow, btnIncrease, btnDecrease;
    private ProgressBar progressTemperature;
    private TextView txtTempThreshold;
    private TextView waterTemperature, txtWaterTempStatus;
    private TextView txtFeedLevelStatus;

    // Bluetooth
    private BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // State & prefs
    private int feedAmount = 25;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "FeedFlowPrefs";
    private String connectedDeviceName = "Not Connected";
    private List<String> feedingHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        db = FirebaseFirestore.getInstance();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        initViews();
        initBluetooth();
        restoreSavedData();
        setupButtons();
        setupBottomNavigation(bottomNav);
        loadFeedLevelFromFirestore();
    }

    private void initViews() {
        txtTemperature = findViewById(R.id.txtTemperature);
        txtFeedLevel = findViewById(R.id.txtFeedLevel);
        txtFeedAmount = findViewById(R.id.txtFeedAmount);
        txtDeviceName = findViewById(R.id.txtDeviceName);
        btnFeedNow = findViewById(R.id.btnFeedNow);
        btnIncrease = findViewById(R.id.btnIncrease);
        btnDecrease = findViewById(R.id.btnDecrease);
        txtTempThreshold = findViewById(R.id.txtTemperature);
        waterTemperature = findViewById(R.id.waterTemperature);
        txtWaterTempStatus = findViewById(R.id.txtWaterTempStatus);
        progressTemperature = findViewById(R.id.progressTemperature);
        txtFeedLevelStatus = findViewById(R.id.txtFeedLevelStatus);
    }

    private void restoreSavedData() {
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        feedAmount = sharedPreferences.getInt("feedAmount", 25);
        txtFeedAmount.setText(feedAmount + " kg");
        int tempThreshold = sharedPreferences.getInt("tempThreshold", 28);
        progressTemperature.setProgress(tempThreshold);
        txtTempThreshold.setText("Temperature: " + tempThreshold + "°C");
    }

    private void setupButtons() {
        btnIncrease.setOnClickListener(v -> {
            feedAmount += 1;
            txtFeedAmount.setText(feedAmount + " kg");
            sharedPreferences.edit().putInt("feedAmount", feedAmount).apply();
        });
        btnDecrease.setOnClickListener(v -> {
            if (feedAmount > 1) {
                feedAmount -= 1;
                txtFeedAmount.setText(feedAmount + " kg");
                sharedPreferences.edit().putInt("feedAmount", feedAmount).apply();
            }
        });
        btnFeedNow.setOnClickListener(v -> saveFeedLog(feedAmount));
    }

    private void saveFeedLog(int amount) {
        // send command to device to feed
        sendBluetoothCommand("FEED:" + amount);

        String time = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(new Date());
        long now = System.currentTimeMillis();

        // Save last feed in preferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("lastFeedTime", time);
        editor.putInt("lastFeedAmount", amount);
        editor.apply();

        // Update FeedData prefs for stats
        SharedPreferences statsPrefs = getSharedPreferences("FeedData", MODE_PRIVATE);
        SharedPreferences.Editor statsEditor = statsPrefs.edit();

        float todayFeed = statsPrefs.getFloat("todayFeed", 0f);
        float totalFeed = statsPrefs.getFloat("totalFeed", 0f);
        int daysCount = statsPrefs.getInt("daysCount", 0);

        String lastDay = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date(now));
        String savedDay = statsPrefs.getString("lastDay", "");
        if (!lastDay.equals(savedDay)) {
            todayFeed = 0f;
            daysCount++;
            statsEditor.putString("lastDay", lastDay);
            statsEditor.putInt("daysCount", daysCount);
        }

        todayFeed += amount;
        totalFeed += amount;

        statsEditor.putFloat("todayFeed", todayFeed);
        statsEditor.putFloat("totalFeed", totalFeed);
        statsEditor.putLong("lastUpdated", now);
        statsEditor.apply();

        Map<String, Object> feedLog = new HashMap<>();
        feedLog.put("amount", amount);
        feedLog.put("time", time);
        feedLog.put("timestamp", now);
        feedLog.put("todayFeed", todayFeed);
        feedLog.put("totalFeed", totalFeed);

        db.collection("FeedFlow")
                .document("Device001")
                .collection("feedLogs")
                .add(feedLog)
                .addOnSuccessListener(documentReference -> {
                    Log.d("Firestore", "Feed log saved with ID: " + documentReference.getId());
                    Toast.makeText(this, "Fed " + amount + " kg at " + time, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e("Firestore", "Error saving feed log", e);
                    Toast.makeText(this, "Fed " + amount + " kg (offline)", Toast.LENGTH_SHORT).show();
                });

        Toast.makeText(this, "Fed " + amount + " kg at " + time, Toast.LENGTH_SHORT).show();
    }

    private void sendBluetoothCommand(String command) {
        if (btSocket != null && btSocket.isConnected()) {
            try {
                btSocket.getOutputStream().write((command + "\n").getBytes());
                Log.d("BT_SEND", "Sent: " + command);
            } catch (IOException e) {
                Log.e("BT_SEND", "Failed to send command", e);
                Toast.makeText(this, "Failed to send feed command", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        bottomNav.setSelectedItemId(R.id.nav_home); // highlight Home tab

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_stats) {
                startActivity(new Intent(HomeActivity.this, StatsActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_notes) {
                startActivity(new Intent(HomeActivity.this, NotesActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            } else if (id == R.id.nav_alerts) {
                startActivity(new Intent(HomeActivity.this, AlertsActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            }
            return false;
        });
    }

    private void initBluetooth() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
            return;
        }

        if (!btAdapter.isEnabled()) {
            Toast.makeText(this, "Enable Bluetooth to get sensor data.", Toast.LENGTH_LONG).show();
            txtDeviceName.setText("Not Connected");
            return;
        }

        // Check runtime permissions (Android 12+)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 100);
            return;
        }

        // Load the device name from Firestore (expected doc: settings/device -> deviceName)
        db.collection("settings").document("device")
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String targetDeviceName = doc.getString("deviceName");
                        startBluetoothScan(targetDeviceName);
                    } else {
                        Toast.makeText(this, "No device info found in Firestore", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to get device info", Toast.LENGTH_SHORT).show());
    }

    private void startBluetoothScan(String targetDeviceName) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (btAdapter.isDiscovering()) {
            btAdapter.cancelDiscovery();
        }
        btAdapter.startDiscovery();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }

                    if (device != null && device.getName() != null) {
                        String deviceName = device.getName();
                        String deviceAddress = device.getAddress();

                        Log.d("BT_SCAN", "Found: " + deviceName + " [" + deviceAddress + "]");

                        // Only connect if device matches Firestore
                        if (deviceName.equals(targetDeviceName)) {
                            btAdapter.cancelDiscovery();
                            connectToDevice(device);
                            txtDeviceName.setText("Connecting: " + deviceName);
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
    }

    @SuppressLint("SetTextI18n")
    private void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                btSocket = device.createRfcommSocketToServiceRecord(BT_UUID);
                btSocket.connect();
                inputStream = btSocket.getInputStream();
                outputStream = btSocket.getOutputStream();
                connectedDeviceName = device.getName();

                runOnUiThread(() -> {
                    txtDeviceName.setText(connectedDeviceName + "\nLast Updated: Waiting for data...");
                    Toast.makeText(this, "Connected to " + connectedDeviceName, Toast.LENGTH_SHORT).show();
                });

                listenForData();

            } catch (IOException e) {
                runOnUiThread(() -> {
                    txtDeviceName.setText("Not Connected");
                    Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void listenForData() {
        // We'll read the stream line-by-line. Ensure ESP32 ends lines with '\n' or '\r\n'.
        Handler handler = new Handler(getMainLooper());

        new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String data;

                while ((data = reader.readLine()) != null) {
                    data = data.trim();
                    if (data.isEmpty()) continue;

                    // Expecting format: "27.5:85" (temp:feed)
                    String fTemp = "";
                    String fFeed = "";

                    if (data.contains(":")) {
                        String[] parts = data.split(":");
                        if (parts.length >= 2) {
                            fTemp = parts[0].trim();
                            fFeed = parts[1].trim();
                        } else {
                            Log.w("BT_DATA", "Malformed line (has colon but not two parts): " + data);
                            continue;
                        }
                    } else {
                        // If no colon, ignore line OR treat as temp only (not expected for your chosen format)
                        // For robustness, handle as temp-only and set feed to 0
                        try {
                            Double.parseDouble(data); // validate
                            fTemp = data;
                            fFeed = "0";
                        } catch (NumberFormatException ex) {
                            Log.w("BT_DATA", "Unrecognized data format: " + data);
                            continue;
                        }
                    }

                    final String finalTemp = fTemp;
                    final String finalFeed = fFeed;
                    final String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

                    // Update UI and Firestore on main thread
                    handler.post(() -> {
                        // Update UI (water card)
                        try {
                            double currentTemp = Double.parseDouble(finalTemp);
                            waterTemperature.setText(String.format(Locale.getDefault(), "%.1f °C", currentTemp));
                            progressTemperature.setProgress((int) Math.round(currentTemp));
                            txtWaterTempStatus(currentTemp);

                        } catch (NumberFormatException e) {
                            Log.e("BT_DATA", "Invalid temperature format: " + finalTemp);
                        }

                        // Update feed level UI
                        txtFeedLevel.setText(finalFeed + "%");

                        // Update device info display
                        txtDeviceName.setText(connectedDeviceName + "\nLast Updated: " + time);

                        // Save to SharedPreferences
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("latestTemperature", finalTemp);
                        editor.putString("latestFeedLevel", finalFeed);
                        editor.putString("lastUpdatedTime", time);
                        editor.apply();

                        // Upload to Firestore with proper numeric types so Stats page reads numbers
                        Map<String, Object> sensorData = new HashMap<>();
                        try {
                            double tempNumeric = Double.parseDouble(finalTemp);
                            int feedNumeric = Integer.parseInt(finalFeed);

                            sensorData.put("temperature", tempNumeric);
                            sensorData.put("feedLevel", feedNumeric);
                            sensorData.put("timestamp", System.currentTimeMillis());

                            db.collection("FeedFlow")
                                    .document("Device001")
                                    .collection("readings")
                                    .add(sensorData)
                                    .addOnSuccessListener(documentReference ->
                                            Log.d("FIRESTORE", "Data added with ID: " + documentReference.getId()))
                                    .addOnFailureListener(e ->
                                            Log.w("FIRESTORE", "Error adding document", e));

                            // Also update top-level doc for quick reads (used in loadFeedLevelFromFirestore)
                            Map<String, Object> topDoc = new HashMap<>();
                            topDoc.put("temperature", tempNumeric);
                            topDoc.put("feedLevel", feedNumeric);
                            topDoc.put("timestamp", System.currentTimeMillis());

                            db.collection("FeedFlow")
                                    .document("Device001")
                                    .set(topDoc)
                                    .addOnSuccessListener(aVoid -> Log.d("FIRESTORE", "Top-level doc updated"))
                                    .addOnFailureListener(e -> Log.w("FIRESTORE", "Top-level update failed", e));

                        } catch (NumberFormatException nfe) {
                            Log.e("BT_DATA", "Failed to parse numeric values for Firestore: temp=" + finalTemp + " feed=" + finalFeed);
                        }
                    });
                }
            } catch (IOException e) {
                Log.e("BT", "Read failed", e);
            }
        }).start();
    }

    private void loadFeedLevelFromFirestore() {
        db.collection("FeedFlow")
                .document("Device001")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e("FIRESTORE", "Error: ", error);
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        // Temperature
                        Double temp = snapshot.getDouble("temperature");
                        if (temp != null) {
                            txtTemperature.setText(String.format(Locale.getDefault(), "%.1f°C", temp));
                            waterTemperature.setText(String.format(Locale.getDefault(), "%.1f °C", temp));
                            progressTemperature.setProgress(temp.intValue());
                            txtWaterTempStatus(temp);
                        }
                        // Feed Level (weight)
                        Double weight = snapshot.getDouble("weight");
                        if (weight != null) {
                            txtFeedLevel.setText(String.format(Locale.getDefault(), "%.2f kg", weight));
                            updateFeedLevelStatus(weight);
                        } else {
                            // If feedLevel numeric exists (percentage), show that
                            Long feedPercent = snapshot.getLong("feedLevel");
                            if (feedPercent != null) {
                                txtFeedLevel.setText(feedPercent + "%");
                                // Optionally also update feed level status by mapping percent -> kg or thresholds
                            }
                        }
                    }
                });
    }

    private void txtWaterTempStatus(double temp) {
        if (txtWaterTempStatus == null) return;
        if (temp >= 26 && temp <= 30) {
            txtWaterTempStatus.setText("Optimal");
            txtWaterTempStatus.setTextColor(Color.parseColor("#28A745")); // green
        } else if (temp >= 31 && temp <= 33) {
            txtWaterTempStatus.setText("Above Optimal");
            txtWaterTempStatus.setTextColor(Color.parseColor("#FFA500")); // orange
        } else if (temp > 33) {
            txtWaterTempStatus.setText("Critical – Too Hot");
            txtWaterTempStatus.setTextColor(Color.parseColor("#DC3545")); // red
        } else if (temp < 26) {
            txtWaterTempStatus.setText("Too Cold");
            txtWaterTempStatus.setTextColor(Color.parseColor("#007BFF")); // blue
        }
    }

    private void updateFeedLevelStatus(double feedLevel) {
        if (txtFeedLevelStatus == null) return;

        if (feedLevel >= 5) {
            txtFeedLevelStatus.setText("Sufficient");
            txtFeedLevelStatus.setTextColor(Color.parseColor("#28A745")); // green
        } else if (feedLevel >= 2) {
            txtFeedLevelStatus.setText("Refill Soon");
            txtFeedLevelStatus.setTextColor(Color.parseColor("#FFA500")); // orange
        } else {
            txtFeedLevelStatus.setText("Critical – Refill Now");
            txtFeedLevelStatus.setTextColor(Color.parseColor("#DC3545")); // red
        }
    }
}
