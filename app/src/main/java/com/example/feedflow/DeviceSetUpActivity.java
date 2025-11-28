package com.example.feedflow;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class DeviceSetUpActivity extends AppCompatActivity {

    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;

    private int retryCount = 0;
    private final int MAX_RETRIES = 3;

    private OnBluetoothDataReceived dataCallback;
    private volatile boolean stopReading = false;

    private String connectedDeviceAddress;
    private String connectedDeviceName;

    private Spinner deviceSpinner;
    private Button confirmButton;
    private FrameLayout loadingOverlay;

    // UUID for HC-05 / HC-06 / ESP32 SPP
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devicesetup);

        deviceSpinner = findViewById(R.id.deviceSpinner);
        confirmButton = findViewById(R.id.confirmButton);
        loadingOverlay = findViewById(R.id.loadingOverlay);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this,
                    "Bluetooth not supported on this device",
                    Toast.LENGTH_LONG).show();
            finish();
        }

        checkPermissions();
        loadPairedDevices();
        startDeviceDiscovery();

        confirmButton.setOnClickListener(v -> connectToSelectedDevice());
    }


    // ------------------------------------------------------------------
    // BLUETOOTH DISCOVERY RECEIVER
    // ------------------------------------------------------------------

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {

                BluetoothDevice device =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (device == null) return;

                String deviceInfo = device.getName() + " - " + device.getAddress();

                ArrayAdapter<String> adapter =
                        (ArrayAdapter<String>) deviceSpinner.getAdapter();

                if (adapter != null && adapter.getPosition(deviceInfo) == -1) {
                    adapter.add(deviceInfo);
                    adapter.notifyDataSetChanged();
                }
            }
        }
    };


    // ------------------------------------------------------------------
    // CALLBACK INTERFACE
    // ------------------------------------------------------------------

    public interface OnBluetoothDataReceived {
        void onDataReceived(String data);
    }

    public void setDataCallback(OnBluetoothDataReceived callback) {
        this.dataCallback = callback;
    }


    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(discoveryReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(discoveryReceiver);
    }


    // ------------------------------------------------------------------
    // PERMISSIONS
    // ------------------------------------------------------------------

    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissions.toArray(new String[0]),
                    REQUEST_BLUETOOTH_PERMISSIONS
            );
        }
    }


    // ------------------------------------------------------------------
    // LOAD PAIRED DEVICES
    // ------------------------------------------------------------------

    private void loadPairedDevices() {

        List<String> devicesList = new ArrayList<>();
        devicesList.add("Select Device");

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        for (BluetoothDevice device : pairedDevices) {
            devicesList.add(device.getName() + " - " + device.getAddress());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                devicesList
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);
    }


    // ------------------------------------------------------------------
    // CONNECT TO DEVICE
    // ------------------------------------------------------------------

    private void connectToSelectedDevice() {

        String selected = (String) deviceSpinner.getSelectedItem();

        if (selected == null || selected.equals("Select Device")) {
            Toast.makeText(this,
                    "Please select a device",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        int lastDash = selected.lastIndexOf("-");

        if (lastDash == -1) {
            Toast.makeText(this,
                    "Invalid device format",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        connectedDeviceName = selected.substring(0, lastDash).trim();
        connectedDeviceAddress = selected.substring(lastDash + 1).trim();

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED) {
            checkPermissions();
            return;
        }

        confirmButton.setEnabled(false);
        loadingOverlay.setVisibility(View.VISIBLE);

        retryCount = 0;
        connectWithRetry();
    }


    // ------------------------------------------------------------------
    // CONNECTION RETRY LOGIC
    // ------------------------------------------------------------------

    private void connectWithRetry() {

        new Thread(() -> {
            try {
                BluetoothDevice device =
                        bluetoothAdapter.getRemoteDevice(connectedDeviceAddress);

                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                bluetoothSocket =
                        device.createRfcommSocketToServiceRecord(SPP_UUID);

                bluetoothAdapter.cancelDiscovery();
                bluetoothSocket.connect();

                runOnUiThread(() -> {
                    Toast.makeText(this,
                            "Connected to " + connectedDeviceName,
                            Toast.LENGTH_SHORT).show();
                    loadingOverlay.setVisibility(View.GONE);
                });

                readSensorData();

                Intent intent = new Intent(
                        DeviceSetUpActivity.this,
                        HomeActivity.class
                );
                intent.putExtra("DEVICE_NAME", connectedDeviceName);
                intent.putExtra("DEVICE_ADDRESS", connectedDeviceAddress);
                startActivity(intent);
                finish();

            } catch (IOException e) {

                retryCount++;

                if (retryCount <= MAX_RETRIES) {
                    runOnUiThread(() -> Toast.makeText(
                            this,
                            "Retrying connection... (" + retryCount + ")",
                            Toast.LENGTH_SHORT
                    ).show());

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }

                    connectWithRetry();

                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(
                                this,
                                "Bluetooth connection failed. Please try again.",
                                Toast.LENGTH_LONG
                        ).show();
                        loadingOverlay.setVisibility(View.GONE);
                        confirmButton.setEnabled(true);
                        confirmButton.setText("Retry");
                    });
                }
            }

        }).start();
    }


    // ------------------------------------------------------------------
    // READ INCOMING DATA
    // ------------------------------------------------------------------

    private void readSensorData() {

        stopReading = false;

        new Thread(() -> {

            try {
                InputStream inputStream = bluetoothSocket.getInputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream)
                );

                String line;

                while (!stopReading && (line = reader.readLine()) != null) {

                    String finalLine = line;

                    runOnUiThread(() -> {
                        Log.d("BT_DATA", finalLine);

                        if (dataCallback != null) {
                            dataCallback.onDataReceived(finalLine);
                        }
                    });
                }

            } catch (IOException e) {
                if (!stopReading) {
                    Log.e("BT_READ", "Failed to read data", e);
                }
            }

        }).start();
    }


    // ------------------------------------------------------------------
    // PERMISSION RESULT
    // ------------------------------------------------------------------

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {

            boolean granted = true;

            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }

            if (granted) {
                Toast.makeText(this,
                        "Permissions granted. Press confirm again.",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "Bluetooth permission denied!",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }


    // ------------------------------------------------------------------
    // START DISCOVERY
    // ------------------------------------------------------------------

    private void startDeviceDiscovery() {

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_SCAN
        ) != PackageManager.PERMISSION_GRANTED) {
            checkPermissions();
            return;
        }

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        bluetoothAdapter.startDiscovery();

        Toast.makeText(this,
                "Scanning for devices...",
                Toast.LENGTH_SHORT).show();
    }


    // ------------------------------------------------------------------
    // CLEANUP
    // ------------------------------------------------------------------

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
