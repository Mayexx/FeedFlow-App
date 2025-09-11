package com.example.feedflow;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class NoteDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        TextView tvDetail = findViewById(R.id.tvDetail);
        Button btnClose = findViewById(R.id.btnClose);

        // Get note text from intent
        String noteText = getIntent().getStringExtra("note_text");
        if (noteText != null) {
            tvDetail.setText(noteText);
        }

        // Close button logic
        btnClose.setOnClickListener(v -> finish());
    }
}
