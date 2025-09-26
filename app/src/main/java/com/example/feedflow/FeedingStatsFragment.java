package com.example.feedflow;

import com.google.firebase.firestore.FirebaseFirestore;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import android.util.Log;

import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class FeedingStatsFragment extends Fragment {
    // Firebase
    private FirebaseFirestore db;

    private TextView tvLastUpdated, tvTodayFeed, tvFeedLevel, tvAverageFeed, tvFeedEfficiency;

    // --- Bluetooth ---
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private Handler handler = new Handler();
    private boolean isReading = false;

    // Replace this with your ESP32 MAC address
    private static final String DEVICE_ADDRESS = "XX:XX:XX:XX:XX:XX";
    private static final UUID BT_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_feeding_stats, container, false);

        tvLastUpdated = view.findViewById(R.id.tvLastUpdated);
        tvTodayFeed = view.findViewById(R.id.tvTodayFeed);
        tvFeedLevel = view.findViewById(R.id.tvFeedLevel);
        tvAverageFeed = view.findViewById(R.id.tvAverageFeed);
        tvFeedEfficiency = view.findViewById(R.id.tvFeedEfficiency);

        db = FirebaseFirestore.getInstance();

        // Load saved data
        loadFeedingData();

        // Connect to Bluetooth ESP32
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        connectToBluetooth();

        // Load from Firestore too
        fetchFirestoreFeedData();

        return view;
    }

    // 🔹 Firestore fetch for last readings
    private void fetchFirestoreFeedData() {
        db.collection("FeedFlow")
                .document("Device001")
                .collection("readings")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Double feed = doc.getDouble("feedLevel");
                        Long timestamp = doc.getLong("timestamp");

                        Log.d("STATS", "Feed: " + feed + ", Time: " + timestamp);

                        if (timestamp != null) {
                            String lastUpdated = "Last Updated: " + new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                                    .format(new Date(timestamp));
                            tvLastUpdated.setText(lastUpdated);
                        }
                        if (feed != null) {
                            tvFeedLevel.setText("Feed Level: " + feed + "%");
                            tvTodayFeed.setText("Today: " + feed + " kg");

                            checkFeedLevelAndTriggerAlert(feed);
                        }
                    }
                })
                .addOnFailureListener(e -> Log.w("FIRESTORE", "Error reading stats", e));
    }

    // 🔹 Bluetooth connection
    private void connectToBluetooth() {
        if (bluetoothAdapter == null) {
            Log.e("BT", "Bluetooth not supported");
            return;
        }

        try {
            if (ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e("BT", "No BLUETOOTH_CONNECT permission granted");
                return;
            }

            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(DEVICE_ADDRESS);
            bluetoothSocket = device.createRfcommSocketToServiceRecord(BT_UUID);

            bluetoothSocket.connect();
            inputStream = bluetoothSocket.getInputStream();
            isReading = true;

            new Thread(this::listenForData).start();
        } catch (IOException e) {
            Log.e("BT", "Connection failed", e);
        }
    }

    // 🔹 Listen for ESP32 data
    private void listenForData() {
        byte[] buffer = new byte[1024];
        int bytes;

        while (isReading) {
            try {
                if (inputStream.available() > 0) {
                    bytes = inputStream.read(buffer);
                    String incoming = new String(buffer, 0, bytes).trim();

                    // Assume ESP32 sends: "FEED:5;REMAIN:45"
                    handler.post(() -> handleIncomingData(incoming));
                }
            } catch (IOException e) {
                Log.e("BT", "Error reading", e);
                break;
            }
        }
    }

    // 🔹 Parse incoming data
    private void handleIncomingData(String data) {
        try {
            String[] parts = data.split(";");
            int dispensed = 0;
            int remaining = 0;

            for (String part : parts) {
                if (part.startsWith("FEED:")) {
                    dispensed = Integer.parseInt(part.replace("FEED:", ""));
                } else if (part.startsWith("REMAIN:")) {
                    remaining = Integer.parseInt(part.replace("REMAIN:", ""));
                }
            }

            // Update UI
            tvTodayFeed.setText("Feed Dispensed: " + dispensed + " kg");
            tvFeedLevel.setText("Feed Remaining: " + remaining + "%");
            tvLastUpdated.setText("Last Updated: " +
                    new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(new Date()));

            // Save to Firestore
            Map<String, Object> feedData = new HashMap<>();
            feedData.put("dispensed", dispensed);
            feedData.put("remaining", remaining);
            feedData.put("timestamp", System.currentTimeMillis());

            db.collection("FeedFlow")
                    .document("Device001")
                    .collection("feeding")
                    .add(feedData);

            checkFeedLevelAndTriggerAlert(remaining);

        } catch (Exception e) {
            Log.e("BT", "Parse error: " + data, e);
        }
    }

    private void loadFeedingData() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("FeedData", Context.MODE_PRIVATE);

        int feedLevel = prefs.getInt("feedLevel", -1);
        long lastUpdated = prefs.getLong("lastUpdated", 0);

        // --- Per-day feeding totals ---
        String todayKey = getTodayKey();
        float todayFeed = prefs.getFloat(todayKey, 0f);

        // Sum for weekly feed
        float weeklyTotal = 0f;
        int daysWithData = 0;
        for (int i = 0; i < 7; i++) {
            String key = getDayKey(i);
            float dailyFeed = prefs.getFloat(key, 0f);
            if (dailyFeed > 0) {
                weeklyTotal += dailyFeed;
                daysWithData++;
            }
        }

        float totalWeightGain = prefs.getFloat("weightGain", 0f);

        if (lastUpdated > 0) {
            String formattedDate = DateFormat.getDateTimeInstance().format(new Date(lastUpdated));
            tvLastUpdated.setText("Last Updated: " + formattedDate);
        } else {
            tvLastUpdated.setText("Last Updated: --");
        }

        tvTodayFeed.setText("Today: " + todayFeed + " kg");
        tvFeedLevel.setText("Feed Level: " + (feedLevel >= 0 ? feedLevel + "%" : "--%"));

        if (daysWithData > 0) {
            float averageFeed = weeklyTotal / daysWithData;
            tvAverageFeed.setText("Average Daily Feed: " + String.format("%.2f", averageFeed) + " kg");
        } else {
            tvAverageFeed.setText("Average Daily Feed: --");
        }

        if (totalWeightGain > 0) {
            float fcr = weeklyTotal / totalWeightGain;
            tvFeedEfficiency.setText("Feed Efficiency: " + String.format("%.2f", fcr) + " FCR");
        } else {
            tvFeedEfficiency.setText("Feed Efficiency: --");
        }
    }

    private String getTodayKey() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return "feed_" + sdf.format(new Date());
    }

    private String getDayKey(int daysAgo) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        return "feed_" + sdf.format(cal.getTime());
    }

    private void checkFeedLevelAndTriggerAlert(double feedLevel) {
        if (feedLevel < 20) { // adjust threshold
            addAlertToFirestore(
                    "Low Feed Level",
                    "Feed storage is below 20%. Current level: " + feedLevel + "%"
            );
        }
    }

    private void addAlertToFirestore(String title, String description) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> alert = new HashMap<>();
        alert.put("title", title);
        alert.put("description", description);
        alert.put("time", new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date()));
        alert.put("status", "Warning");

        db.collection("alerts").add(alert);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isReading = false;
        try {
            if (inputStream != null) inputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException e) {
            Log.e("BT", "Error closing BT", e);
        }
    }
}
