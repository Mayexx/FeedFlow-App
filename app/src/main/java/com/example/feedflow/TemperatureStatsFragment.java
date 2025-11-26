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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

public class TemperatureStatsFragment extends Fragment {

    private LineChart tempLineChart;
    private FirebaseFirestore db;
    private ArrayList<Double> tempHistory = new ArrayList<>();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

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

        tempLineChart = view.findViewById(R.id.tempLineChart);
        db = FirebaseFirestore.getInstance();

        fetchWeeklyTemperature();
        connectToEsp32();
    }

    // ------------------------------------------------------------
    // 🔹 Fetch last 7 days temperature from Firestore
    // ------------------------------------------------------------
    private void fetchWeeklyTemperature() {
        db.collection("FeedFlow")
                .document("Device001")
                .collection("readings")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(7)
                .get()
                .addOnSuccessListener(querySnapshots -> {
                    ArrayList<Entry> entries = new ArrayList<>();
                    tempHistory.clear();
                    int index = 0;

                    // Reverse to get oldest first
                    ArrayList<QueryDocumentSnapshot> docs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshots) docs.add(0, doc);

                    for (QueryDocumentSnapshot doc : docs) {
                        Double temp = doc.getDouble("temperature");
                        if (temp != null) {
                            entries.add(new Entry(index, temp.floatValue()));
                            tempHistory.add(temp);
                            index++;
                        }
                    }

                    updateLineChart(entries);
                })
                .addOnFailureListener(e -> Log.e("FIRESTORE", "Failed to load chart", e));
    }

    // ------------------------------------------------------------
    // 🔹 Update chart with weekly temperature
    // ------------------------------------------------------------
    private void updateLineChart(ArrayList<Entry> entries) {
        LineDataSet dataSet = new LineDataSet(entries, "Sea Water Temp (°C)");
        dataSet.setColor(0xFF0288D1); // Blue line
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setValueTextSize(10f);

        LineData lineData = new LineData(dataSet);
        tempLineChart.setData(lineData);

        // X-axis labels
        ArrayList<String> labels = getLast7DaysLabels();
        tempLineChart.getXAxis().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = Math.round(value);
                if (index >= 0 && index < labels.size()) return labels.get(index);
                else return "";
            }
        });

        tempLineChart.getXAxis().setGranularity(1f);
        tempLineChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);

        tempLineChart.invalidate();
    }

    // ------------------------------------------------------------
    // 🔹 Last 7 days labels for X-axis
    // ------------------------------------------------------------
    private ArrayList<String> getLast7DaysLabels() {
        ArrayList<String> labels = new ArrayList<>();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EEE", Locale.getDefault());
        Calendar cal = Calendar.getInstance();

        for (int i = 6; i >= 0; i--) {
            cal.setTimeInMillis(System.currentTimeMillis());
            cal.add(Calendar.DAY_OF_YEAR, -i);
            labels.add(sdf.format(cal.getTime()));
        }
        return labels;
    }

    // ------------------------------------------------------------
    // 🔹 Bluetooth connection to ESP32 to read live data
    // ------------------------------------------------------------
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
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
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
                    String[] parts = line.split(":");
                    if (parts.length >= 1) {
                        try {
                            double waterTemp = Double.parseDouble(parts[0]);

                            requireActivity().runOnUiThread(() -> {
                                tempHistory.add(0, waterTemp);
                                if (tempHistory.size() > 50) tempHistory.remove(tempHistory.size() - 1);

                                ArrayList<Entry> entries = new ArrayList<>();
                                for (int i = 0; i < tempHistory.size() && i < 7; i++)
                                    entries.add(new Entry(i, tempHistory.get(tempHistory.size() - 1 - i).floatValue()));

                                updateLineChart(entries);
                                saveTemperature(waterTemp);
                            });
                        } catch (Exception ignored) {}
                    }
                }
            } catch (IOException e) {
                Log.e("BT", "Read failed", e);
            }
        }).start();
    }

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
