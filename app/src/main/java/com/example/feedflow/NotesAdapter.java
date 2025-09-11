package com.example.feedflow;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    private List<String> notesList;
    private Context context;
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "FeedFlowNotes";
    private static final String NOTES_KEY = "saved_notes";

    public NotesAdapter(Context context, List<String> notesList) {
        this.context = context;
        this.notesList = new ArrayList<>(notesList);
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        String note = notesList.get(position);
        holder.tvNote.setText(note);

        // Open note detail on click
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, NoteDetailActivity.class);
            intent.putExtra("note_text", note);
            context.startActivity(intent);
        });

        // Delete button logic
        holder.btnDelete.setOnClickListener(v -> {
            notesList.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, notesList.size());

            Set<String> updatedSet = new HashSet<>(notesList);
            sharedPreferences.edit().putStringSet(NOTES_KEY, updatedSet).apply();
        });
    }

    @Override
    public int getItemCount() {
        return notesList.size();
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvNote;
        Button btnDelete;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNote = itemView.findViewById(R.id.tvNote);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
