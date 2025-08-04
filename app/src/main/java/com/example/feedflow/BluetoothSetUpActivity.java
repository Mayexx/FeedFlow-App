package com.example.feedflow;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class BluetoothSetUpActivity extends AppCompatActivity {

    Button buttonSetup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetoothsetup); // Make sure this is the correct layout

        // Correct way to reference the button by ID
        buttonSetup = findViewById(R.id.buttonSetup);

        buttonSetup.setOnClickListener(v -> {
            Intent intent = new Intent(BluetoothSetUpActivity.this, ConnectDeviceActivity.class);
            startActivity(intent);
        });
    }
}
