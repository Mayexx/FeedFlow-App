package com.example.feedflow;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

public class NoteDetailActivity extends AppCompatActivity {

    private TextView tvDeadFish, tvTemperature, tvWeather, tvFeedingTime, tvAmount, tvBehaviour, tvNotes;
    private Button btnClose;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        // Initialize views
        tvDeadFish = findViewById(R.id.tvDeadFish);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvWeather = findViewById(R.id.tvWeather);
        tvFeedingTime = findViewById(R.id.tvFeedingTime);
        tvAmount = findViewById(R.id.tvAmount);
        tvBehaviour = findViewById(R.id.tvBehaviour);
        tvNotes = findViewById(R.id.tvNotes);
        btnClose = findViewById(R.id.btnClose);

        db = FirebaseFirestore.getInstance();

        String noteId = getIntent().getStringExtra("note_id");
        if (noteId != null) {
            loadNoteDetail(noteId);
        } else {
            Toast.makeText(this, "No note found", Toast.LENGTH_SHORT).show();
        }

        btnClose.setOnClickListener(v -> finish());
    }

    private void loadNoteDetail(String noteId) {
        db.collection("FeedFlow")
                .document("Device001")
                .collection("notes")
                .document(noteId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        tvDeadFish.setText(doc.getString("deadFish") != null ? doc.getString("deadFish") : "-");
                        tvTemperature.setText(doc.getString("temperature") != null ? doc.getString("temperature") + "°C" : "-");
                        tvWeather.setText(doc.getString("weather") != null ? doc.getString("weather") : "-");
                        tvFeedingTime.setText(doc.getString("feedingTime") != null ? doc.getString("feedingTime") : "-");
                        tvAmount.setText(doc.getString("amount") != null ? doc.getString("amount") + " kg" : "-");
                        tvBehaviour.setText(doc.getString("behaviour") != null ? doc.getString("behaviour") : "-");
                        tvNotes.setText(doc.getString("notes") != null ? doc.getString("notes") : "-");
                    } else {
                        Toast.makeText(this, "Note not found", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load note", Toast.LENGTH_SHORT).show());
    }

}
