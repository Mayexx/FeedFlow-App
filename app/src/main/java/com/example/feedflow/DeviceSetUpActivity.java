package com.example.feedflow;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

    private String connectedDeviceAddress;
    private String connectedDeviceName;

    private Spinner deviceSpinner;
    private Button confirmButton;

    // SPP UUID for most Bluetooth modules (HC-05, HC-06, ESP32)
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devicesetup);

        deviceSpinner = findViewById(R.id.deviceSpinner);
        confirmButton = findViewById(R.id.confirmButton);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show();
            finish();
        }

        checkPermissions();
        loadPairedDevices();

        confirmButton.setOnClickListener(v -> connectToSelectedDevice());
    }

    // ✅ Check runtime permissions
    private void checkPermissions() {
        List<String> permissions = new ArrayList<>();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    permissions.toArray(new String[0]),
                    REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    // ✅ Load paired devices into spinner
    private void loadPairedDevices() {
        List<String> devicesList = new ArrayList<>();
        devicesList.add("Select Device");

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            devicesList.add(device.getName() + " - " + device.getAddress());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, devicesList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);
    }

    // ✅ Connect to the selected device
    private void connectToSelectedDevice() {
        String selected = (String) deviceSpinner.getSelectedItem();
        if (selected == null || selected.equals("Select Device")) {
            Toast.makeText(this, "Please select a device", Toast.LENGTH_SHORT).show();
            return;
        }

        // Extract name and MAC address
        if (selected.contains("-")) {
            String[] parts = selected.split("-");
            connectedDeviceName = parts[0].trim();
            connectedDeviceAddress = parts[1].trim();
        } else {
            Toast.makeText(this, "Invalid device format", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check permissions before connecting
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth permission missing!", Toast.LENGTH_SHORT).show();
            checkPermissions();
            return;
        }

        // Connect in background thread
        new Thread(() -> {
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(connectedDeviceAddress);
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothAdapter.cancelDiscovery();
                bluetoothSocket.connect();

                runOnUiThread(() -> Toast.makeText(this,
                        "Connected to " + connectedDeviceName, Toast.LENGTH_SHORT).show());

                // Start reading data
                readSensorData();

                // Navigate to HomeActivity
                Intent intent = new Intent(DeviceSetUpActivity.this, HomeActivity.class);
                intent.putExtra("DEVICE_NAME", connectedDeviceName);
                intent.putExtra("DEVICE_ADDRESS", connectedDeviceAddress);
                startActivity(intent);
                finish();

            } catch (IOException e) {
                Log.e("BT_CONNECT", "Connection failed", e);
                runOnUiThread(() -> Toast.makeText(this,
                        "Bluetooth connection failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ✅ Read data continuously from ESP32
    private void readSensorData() {
        new Thread(() -> {
            try {
                InputStream inputStream = bluetoothSocket.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;

                while ((line = reader.readLine()) != null) {
                    String finalLine = line;
                    runOnUiThread(() -> {
                        // Example: display in Toast or send to HomeActivity via Intent/LiveData
                        Log.d("BT_DATA", finalLine);
                        // Toast.makeText(this, finalLine, Toast.LENGTH_SHORT).show(); // optional
                    });

                    // Optional: send to Firebase
                    // FirebaseDatabase.getInstance().getReference("esp32Data").setValue(finalLine);
                }
            } catch (IOException e) {
                Log.e("BT_READ", "Failed to read data", e);
            }
        }).start();
    }

    // ✅ Handle permission result
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
                Toast.makeText(this, "Permissions granted. Press confirm again.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Bluetooth permission denied!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ✅ Disconnect safely
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
