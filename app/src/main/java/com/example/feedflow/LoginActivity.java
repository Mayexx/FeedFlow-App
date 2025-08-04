package com.example.feedflow;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.TextPaint;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    EditText numberInput, passwordInput;
    Button loginButton;
    TextView signUpText;  // <-- Add this

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize views
        numberInput = findViewById(R.id.numberInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        signUpText = findViewById(R.id.signUpText);  // <-- Add this

        // Handle login button click
        loginButton.setOnClickListener(v -> {
            String number = numberInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (number.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Please enter both number and password.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(LoginActivity.this, "Logging in...", Toast.LENGTH_SHORT).show();
                 startActivity(new Intent(LoginActivity.this, BluetoothSetUpActivity.class));
            }
        });

        SpannableString spannable = new SpannableString("Don’t have an account? Sign up");
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                startActivity(new Intent(LoginActivity.this, SignUpActivity.class));
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.parseColor("#4894FE")); // Blue
                ds.setUnderlineText(false); // No underline
            }
        };
        spannable.setSpan(clickableSpan, 23, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        signUpText.setText(spannable);
        signUpText.setMovementMethod(LinkMovementMethod.getInstance());
        signUpText.setHighlightColor(Color.TRANSPARENT); // Optional
    }
}
