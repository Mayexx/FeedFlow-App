package com.example.feedflow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public class HomeActivity extends AppCompatActivity {

    // Firebase
    private FirebaseFirestore db;

    // UI Elements
    private TextView txtTemperature, txtFeedLevel, txtFeedAmount;
    private TextView waterTemperature, txtWaterTempStatus, txtFeedLevelStatus, txtDeviceName;
    private ProgressBar progressTemperature;
    private Button btnFeedNow, btnIncrease, btnDecrease;

    // Bluetooth
    private BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private String connectedDeviceName = "Not Connected";

    // App Data
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "FeedFlowPrefs";
    private int feedAmount = 25;
    private int tempThreshold = 28;
    private List<String> feedingHistory = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        db = FirebaseFirestore.getInstance();

        initViews();
        restoreSavedData();
        initBluetooth();
        setupButtons();
        setupBottomNavigation(findViewById(R.id.bottomNavigation));
        loadFeedLevelFromFirestore();
    }

    private void initViews() {
        txtTemperature = findViewById(R.id.txtTemperature);
        txtFeedLevel = findViewById(R.id.txtFeedLevel);
        txtFeedAmount = findViewById(R.id.txtFeedAmount);
        waterTemperature = findViewById(R.id.waterTemperature);
        txtWaterTempStatus = findViewById(R.id.txtWaterTempStatus);
        txtFeedLevelStatus = findViewById(R.id.txtFeedLevelStatus);
        progressTemperature = findViewById(R.id.progressTemperature);
        btnFeedNow = findViewById(R.id.btnFeedNow);
        btnIncrease = findViewById(R.id.btnIncrease);
        btnDecrease = findViewById(R.id.btnDecrease);
        txtDeviceName = findViewById(R.id.txtDeviceName); // new TextView for connected device
    }

    private void restoreSavedData() {
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        feedAmount = sharedPreferences.getInt("feedAmount", 25);
        txtFeedAmount.setText(feedAmount + " kg");
        tempThreshold = sharedPreferences.getInt("tempThreshold", 28);
        progressTemperature.setProgress(tempThreshold);
        txtTemperature.setText("Temperature: " + tempThreshold + "°C");
        txtDeviceName.setText(connectedDeviceName);
    }

    // -------------------- FEEDING --------------------
    private void setupButtons() {
        btnIncrease.setOnClickListener(v -> {
            feedAmount++;
            txtFeedAmount.setText(feedAmount + " kg");
            sharedPreferences.edit().putInt("feedAmount", feedAmount).apply();
        });

        btnDecrease.setOnClickListener(v -> {
            if (feedAmount > 1) {
                feedAmount--;
                txtFeedAmount.setText(feedAmount + " kg");
                sharedPreferences.edit().putInt("feedAmount", feedAmount).apply();
            }
        });

        btnFeedNow.setOnClickListener(v -> saveFeedLog(feedAmount));
    }

    private void saveFeedLog(int amount) {
        sendBluetoothCommand("FEED:" + amount);

        String time = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(new Date());
        long now = System.currentTimeMillis();

        sharedPreferences.edit()
                .putString("lastFeedTime", time)
                .putInt("lastFeedAmount", amount)
                .apply();

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
                .addOnSuccessListener(docRef -> Log.d("FIRESTORE", "Feed log saved"))
                .addOnFailureListener(e -> Log.e("FIRESTORE", "Error saving feed log", e));

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

    // -------------------- BLUETOOTH --------------------
    private void initBluetooth() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            return;
        }

        if (!btAdapter.isEnabled()) {
            Toast.makeText(this, "Enable Bluetooth to get sensor data", Toast.LENGTH_LONG).show();
            txtDeviceName.setText("Not Connected");
            return;
        }

        String macAddress = getIntent().getStringExtra("DEVICE_MAC");
        if (macAddress == null || macAddress.isEmpty()) {
            Toast.makeText(this, "No device found", Toast.LENGTH_LONG).show();
            txtDeviceName.setText("No Device Selected");
            return;
        }

        txtDeviceName.setText("Connecting: " + connectedDeviceName);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 200);
            return;
        }

        BluetoothDevice device = btAdapter.getRemoteDevice(macAddress);
        connectToDevice(device);
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
                    txtDeviceName.setText("Connected to: " + connectedDeviceName + "\nWaiting for data...");
                    Toast.makeText(this, "Connected to " + connectedDeviceName, Toast.LENGTH_SHORT).show();
                });

                listenForData();

            } catch (IOException e) {
                runOnUiThread(() -> {
                    txtDeviceName.setText("Not Connected");
                    Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show();
                });
                Log.e("BT_CONNECT", "Connection failed", e);
            }
        }).start();
    }

    private void listenForData() {
        Handler handler = new Handler(getMainLooper());
        byte[] buffer = new byte[1024];

        new Thread(() -> {
            while (true) {
                try {
                    if (inputStream != null && inputStream.available() > 0) {
                        int bytes = inputStream.read(buffer);
                        String data = new String(buffer, 0, bytes).trim();

                        if (data.contains(":")) {
                            String[] parts = data.split(":");
                            String tempStr = parts[0];
                            String feedStr = parts[1];
                            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

                            handler.post(() -> updateUI(tempStr, feedStr, time));
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }).start();
    }

    private void updateUI(String tempStr, String feedStr, String time) {
        double temp = 0;
        double feedLevel = 0;

        try {
            temp = Double.parseDouble(tempStr);
            feedLevel = Double.parseDouble(feedStr);
        } catch (NumberFormatException e) {
            Log.e("BT_DATA", "Invalid data: " + tempStr + ", " + feedStr);
            return;
        }

        // Update UI
        waterTemperature.setText(String.format("%.1f °C", temp));
        progressTemperature.setProgress((int) temp);
        txtFeedLevel.setText(feedLevel + "%");

        updateTempStatus(temp);
        updateFeedLevelStatus(feedLevel);

        txtDeviceName.setText("Connected to: " + connectedDeviceName + "\nLast Updated: " + time);

        // Save locally
        sharedPreferences.edit()
                .putString("latestTemperature", tempStr)
                .putString("latestFeedLevel", feedStr)
                .putString("lastUpdatedTime", time)
                .apply();

        // Prepare data for Firestore
        Map<String, Object> sensorData = new HashMap<>();
        sensorData.put("temperature", temp);
        sensorData.put("feedLevel", feedLevel);
        sensorData.put("timestamp", System.currentTimeMillis());

        // 1️⃣ Add to 'readings' subcollection (history)
        db.collection("FeedFlow")
                .document("Device001")
                .collection("readings")
                .add(sensorData)
                .addOnSuccessListener(docRef -> Log.d("FIRESTORE", "Bluetooth data added to readings"))
                .addOnFailureListener(e -> Log.e("FIRESTORE", "Failed to add data to readings", e));

        // 2️⃣ Update main document with latest values (real-time)
        Map<String, Object> latestData = new HashMap<>();
        latestData.put("temperature", temp);
        latestData.put("feedLevel", feedLevel);
        latestData.put("lastUpdated", System.currentTimeMillis());

        db.collection("FeedFlow")
                .document("Device001")
                .set(latestData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d("FIRESTORE", "Device001 latest values updated"))
                .addOnFailureListener(e -> Log.e("FIRESTORE", "Failed to update Device001", e));
    }

    private void updateTempStatus(double temp) {
        if (txtWaterTempStatus == null) return;
        if (temp >= 26 && temp <= 30) {
            txtWaterTempStatus.setText("Optimal");
            txtWaterTempStatus.setTextColor(Color.parseColor("#28A745"));
        } else if (temp >= 31 && temp <= 33) {
            txtWaterTempStatus.setText("Above Optimal");
            txtWaterTempStatus.setTextColor(Color.parseColor("#FFA500"));
        } else if (temp > 33) {
            txtWaterTempStatus.setText("Critical – Too Hot");
            txtWaterTempStatus.setTextColor(Color.parseColor("#DC3545"));
        } else {
            txtWaterTempStatus.setText("Too Cold");
            txtWaterTempStatus.setTextColor(Color.parseColor("#007BFF"));
        }
    }

    private void updateFeedLevelStatus(double feedLevel) {
        if (txtFeedLevelStatus == null) return;
        if (feedLevel >= 5) {
            txtFeedLevelStatus.setText("Sufficient");
            txtFeedLevelStatus.setTextColor(Color.parseColor("#28A745"));
        } else if (feedLevel >= 2) {
            txtFeedLevelStatus.setText("Refill Soon");
            txtFeedLevelStatus.setTextColor(Color.parseColor("#FFA500"));
        } else {
            txtFeedLevelStatus.setText("Critical – Refill Now");
            txtFeedLevelStatus.setTextColor(Color.parseColor("#DC3545"));
        }
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
                        Double temp = snapshot.getDouble("temperature");
                        if (temp != null) {
                            txtTemperature.setText(temp + "°C");
                            waterTemperature.setText(String.format("%.1f °C", temp));
                            progressTemperature.setProgress(temp.intValue());
                            updateTempStatus(temp);
                        }

                        Double weight = snapshot.getDouble("weight");
                        if (weight != null) {
                            txtFeedLevel.setText(String.format(Locale.getDefault(), "%.2f kg", weight));
                            updateFeedLevelStatus(weight);
                        }
                    }
                });
    }

    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        bottomNav.setSelectedItemId(R.id.nav_home);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) return true;
            else if (id == R.id.nav_stats) startActivity(new Intent(HomeActivity.this, StatsActivity.class));
            else if (id == R.id.nav_notes) startActivity(new Intent(HomeActivity.this, NotesActivity.class));
            else if (id == R.id.nav_alerts) startActivity(new Intent(HomeActivity.this, AlertsActivity.class));

            overridePendingTransition(0, 0);
            finish();
            return true;
        });
    }
}
