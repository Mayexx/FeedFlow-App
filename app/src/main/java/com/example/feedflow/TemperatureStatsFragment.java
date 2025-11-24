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

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class TemperatureStatsFragment extends Fragment {

    private TextView tvLastUpdated, tvTempCurrent, tvTempAverage;
    private TextView tvOptimalTime, tvBelowOptimal, tvAboveOptimal;
    private ImageView ivTempGraph;

    private FirebaseFirestore db;

    private ArrayList<Double> tempHistory = new ArrayList<>();

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // SPP UUID

    private final double OPTIMAL_MIN = 25.0;
    private final double OPTIMAL_MAX = 30.0;

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
        tvTempCurrent = view.findViewById(R.id.tvTempCurrent);
        tvTempAverage = view.findViewById(R.id.tvTempAverage);
        tvOptimalTime = view.findViewById(R.id.tvOptimalTime);
        tvBelowOptimal = view.findViewById(R.id.tvBelowOptimal);
        tvAboveOptimal = view.findViewById(R.id.tvAboveOptimal);
        ivTempGraph = view.findViewById(R.id.ivTempGraph);

        db = FirebaseFirestore.getInstance();

        fetchTemperatureFromFirestore();
        connectToEsp32();
    }

    private void fetchTemperatureFromFirestore() {
        db.collection("FeedFlow")
                .document("Device001")
                .get()
                .addOnSuccessListener(this::updateTemperatureUI)
                .addOnFailureListener(e -> Log.e("FIRESTORE", "Failed to fetch temperature", e));
    }

    private void updateTemperatureUI(DocumentSnapshot doc) {
        if (doc.exists()) {
            Double temp = doc.getDouble("temperature");
            Long timestamp = doc.getLong("timestamp");

            if (temp != null) {
                addTemperatureToHistory(temp);
                updateStatsUI();
            }

            if (timestamp != null) {
                String formatted = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                        .format(new Date(timestamp));
                tvLastUpdated.setText("Last Updated: " + formatted);
            }
        } else {
            tvTempCurrent.setText("No data");
        }
    }

    private void addTemperatureToHistory(double temp) {
        tempHistory.add(temp);
        if (tempHistory.size() > 50) { // keep last 50 readings
            tempHistory.remove(0);
        }
    }

    private void updateStatsUI() {
        if (tempHistory.isEmpty()) return;

        double sum = 0;
        int optimalCount = 0;
        int belowCount = 0;
        int aboveCount = 0;

        for (double t : tempHistory) {
            sum += t;
            if (t >= OPTIMAL_MIN && t <= OPTIMAL_MAX) optimalCount++;
            else if (t < OPTIMAL_MIN) belowCount++;
            else aboveCount++;
        }

        double avg = sum / tempHistory.size();
        tvTempAverage.setText(String.format(Locale.getDefault(), "Average: %.2f°C", avg));
        tvOptimalTime.setText(String.format(Locale.getDefault(), "Time in optimal: %.0f%%",
                100.0 * optimalCount / tempHistory.size()));
        tvBelowOptimal.setText(String.format(Locale.getDefault(), "Time below optimal: %.0f%%",
                100.0 * belowCount / tempHistory.size()));
        tvAboveOptimal.setText(String.format(Locale.getDefault(), "Time above optimal: %.0f%%",
                100.0 * aboveCount / tempHistory.size()));

        tvTempCurrent.setText(String.format(Locale.getDefault(), "Current: %.2f°C",
                tempHistory.get(tempHistory.size() - 1)));
    }

    private void connectToEsp32() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) return;

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    1001);
            return;
        }

        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            if (device.getName() != null && device.getName().equals("ESP32_Test")) {
                try {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                    bluetoothSocket.connect();
                    startReadingBluetoothData();
                } catch (IOException e) {
                    Log.e("BT", "Connection failed", e);
                }
                break;
            }
        }
    }

    private void startReadingBluetoothData() {
        new Thread(() -> {
            try {
                InputStream inputStream = bluetoothSocket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        double temp = Double.parseDouble(line);
                        requireActivity().runOnUiThread(() -> {
                            addTemperatureToHistory(temp);
                            updateStatsUI();
                            saveTemperatureToFirestore(temp);
                        });
                    } catch (NumberFormatException ignored) {}
                }
            } catch (IOException e) {
                Log.e("BT", "Error reading data", e);
            }
        }).start();
    }

    private void saveTemperatureToFirestore(double temperature) {
        db.collection("FeedFlow")
                .document("Device001")
                .update("temperature", temperature, "timestamp", System.currentTimeMillis())
                .addOnFailureListener(e -> Log.e("FIRESTORE", "Failed to save temperature", e));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001 &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            connectToEsp32();
        } else {
            Log.e("BT", "Bluetooth permissions denied");
        }
    }
}
