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

public class TemperatureStatsFragment extends Fragment {

    // Declare views
    private TextView tvLastUpdated, tvTempCurrent, tvTempAverage;
    private TextView tvOptimalTime, tvBelowOptimal, tvAboveOptimal;
    private ImageView ivTempGraph;

    private FirebaseFirestore db;

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Standard SPP UUID

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Inflate layout
        return inflater.inflate(R.layout.fragment_temperature_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind views
        tvLastUpdated = view.findViewById(R.id.tvLastUpdated);
        tvTempCurrent = view.findViewById(R.id.tvTempCurrent);
        tvTempAverage = view.findViewById(R.id.tvTempAverage);
        tvOptimalTime = view.findViewById(R.id.tvOptimalTime);
        tvBelowOptimal = view.findViewById(R.id.tvBelowOptimal);
        tvAboveOptimal = view.findViewById(R.id.tvAboveOptimal);
        ivTempGraph = view.findViewById(R.id.ivTempGraph);

        db = FirebaseFirestore.getInstance();

        // 🔹 Try to connect to ESP32 via Bluetooth
        connectToEsp32();

        // 🔹 Fetch stored temperature history from Firestore
        fetchTemperatureStats();
    }

    // ------------------ FIRESTORE STATS ------------------
    private void fetchTemperatureStats() {
        db.collection("FeedFlow")
                .document("Device001")
                .collection("readings")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    float totalTemp = 0;
                    int count = 0;

                    for (QueryDocumentSnapshot doc : querySnapshots) {
                        Double temp = doc.getDouble("temperature");
                        Long timestamp = doc.getLong("timestamp");

                        if (temp != null) {
                            if (count == 0) {
                                // Latest temp
                                tvTempCurrent.setText("Current: " + temp + "°C");
                                if (timestamp != null) {
                                    String formatted = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                                            .format(new Date(timestamp));
                                    tvLastUpdated.setText("Last Updated: " + formatted);
                                }
                            }
                            totalTemp += temp;
                            count++;
                        }
                    }

                    // Average temperature
                    if (count > 0) {
                        float avg = totalTemp / count;
                        tvTempAverage.setText("Average: " + String.format("%.2f", avg) + "°C");
                    } else {
                        tvTempAverage.setText("Average: --°C");
                    }

                    // TODO: Calculate optimal/below/above percentages based on your rules
                    tvOptimalTime.setText("Time in optimal range: --%");
                    tvBelowOptimal.setText("Time below optimal: --%");
                    tvAboveOptimal.setText("Time above optimal: --%");
                })
                .addOnFailureListener(e -> Log.w("FIRESTORE", "Error loading temperature data", e));
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

        // 🔹 Check Bluetooth permissions
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    1001);
            return; // wait until user grants permission
        }

        // Get paired devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                if (device.getName().equals("ESP32_Test")) { // change to your ESP32 name
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

    private void startReadingData() {
        new Thread(() -> {
            try {
                InputStream inputStream = bluetoothSocket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        double temp = Double.parseDouble(line);
                        requireActivity().runOnUiThread(() -> {
                            tvTempCurrent.setText("Current: " + temp + "°C");
                            tvLastUpdated.setText("Last Updated: " +
                                    new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));

                            checkTemperatureAndTriggerAlert(temp);
                            saveTemperatureToFirestore(temp);
                        });
                    } catch (NumberFormatException e) {
                        Log.w("BT", "Invalid data: " + line);
                    }
                }
            } catch (IOException e) {
                Log.e("BT", "Error reading data", e);
            }
        }).start();
    }

    // ------------------ FIRESTORE ALERTS ------------------
    private void checkTemperatureAndTriggerAlert(double temperature) {
        if (temperature > 30) {
            addAlertToFirestore(
                    "High Water Temperature",
                    "Water temperature is " + temperature + "°C. Optimal range is 25–30°C."
            );
        } else if (temperature < 25) {
            addAlertToFirestore(
                    "Low Water Temperature",
                    "Water temperature is " + temperature + "°C. Optimal range is 25–30°C."
            );
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

    private void saveTemperatureToFirestore(double temperature) {
        Map<String, Object> reading = new HashMap<>();
        reading.put("temperature", temperature);
        reading.put("timestamp", System.currentTimeMillis());

        db.collection("FeedFlow")
                .document("Device001")
                .collection("readings")
                .add(reading);
    }

    // ------------------ PERMISSION HANDLER ------------------
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                connectToEsp32(); // retry after permission granted
            } else {
                Log.e("BT", "Bluetooth permissions denied");
            }
        }
    }
}
