package com.example.feedflow;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.UUID;

public class TemperatureStatsFragment extends Fragment {

    private TextView tvLastUpdated, tvTempCurrent, tvTempAverage;
    private TextView tvOptimalTime, tvBelowOptimal, tvAboveOptimal;
    private LineChart tempLineChart;

    private FirebaseFirestore db;
    private ArrayList<Double> tempHistory = new ArrayList<>();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;

    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

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

        tvLastUpdated   = view.findViewById(R.id.tvLastUpdated);
        tvTempCurrent   = view.findViewById(R.id.tvTempCurrent);
        tvTempAverage   = view.findViewById(R.id.tvTempAverage);
        tvOptimalTime   = view.findViewById(R.id.tvOptimalTime);
        tvBelowOptimal  = view.findViewById(R.id.tvBelowOptimal);
        tvAboveOptimal  = view.findViewById(R.id.tvAboveOptimal);
        tempLineChart   = view.findViewById(R.id.tempLineChart);

        db = FirebaseFirestore.getInstance();

        fetchTemperatureTrends();
        connectToEsp32();
    }

    // -------------------------
    // 🔹 Load Firestore readings
    // -------------------------
    private void fetchTemperatureTrends() {
        db.collection("FeedFlow")
                .document("Device001")
                .collection("readings")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    ArrayList<Entry> entries = new ArrayList<>();
                    int index = 0;

                    tempHistory.clear();

                    for (QueryDocumentSnapshot doc : querySnapshots) {
                        Double temp = doc.getDouble("temperature");
                        if (temp != null) {
                            entries.add(new Entry(index, temp.floatValue()));
                            tempHistory.add(temp);
                            index++;
                        }
                    }

                    updateStatsUI();
                    updateLineChart(entries);
                })
                .addOnFailureListener(e -> Log.e("FIRESTORE", "Failed to load chart", e));
    }

    // -------------------------
    // 🔹 Update MPAndroidChart
    // -------------------------
    private void updateLineChart(ArrayList<Entry> entries) {
        LineDataSet dataSet = new LineDataSet(entries, "Sea Water Temp (°C)");

        dataSet.setColor(Color.BLUE); // or use a resource
        dataSet.setColor(Color.RED);

        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setValueTextSize(10f);

        LineData lineData = new LineData(dataSet);
        tempLineChart.setData(lineData);

        Description desc = new Description();
        desc.setText("Sea Water Temperature Trends");
        tempLineChart.setDescription(desc);

        tempLineChart.invalidate();
    }


    // -------------------------
    // 🔹 Temperature statistics
    // -------------------------
    private void updateStatsUI() {
        if (tempHistory.isEmpty()) return;

        double sum = 0;
        int optimal = 0, below = 0, above = 0;

        for (double t : tempHistory) {
            sum += t;

            if (t >= OPTIMAL_MIN && t <= OPTIMAL_MAX) optimal++;
            else if (t < OPTIMAL_MIN) below++;
            else above++;
        }

        double avg = sum / tempHistory.size();

        tvTempAverage.setText(String.format("Average: %.2f°C", avg));
        tvOptimalTime.setText(String.format("Optimal: %.0f%%",
                (optimal * 100.0 / tempHistory.size())));
        tvBelowOptimal.setText(String.format("Below: %.0f%%",
                (below * 100.0 / tempHistory.size())));
        tvAboveOptimal.setText(String.format("Above: %.0f%%",
                (above * 100.0 / tempHistory.size())));

        tvTempCurrent.setText(
                String.format("Current: %.2f°C",
                        tempHistory.get(tempHistory.size() - 1))
        );
    }

    // -------------------------
    // 🔹 Bluetooth
    // -------------------------
    private void connectToEsp32() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) return;

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN},
                    1001);
            return;
        }

        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
            if ("ESP32_Test".equals(device.getName())) {
                try {
                    bluetoothSocket =
                            device.createRfcommSocketToServiceRecord(MY_UUID);
                    bluetoothSocket.connect();
                    startReadingBluetooth();
                } catch (IOException e) {
                    Log.e("BT", "Connection failed", e);
                }
            }
        }
    }

    private void startReadingBluetooth() {
        new Thread(() -> {
            try {
                InputStream inputStream = bluetoothSocket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;

                while ((line = reader.readLine()) != null) {
                    try {
                        double temp = Double.parseDouble(line);

                        requireActivity().runOnUiThread(() -> {
                            tempHistory.add(temp);
                            if (tempHistory.size() > 50) tempHistory.remove(0);

                            updateStatsUI();
                            saveTemperature(temp);
                        });

                    } catch (Exception ignored) {
                    }
                }
            } catch (IOException e) {
                Log.e("BT", "Read failed", e);
            }
        }).start();
    }

    // -------------------------
    // 🔹 Save to Firestore
    // -------------------------
    private void saveTemperature(double temp) {
        db.collection("FeedFlow")
                .document("Device001")
                .collection("readings")
                .add(new TempReading(temp, System.currentTimeMillis()))
                .addOnSuccessListener(doc -> Log.d("FIRESTORE", "Saved"))
                .addOnFailureListener(e -> Log.e("FIRESTORE", "Save failed", e));
    }

    public static class TempReading {
        public double temperature;
        public long timestamp;

        TempReading() {}

        TempReading(double temp, long ts) {
            this.temperature = temp;
            this.timestamp = ts;
        }
    }
}
