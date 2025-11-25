package com.example.feedflow;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.firestore.FirebaseFirestore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FeedingStatsFragment extends Fragment {

    private TextView tvLastUpdated, tvTodayFeed, tvFeedLevel, tvAverageFeed, tvFeedEfficiency;
    private ImageView ivFeedGraph;

    private FirebaseFirestore db;
    private ArrayList<Double> feedHistory = new ArrayList<>();
    private ArrayList<Double> feedLevelHistory = new ArrayList<>();

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_temperature_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvLastUpdated = view.findViewById(R.id.tvLastUpdated);
        tvTodayFeed = view.findViewById(R.id.tvTodayFeed);
        tvFeedLevel = view.findViewById(R.id.tvFeedLevel);
        tvAverageFeed = view.findViewById(R.id.tvAverageFeed);
        tvFeedEfficiency = view.findViewById(R.id.tvFeedEfficiency);
        ivFeedGraph = view.findViewById(R.id.ivFeedGraph);

        db = FirebaseFirestore.getInstance();

        connectToEsp32();
        fetchFeedStats();
    }

    private void fetchFeedStats() {
        db.collection("FeedFlow")
                .document("Device001")
                .collection("weight")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    double totalFeed = 0;
                    int count = 0;

                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : querySnapshots) {
                        Double dispensed = doc.getDouble("dispensed");
                        Double remaining = doc.getDouble("remaining");
                        Long timestamp = doc.getLong("timestamp");

                        if (dispensed != null) {
                            feedHistory.add(dispensed);
                            if (remaining != null) feedLevelHistory.add(remaining);

                            if (count == 0) {
                                tvTodayFeed.setText("Feed Dispensed: " + dispensed + " kg");
                                if (remaining != null) tvFeedLevel.setText("Feed Level: " + remaining + "%");
                                if (timestamp != null) {
                                    String formatted = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                                            .format(new Date(timestamp));
                                    tvLastUpdated.setText("Last Updated: " + formatted);
                                }
                            }
                            totalFeed += dispensed;
                            count++;
                        }
                    }

                    if (count > 0) {
                        tvAverageFeed.setText("Average Feed: " + String.format("%.2f", totalFeed / count) + " kg");
                    } else {
                        tvAverageFeed.setText("Average Feed: -- kg");
                    }

                    tvFeedEfficiency.setText("Feed Efficiency: -- FCR");
                })
                .addOnFailureListener(e -> Log.w("FIRESTORE", "Error loading feed data", e));
    }

    private void connectToEsp32() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) return;

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, 2001);
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (device.getName() != null && device.getName().equals("ESP32_Feeder")) {
                try {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    bluetoothSocket.connect();
                    startReadingData();
                } catch (IOException e) {
                    Log.e("BT", "Connection failed", e);
                }
                break;
            }
        }
    }

    private void startReadingData() {
        new Thread(() -> {
            try {
                InputStream inputStream = bluetoothSocket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        String[] parts = line.split(";");
                        double dispensed = 0, remaining = 0;

                        for (String part : parts) {
                            if (part.startsWith("FEED:")) dispensed = Double.parseDouble(part.replace("FEED:", ""));
                            else if (part.startsWith("REMAIN:")) remaining = Double.parseDouble(part.replace("REMAIN:", ""));
                        }

                        double finalDispensed = dispensed;
                        double finalRemaining = remaining;

                        requireActivity().runOnUiThread(() -> {
                            feedHistory.add(finalDispensed);
                            feedLevelHistory.add(finalRemaining);

                            tvTodayFeed.setText("Feed Dispensed: " + finalDispensed + " kg");
                            tvFeedLevel.setText("Feed Level: " + finalRemaining + "%");
                            tvLastUpdated.setText("Last Updated: " + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));

                            saveFeedingDataToFirestore(finalDispensed, finalRemaining);
                            if (finalRemaining < 25) addAlertToFirestore("Low Feed Level", "Feed storage is below 25%. Refill needed.");
                        });

                    } catch (Exception ignored) {}
                }
            } catch (IOException e) {
                Log.e("BT", "Error reading data", e);
            }
        }).start();
    }
    private void saveFeedingDataToFirestore(double dispensed, double remaining) {
        Map<String, Object> data = new HashMap<>();
        data.put("dispensed", dispensed);
        data.put("remaining", remaining);
        data.put("timestamp", System.currentTimeMillis());

        db.collection("FeedFlow")
                .document("Device001")
                .collection("weight")
                .add(data)
                .addOnFailureListener(e -> Log.e("FIRESTORE", "Failed to save feed data", e));
    }

    private void addAlertToFirestore(String title, String description) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("title", title);
        alert.put("description", description);
        alert.put("time", new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date()));
        alert.put("status", "Warning");

        db.collection("alerts").add(alert);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2001 &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            connectToEsp32();
        } else {
            Log.e("BT", "Bluetooth permissions denied");
        }
    }
}
