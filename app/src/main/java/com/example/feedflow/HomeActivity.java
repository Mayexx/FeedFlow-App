package com.example.feedflow;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public class HomeActivity extends AppCompatActivity {

    private TextView txtTemperature, txtFeedLevel, txtFeedAmount;
    private Button btnFeedNow, btnIncrease, btnDecrease;
    private Switch autoSwitch;

    private BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;
    private InputStream inputStream;
    private final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private int feedAmount = 25;

    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "FeedFlowPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);

        initViews();
        initBluetooth();
        restoreSavedData();
        setupButtons();
        setupBottomNavigation(bottomNav);
    }

    private void initViews() {
        txtTemperature = findViewById(R.id.txtTemperature);
        txtFeedLevel = findViewById(R.id.txtFeedLevel);
        txtFeedAmount = findViewById(R.id.txtFeedAmount);
        btnFeedNow = findViewById(R.id.btnFeedNow);
        btnIncrease = findViewById(R.id.btnIncrease);
        btnDecrease = findViewById(R.id.btnDecrease);
        autoSwitch = findViewById(R.id.autoSwitch);
    }

    private void restoreSavedData() {
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        feedAmount = sharedPreferences.getInt("feedAmount", 25);
        txtFeedAmount.setText(feedAmount + " kg");
    }

    private void saveFeedLog(int amount) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        editor.putString("lastFeedTime", time);
        editor.putInt("lastFeedAmount", amount);
        editor.apply();
        Toast.makeText(this, "Fed " + amount + " kg at " + time, Toast.LENGTH_SHORT).show();
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

        btnFeedNow.setOnClickListener(v -> {
            saveFeedLog(feedAmount);
        });

        autoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Toast.makeText(this, isChecked ? "Auto Feeding ON" : "Auto Feeding OFF", Toast.LENGTH_SHORT).show();
            sharedPreferences.edit().putBoolean("autoMode", isChecked).apply();
        });
    }

    private void setupBottomNavigation(BottomNavigationView bottomNav) {
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_home); // highlight Home tab

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                return true; // already in Home
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
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
                overridePendingTransition(0,0);
                finish();
                return true;
            }
            return false;
        });
    }




    private void initBluetooth() {
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null || !btAdapter.isEnabled()) {
            Toast.makeText(this, "Enable Bluetooth to get sensor data.", Toast.LENGTH_LONG).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "No paired Bluetooth devices found.", Toast.LENGTH_SHORT).show();
            return;
        }

        for (BluetoothDevice device : pairedDevices) {
            if (device.getName().startsWith("HC-05") || device.getName().startsWith("ESP")) {
                connectToDevice(device);
                break;
            }
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                btSocket = device.createRfcommSocketToServiceRecord(BT_UUID);
                btSocket.connect();
                inputStream = btSocket.getInputStream();
                listenForData();
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void listenForData() {
        Handler handler = new Handler(getMainLooper());
        byte[] buffer = new byte[1024];
        int bytes;

        while (true) {
            try {
                if (inputStream.available() > 0) {
                    bytes = inputStream.read(buffer);
                    String data = new String(buffer, 0, bytes).trim();
                    if (data.contains(":")) {
                        String[] parts = data.split(":");
                        String temp = parts[0];
                        String feed = parts[1];

                        handler.post(() -> {
                            txtTemperature.setText(temp + "°C");
                            txtFeedLevel.setText(feed + "%");
                        });
                    }
                }
            } catch (IOException e) {
                break;
            }
        }
    }
}
