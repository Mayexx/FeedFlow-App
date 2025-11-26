package com.example.feedflow;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HomeActivity extends AppCompatActivity {

    // Firebase
    private FirebaseFirestore db;

    // UI Elements
    private TextView txtTemperature, txtFeedLevel, txtFeedAmount;
    private TextView waterTemperature, txtWaterTempStatus, txtFeedLevelStatus, txtDeviceName;
    private ProgressBar progressTemperature;
    private Button btnFeedNow, btnIncrease, btnDecrease;

    // App Data
    private int feedAmount = 25;
    private static final String PREF_NAME = "FeedFlowPrefs";
    private SharedPreferences sharedPreferences;

    // Bluetooth
    private BluetoothSerial btSerial;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        db = FirebaseFirestore.getInstance();

        initViews();
        restoreSavedData();
        setupButtons();
        setupBottomNavigation(findViewById(R.id.bottomNavigation));

        // ----- BLUETOOTH -----
        btSerial = new BluetoothSerial(this);

        // Set callback to receive data from ESP32
        btSerial.setCallbacks(data -> {
            String msg = new String(data).trim();
            runOnUiThread(() -> {
                if (msg.contains(":")) {
                    String[] parts = msg.split(":");
                    String tempStr = parts[0];
                    String feedStr = parts[1];
                    String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                    updateUI(tempStr, feedStr, time);
                }
            });
        });

        // Connect to your ESP32 MAC
        btSerial.connect("B4:88:08:B4:03:6A"); // replace with your device MAC
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
        txtDeviceName = findViewById(R.id.txtDeviceName);
    }

    private void restoreSavedData() {
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        feedAmount = sharedPreferences.getInt("feedAmount", 25);
        txtFeedAmount.setText(feedAmount + " kg");
        int tempThreshold = sharedPreferences.getInt("tempThreshold", 28);
        progressTemperature.setProgress(tempThreshold);
        txtTemperature.setText("Temperature: " + tempThreshold + "°C");
        txtDeviceName.setText("Not Connected");
    }

    private void setupButtons() {
        btnIncrease.setOnClickListener(v -> {
            if (feedAmount < 10) {
                feedAmount++;
                txtFeedAmount.setText(feedAmount + " kg");
                sharedPreferences.edit().putInt("feedAmount", feedAmount).apply();
            } else {
                Toast.makeText(this, "Maximum is 10 kg", Toast.LENGTH_SHORT).show();
            }
        });

        btnDecrease.setOnClickListener(v -> {
            if (feedAmount > 1) {
                feedAmount--;
                txtFeedAmount.setText(feedAmount + " kg");
                sharedPreferences.edit().putInt("feedAmount", feedAmount).apply();
            } else {
                Toast.makeText(this, "Cannot dispense less than 1 kg", Toast.LENGTH_SHORT).show();
            }
        });

        btnFeedNow.setOnClickListener(v -> {
            if (btSerial != null) {
                btSerial.send("FEED:" + feedAmount + "\n");
                Toast.makeText(this, "Sent feed command: " + feedAmount + " kg", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Bluetooth not connected", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI(String tempStr, String feedStr, String time) {
        try {
            double temp = Double.parseDouble(tempStr);
            double feedLevel = Double.parseDouble(feedStr);

            waterTemperature.setText(String.format("%.1f °C", temp));
            progressTemperature.setProgress((int) temp);
            txtTemperature.setText(temp + " °C");
            txtFeedLevel.setText(feedStr + "%");
            txtDeviceName.setText("Connected | Last: " + time);

            // Temperature status
            if (temp >= 26 && temp <= 30) {
                txtWaterTempStatus.setText("Optimal");
                txtWaterTempStatus.setTextColor(Color.GREEN);
            } else if (temp > 30) {
                txtWaterTempStatus.setText("Too Hot");
                txtWaterTempStatus.setTextColor(Color.RED);
            } else {
                txtWaterTempStatus.setText("Too Cold");
                txtWaterTempStatus.setTextColor(Color.BLUE);
            }

            // Feed status
            if (feedLevel >= 5) {
                txtFeedLevelStatus.setText("Sufficient");
                txtFeedLevelStatus.setTextColor(Color.GREEN);
            } else if (feedLevel >= 2) {
                txtFeedLevelStatus.setText("Refill Soon");
                txtFeedLevelStatus.setTextColor(Color.YELLOW);
            } else {
                txtFeedLevelStatus.setText("Refill Now");
                txtFeedLevelStatus.setTextColor(Color.RED);
            }

            // Save latest data locally
            sharedPreferences.edit()
                    .putString("latestTemperature", tempStr)
                    .putString("latestFeedLevel", feedStr)
                    .putString("lastUpdatedTime", time)
                    .apply();

            // Save to Firestore
            Map<String, Object> sensorData = new HashMap<>();
            sensorData.put("temperature", temp);
            sensorData.put("feedLevel", feedLevel);
            sensorData.put("timestamp", System.currentTimeMillis());

            db.collection("FeedFlow")
                    .document("Device001")
                    .collection("readings")
                    .add(sensorData)
                    .addOnSuccessListener(docRef -> Log.d("FIRESTORE", "Data added"))
                    .addOnFailureListener(e -> Log.e("FIRESTORE", "Error adding data", e));

        } catch (NumberFormatException e) {
            Log.e("BT_DATA", "Invalid data: " + tempStr + ":" + feedStr, e);
        }
    }

    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            else if (id == R.id.nav_stats) startActivity(new android.content.Intent(HomeActivity.this, StatsActivity.class));
            else if (id == R.id.nav_notes) startActivity(new android.content.Intent(HomeActivity.this, NotesActivity.class));
            else if (id == R.id.nav_alerts) startActivity(new android.content.Intent(HomeActivity.this, AlertsActivity.class));
            overridePendingTransition(0, 0);
            finish();
            return true;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (btSerial != null) btSerial.disconnect();
    }
}
