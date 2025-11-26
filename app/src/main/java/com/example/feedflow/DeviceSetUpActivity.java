package com.example.feedflow;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Set;

public class DeviceSetUpActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private Spinner deviceSpinner;
    private ArrayAdapter<String> deviceListAdapter;
    private final int REQUEST_ENABLE_BT = 100;

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devicesetup);

        deviceSpinner = findViewById(R.id.deviceSpinner);
        Button cancelButton = findViewById(R.id.cancelButton);
        Button confirmButton = findViewById(R.id.confirmButton);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Request permissions for Android 12+
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                    }, 1);
        }

        // Enable Bluetooth if not already
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            loadPairedDevices();
        }

        // Cancel button closes the activity
        cancelButton.setOnClickListener(v -> finish());

        // Confirm button checks if ESP32 is selected
        confirmButton.setOnClickListener(v -> {
            String selectedDevice = (String) deviceSpinner.getSelectedItem();

            if (selectedDevice != null && selectedDevice.contains("ESP32")) {
                // Device is ESP32 → proceed
                Intent intent = new Intent(DeviceSetUpActivity.this, HomeActivity.class);
                startActivity(intent);
            } else {
                // Not ESP32 → show warning
                Toast.makeText(DeviceSetUpActivity.this, "Connect Your ESP32", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Load paired devices into spinner
    private void loadPairedDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayList<String> deviceNames = new ArrayList<>();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                deviceNames.add(device.getName() + " (" + device.getAddress() + ")");
            }
        } else {
            deviceNames.add("No paired devices");
        }

        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, deviceNames);
        deviceSpinner.setAdapter(deviceListAdapter);
    }
}
