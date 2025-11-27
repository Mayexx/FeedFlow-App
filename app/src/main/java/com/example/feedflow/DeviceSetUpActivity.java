package com.example.feedflow;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class DeviceSetUpActivity extends AppCompatActivity {

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    public static String latestBluetoothData = "";

    private enum ConnectionState {DISCONNECTED, CONNECTING, CONNECTED}
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;

    private String connectedDeviceName;
    private String connectedDeviceAddress;

    private Spinner deviceSpinner;
    private Button confirmButton;
    private TextView txtBluetoothStatus;

    private volatile boolean stopReading = false;
    private int retryCount = 0;
    private final int MAX_RETRIES = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devicesetup);

        deviceSpinner = findViewById(R.id.deviceSpinner);
        confirmButton = findViewById(R.id.confirmButton);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        checkPermissions();
        loadPairedDevices();

        confirmButton.setOnClickListener(v -> connectToSelectedDevice());
    }

    // --- Permissions ---
    private void checkPermissions() {
        ArrayList<String> permissions = new ArrayList<>();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]),
                    REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) granted = false;
            }
            if (!granted) {
                Toast.makeText(this, "Bluetooth permission denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // --- Load paired devices ---
    private void loadPairedDevices() {
        ArrayList<String> devicesList = new ArrayList<>();
        devicesList.add("Select Device");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) return;

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            devicesList.add(device.getName() + " - " + device.getAddress());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, devicesList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);
    }

    // --- Connect to selected device ---
    private void connectToSelectedDevice() {
        String selected = (String) deviceSpinner.getSelectedItem();
        if (selected == null || selected.equals("Select Device")) {
            Toast.makeText(this, "Please select a device", Toast.LENGTH_SHORT).show();
            return;
        }

        int lastDash = selected.lastIndexOf("-");
        connectedDeviceName = selected.substring(0, lastDash).trim();
        connectedDeviceAddress = selected.substring(lastDash + 1).trim();

        confirmButton.setEnabled(false);
        updateBluetoothStatus(ConnectionState.CONNECTING);
        retryCount = 0;
        connectWithRetry();
    }

    private void connectWithRetry() {
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) return;

                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(connectedDeviceAddress);
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothAdapter.cancelDiscovery();
                bluetoothSocket.connect();

                runOnUiThread(() -> {
                    Toast.makeText(this, "Connected to " + connectedDeviceName, Toast.LENGTH_SHORT).show();
                    updateBluetoothStatus(ConnectionState.CONNECTED);
                });

                readSensorData();

            } catch (IOException e) {
                retryCount++;
                if (retryCount <= MAX_RETRIES) {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    connectWithRetry();
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Bluetooth connection failed.", Toast.LENGTH_LONG).show();
                        confirmButton.setEnabled(true);
                        updateBluetoothStatus(ConnectionState.DISCONNECTED);
                    });
                }
            }
        }).start();
    }

    // --- Read data from ESP32 ---
    private void readSensorData() {
        stopReading = false;

        new Thread(() -> {
            try {
                InputStream inputStream = bluetoothSocket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while (!stopReading && (line = reader.readLine()) != null) {
                    latestBluetoothData = line;
                    String finalLine = line;
                    runOnUiThread(() -> Log.d("BT_DATA", finalLine));
                }
            } catch (IOException e) {
                if (!stopReading) Log.e("BT_READ", "Failed to read data", e);
                runOnUiThread(() -> updateBluetoothStatus(ConnectionState.DISCONNECTED));
            }
        }).start();
    }

    private void updateBluetoothStatus(ConnectionState state) {
        connectionState = state;
        switch (state) {
            case CONNECTED:
                txtBluetoothStatus.setText("Connected to " + connectedDeviceName);
                txtBluetoothStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                break;
            case CONNECTING:
                txtBluetoothStatus.setText("Connecting...");
                txtBluetoothStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                break;
            case DISCONNECTED:
            default:
                txtBluetoothStatus.setText("Disconnected");
                txtBluetoothStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopReading = true;
        try {
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
