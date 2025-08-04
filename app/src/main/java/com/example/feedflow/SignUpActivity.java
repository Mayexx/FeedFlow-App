package com.example.feedflow;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.TextPaint;
import android.graphics.Color;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class SignUpActivity extends AppCompatActivity {

    EditText numberInput, passwordInput, confirmPasswordInput;
    Button signUpButton;
    CheckBox termsCheckBox;
    TextView loginRedirect; // Added for clickable "Log in"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup); // Ensure the XML file name matches

        // Initialize views
        numberInput = findViewById(R.id.numberInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        signUpButton = findViewById(R.id.signUpButton);
        termsCheckBox = findViewById(R.id.termsCheckBox);
        loginRedirect = findViewById(R.id.loginRedirect); // For clickable link

        // Sign up button logic
        signUpButton.setOnClickListener(v -> {
            String number = numberInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();
            String confirmPassword = confirmPasswordInput.getText().toString().trim();

            if (number.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(SignUpActivity.this, "All fields are required", Toast.LENGTH_SHORT).show();
            } else if (!password.equals(confirmPassword)) {
                Toast.makeText(SignUpActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            } else if (!termsCheckBox.isChecked()) {
                Toast.makeText(SignUpActivity.this, "Please agree to the terms and conditions", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(SignUpActivity.this, "Signed up successfully!", Toast.LENGTH_SHORT).show();
                // Redirect if needed
                // startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
                // finish();
            }
        });

        // Make "Log in" clickable and blue
        SpannableString spannable = new SpannableString("Already have an account? Log in");
        ClickableSpan loginClick = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                startActivity(new Intent(SignUpActivity.this, LoginActivity.class));
                finish();
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setColor(Color.parseColor("#4894FE")); // Blue color
                ds.setUnderlineText(false); // Optional
            }
        };

        spannable.setSpan(loginClick, 25, spannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        loginRedirect.setText(spannable);
        loginRedirect.setMovementMethod(LinkMovementMethod.getInstance());
        loginRedirect.setHighlightColor(Color.TRANSPARENT);
    }
}
