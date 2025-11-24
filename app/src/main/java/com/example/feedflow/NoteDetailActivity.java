package com.example.feedflow;


import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;


import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;


public class NoteDetailActivity extends AppCompatActivity {


    private TextView tvDetail, tvTitle;
    private Button btnClose;
    private FirebaseFirestore db;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);


        tvDetail = findViewById(R.id.tvDetail);
        tvTitle = findViewById(R.id.tvTitle);
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
        db.collection("notes").document(noteId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String detailText = "Dead Fish: " + doc.getString("deadFish") +
                                "\nTemperature: " + doc.getString("temperature") + "°C" +
                                "\nWeather: " + doc.getString("weather") +
                                "\nFeeding: " + doc.getString("feedingTime") +
                                "\nAmount: " + doc.getString("amount") + "kg" +
                                "\nBehaviour: " + doc.getString("behaviour") +
                                "\nNotes: " + doc.getString("notes");


                        tvDetail.setText(detailText);
                    } else {
                        tvDetail.setText("Note not found.");
                    }
                })
                .addOnFailureListener(e -> tvDetail.setText("Failed to load note details."));
    }
}
