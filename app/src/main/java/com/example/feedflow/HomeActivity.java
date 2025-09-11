package com.example.feedflow;

import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class HomeActivity extends AppCompatActivity {

    private TextView txtTemperature, txtFeedLevel, txtFeedAmount, txtDeviceName;
    private Button btnFeedNow, btnIncrease, btnDecrease, btnAddSchedule;
    private Switch autoSwitch;
    private LinearLayout historyContainer;

    private BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;
    private InputStream inputStream;
    private final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private int feedAmount = 25;

    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "FeedFlowPrefs";

    private String connectedDeviceName = "Not Connected";
    private List<String> feedingHistory = new ArrayList<>(); // store feeding logs

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
        loadFeedingHistory();
    }

    private void initViews() {
        txtTemperature = findViewById(R.id.txtTemperature);
        txtFeedLevel = findViewById(R.id.txtFeedLevel);
        txtFeedAmount = findViewById(R.id.txtFeedAmount);
        txtDeviceName = findViewById(R.id.txtDeviceName);
        btnFeedNow = findViewById(R.id.btnFeedNow);
        btnIncrease = findViewById(R.id.btnIncrease);
        btnDecrease = findViewById(R.id.btnDecrease);
        autoSwitch = findViewById(R.id.autoSwitch);
        historyContainer = findViewById(R.id.historyContainer);
    }

    private void restoreSavedData() {
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        feedAmount = sharedPreferences.getInt("feedAmount", 25);
        txtFeedAmount.setText(feedAmount + " kg");
    }

    private void saveFeedLog(int amount) {
        String time = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault()).format(new Date());
        long now = System.currentTimeMillis();

        // Save in FeedFlowPrefs (history display)
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("lastFeedTime", time);
        editor.putInt("lastFeedAmount", amount);
        editor.apply();

        // Also save in FeedData (stats fragment)
        SharedPreferences statsPrefs = getSharedPreferences("FeedData", MODE_PRIVATE);
        SharedPreferences.Editor statsEditor = statsPrefs.edit();

        float todayFeed = statsPrefs.getFloat("todayFeed", 0f);
        float totalFeed = statsPrefs.getFloat("totalFeed", 0f);
        int daysCount = statsPrefs.getInt("daysCount", 0);

        // Reset if new day
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

        // Add to history
        addFeedingHistory("Manual Feeding\n" + time, amount + " kg");

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

        btnFeedNow.setOnClickListener(v -> saveFeedLog(feedAmount));

        autoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Toast.makeText(this, isChecked ? "Auto Feeding ON" : "Auto Feeding OFF", Toast.LENGTH_SHORT).show();
            sharedPreferences.edit().putBoolean("autoMode", isChecked).apply();
        });
    }


    // Feeding History methods
    private void addFeedingHistory(String details, String amount) {
        LinearLayout item = (LinearLayout) LayoutInflater.from(this)
                .inflate(R.layout.item_history, historyContainer, false);

        TextView txtDetails = item.findViewById(R.id.txtHistoryDetails);
        TextView txtAmount = item.findViewById(R.id.txtHistoryAmount);

        txtDetails.setText(details);
        txtAmount.setText(amount);

        historyContainer.addView(item, 0); // add latest on top

        // Save in list + SharedPreferences
        feedingHistory.add(0, details + "|" + amount);
        saveFeedingHistory();
    }

    private void saveFeedingHistory() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("feedingHistory", String.join(";;", feedingHistory));
        editor.apply();
    }

    private void loadFeedingHistory() {
        feedingHistory.clear();
        historyContainer.removeAllViews();

        String saved = sharedPreferences.getString("feedingHistory", "");
        if (!saved.isEmpty()) {
            String[] items = saved.split(";;");
            for (String entry : items) {
                String[] parts = entry.split("\\|");
                if (parts.length == 2) {
                    feedingHistory.add(entry);
                    // rebuild UI
                    LinearLayout item = (LinearLayout) LayoutInflater.from(this)
                            .inflate(R.layout.item_history, historyContainer, false);

                    TextView txtDetails = item.findViewById(R.id.txtHistoryDetails);
                    TextView txtAmount = item.findViewById(R.id.txtHistoryAmount);

                    txtDetails.setText(parts[0]);
                    txtAmount.setText(parts[1]);

                    historyContainer.addView(item);
                }
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

        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
            return;
        }

        if (!btAdapter.isEnabled()) {
            Toast.makeText(this, "Enable Bluetooth to get sensor data.", Toast.LENGTH_LONG).show();
            txtDeviceName.setText("Not Connected");
            return;
        }

        // For Android 12+ → Need runtime permissions
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.BLUETOOTH_SCAN,
                            android.Manifest.permission.BLUETOOTH_CONNECT
                    }, 100);
            return;
        }

        // Cancel ongoing discovery if any
        if (btAdapter.isDiscovering()) {
            btAdapter.cancelDiscovery();
        }
        btAdapter.startDiscovery();

        // Register BroadcastReceiver for found devices
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }

                    if (device != null && device.getName() != null) {
                        String deviceName = device.getName();
                        String deviceAddress = device.getAddress();

                        Log.d("BT_SCAN", "Found: " + deviceName + " [" + deviceAddress + "]");
                        Toast.makeText(context, "Found: " + deviceName, Toast.LENGTH_SHORT).show();

                        // Example: auto-connect to first HC-05 / ESP device found
                        if (deviceName.startsWith("HC-05") || deviceName.startsWith("ESP")) {
                            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                                return;
                            }
                            btAdapter.cancelDiscovery();
                            connectToDevice(device);
                            txtDeviceName.setText("Connecting: " + deviceName);
                        }
                    }
                }
            }
        };

        // Register ACTION_FOUND intent filter
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
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

                        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

                        handler.post(() -> {
                            txtTemperature.setText(temp + "°C");
                            txtFeedLevel.setText(feed + "%");
                            txtDeviceName.setText(connectedDeviceName + "\nLast Updated: " + time);

                            SharedPreferences.Editor editor = sharedPreferences.edit();
                            editor.putString("latestTemperature", temp);
                            editor.putString("latestFeedLevel", feed);
                            editor.putString("lastUpdatedTime", time);
                            editor.apply();
                        });
                    }
                }
            } catch (IOException e) {
                break;
            }
        }
    }
}
