package com.example.feedflow;

import android.Manifest;
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
import androidx.fragment.app.Fragment;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FeedingStatsFragment extends Fragment {

    // 🔹 UI
    private TextView tvLastUpdated, tvTodayFeed, tvFeedLevel, tvAverageFeed, tvFeedEfficiency;
    private ImageView ivFeedGraph;

    // 🔹 Firebase
    private FirebaseFirestore db;

    // 🔹 Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // SPP UUID

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feeding_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind UI
        tvLastUpdated = view.findViewById(R.id.tvLastUpdated);
        tvTodayFeed = view.findViewById(R.id.tvTodayFeed);
        tvFeedLevel = view.findViewById(R.id.tvFeedLevel);
        tvAverageFeed = view.findViewById(R.id.tvAverageFeed);
        tvFeedEfficiency = view.findViewById(R.id.tvFeedEfficiency);
        ivFeedGraph = view.findViewById(R.id.ivFeedGraph);

        db = FirebaseFirestore.getInstance();

        // 🔹 Connect to ESP32
        connectToEsp32();

        // 🔹 Fetch existing feed stats from Firestore
        fetchFeedStats();
    }

    // ------------------ FIRESTORE FEED STATS ------------------
    private void fetchFeedStats() {
        db.collection("FeedFlow")
                .document("Device001")
                .collection("feeding")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    float totalFeed = 0;
                    int count = 0;

                    for (QueryDocumentSnapshot doc : querySnapshots) {
                        Double dispensed = doc.getDouble("dispensed");
                        Double remaining = doc.getDouble("remaining");
                        Long timestamp = doc.getLong("timestamp");

                        if (dispensed != null) {
                            if (count == 0) {
                                tvTodayFeed.setText("Feed Dispensed: " + dispensed + " kg");
                                if (remaining != null)
                                    tvFeedLevel.setText("Feed Level: " + remaining + "%");
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
                        float avgFeed = totalFeed / count;
                        tvAverageFeed.setText("Average Feed: " + String.format("%.2f", avgFeed) + " kg");
                    } else {
                        tvAverageFeed.setText("Average Feed: -- kg");
                    }

                    tvFeedEfficiency.setText("Feed Efficiency: -- FCR");
                })
                .addOnFailureListener(e -> Log.w("FIRESTORE", "Error loading feed data", e));
    }

    // ------------------ BLUETOOTH SETUP ------------------
    private void connectToEsp32() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e("BT", "Device doesn’t support Bluetooth");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.e("BT", "Bluetooth is disabled");
            return;
        }

        // Check permissions
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    2001);
            return;
        }

        // Find paired ESP32
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("ESP32_Feeder")) { // change to your ESP32 name
                    try {
                        bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                        bluetoothSocket.connect();
                        Log.d("BT", "Connected to ESP32");
                        startReadingData();
                    } catch (IOException e) {
                        Log.e("BT", "Connection failed", e);
                    }
                    break;
                }
            }
        }
    }

    // ------------------ READ ESP32 DATA ------------------
    private void startReadingData() {
        new Thread(() -> {
            try {
                InputStream inputStream = bluetoothSocket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;

                while ((line = reader.readLine()) != null) {
                    try {
                        // Example incoming: "FEED:3.5;REMAIN:60"
                        String[] parts = line.split(";");
                        double dispensed = 0, remaining = 0;

                        for (String part : parts) {
                            if (part.startsWith("FEED:")) {
                                dispensed = Double.parseDouble(part.replace("FEED:", ""));
                            } else if (part.startsWith("REMAIN:")) {
                                remaining = Double.parseDouble(part.replace("REMAIN:", ""));
                            }
                        }

                        double finalDispensed = dispensed;
                        double finalRemaining = remaining;

                        requireActivity().runOnUiThread(() -> {
                            tvTodayFeed.setText("Feed Dispensed: " + finalDispensed + " kg");
                            tvFeedLevel.setText("Feed Level: " + finalRemaining + "%");
                            tvLastUpdated.setText("Last Updated: " +
                                    new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));

                            checkFeedLevelAndTriggerAlert(finalRemaining);
                            saveFeedingDataToFirestore(finalDispensed, finalRemaining);
                        });

                    } catch (Exception e) {
                        Log.w("BT", "Invalid data: " + line);
                    }
                }
            } catch (IOException e) {
                Log.e("BT", "Error reading data", e);
            }
        }).start();
    }

    // ------------------ ALERT HANDLING ------------------
    private void checkFeedLevelAndTriggerAlert(double feedLevel) {
        if (feedLevel < 25) {
            addAlertToFirestore("Low Feed Level",
                    "Feed storage is below 25%. Refill is needed.");
        }
    }

    private void addAlertToFirestore(String title, String description) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("title", title);
        alert.put("description", description);
        alert.put("time", new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date()));
        alert.put("status", "Warning");

        db.collection("alerts").add(alert);
    }

    // ------------------ FIRESTORE SAVE ------------------
    private void saveFeedingDataToFirestore(double dispensed, double remaining) {
        Map<String, Object> data = new HashMap<>();
        data.put("dispensed", dispensed);
        data.put("remaining", remaining);
        data.put("timestamp", System.currentTimeMillis());

        db.collection("FeedFlow")
                .document("Device001")
                .collection("feeding")
                .add(data);
    }

    // ------------------ PERMISSIONS ------------------
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToEsp32();
            } else {
                Log.e("BT", "Bluetooth permissions denied");
            }
        }
    }
}
