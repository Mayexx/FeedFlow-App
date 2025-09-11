package com.example.feedflow;

import android.annotation.SuppressLint;
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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    EditText usernameInput, passwordInput;
    Button loginButton;
    TextView signUpText;  // <-- Add this

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Animation bubbleAnim = AnimationUtils.loadAnimation(this, R.anim.bubble_float);


        findViewById(R.id.bubble).startAnimation(bubbleAnim);
        findViewById(R.id.bubble1).startAnimation(bubbleAnim);
        findViewById(R.id.bubble2).startAnimation(bubbleAnim);
        findViewById(R.id.bubble3).startAnimation(bubbleAnim);
        findViewById(R.id.bubble4).startAnimation(bubbleAnim);
        findViewById(R.id.bubble5).startAnimation(bubbleAnim);
        findViewById(R.id.bubble6).startAnimation(bubbleAnim);
        findViewById(R.id.bubble7).startAnimation(bubbleAnim);
        findViewById(R.id.bubble8).startAnimation(bubbleAnim);
        findViewById(R.id.bubble9).startAnimation(bubbleAnim);
        findViewById(R.id.bubble10).startAnimation(bubbleAnim);
        findViewById(R.id.bubble11).startAnimation(bubbleAnim);
        findViewById(R.id.bubble12).startAnimation(bubbleAnim);
        findViewById(R.id.bubble13).startAnimation(bubbleAnim);
        findViewById(R.id.bubble_top_right).startAnimation(bubbleAnim);

        // Initialize views
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        signUpText = findViewById(R.id.signUpText);  // <-- Add this

        // Handle login button click
        loginButton.setOnClickListener(v -> {
            String username = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Please enter both username and password.", Toast.LENGTH_SHORT).show();
                return;
            }
            // Passed validation
            Toast.makeText(LoginActivity.this, "Logging in...", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginActivity.this, BluetoothSetUpActivity.class));
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
        signUpText.setHighlightColor(Color.TRANSPARENT);
    }
}
