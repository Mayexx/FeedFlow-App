package com.example.feedflow;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    EditText numberInput, passwordInput;
    Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize views
        numberInput = findViewById(R.id.numberInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);

        // Handle button click
        loginButton.setOnClickListener(v -> {
            String number = numberInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (number.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Please enter both number and password.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(LoginActivity.this, "Logging in...", Toast.LENGTH_SHORT).show();
                // Redirect to dashboard or next activity
                // startActivity(new Intent(LoginActivity.this, DashboardActivity.class));
            }
        });
    }
}

