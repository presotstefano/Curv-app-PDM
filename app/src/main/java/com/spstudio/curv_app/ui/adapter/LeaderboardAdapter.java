package com.spstudio.curv_app.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast; // Per placeholder navigazione

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.TimeEntry;
import com.spstudio.curv_app.ui.activity.UserProfileActivity;
// import com.spstudio.curv_app.ui.activity.UserProfileActivity; // Da creare

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder> {

    private final List<TimeEntry> timeEntries;
    private final Context context; // Serve per avviare l'Activity del profilo

    public LeaderboardAdapter(Context context, List<TimeEntry> timeEntries) {
        this.context = context;
        this.timeEntries = timeEntries != null ? timeEntries : new ArrayList<>();
    }

    @NonNull
    @Override
    public LeaderboardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_leaderboard, parent, false);
        return new LeaderboardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LeaderboardViewHolder holder, int position) {
        TimeEntry entry = timeEntries.get(position);
        holder.bind(entry, position + 1);

        holder.itemView.setOnClickListener(v -> {
            String userId = entry.getUserId();
            if (userId != null && !userId.isEmpty()) {
                // === SCOMMENTA E VERIFICA QUESTE RIGHE ===
                Intent intent = new Intent(context, UserProfileActivity.class);
                intent.putExtra("USER_ID", userId); // Passa l'ID dell'utente della classifica
                context.startActivity(intent);
                // Toast.makeText(context, "Go to profile: " + entry.getUserName(), Toast.LENGTH_SHORT).show(); // Rimuovi Toast
                // === FINE MODIFICA ===
            } else {
                Toast.makeText(context, "Cannot navigate to profile: User ID is missing.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return timeEntries.size();
    }

    // ViewHolder interno
    static class LeaderboardViewHolder extends RecyclerView.ViewHolder {
        TextView rankTextView;
        TextView usernameTextView;
        TextView timeTextView;

        public LeaderboardViewHolder(@NonNull View itemView) {
            super(itemView);
            rankTextView = itemView.findViewById(R.id.rankTextView);
            usernameTextView = itemView.findViewById(R.id.usernameTextView);
            timeTextView = itemView.findViewById(R.id.timeTextView);
        }

        public void bind(TimeEntry entry, int rank) {
            rankTextView.setText(String.format(Locale.US, "#%d", rank));
            usernameTextView.setText(entry.getUserName()); // Il getter ha già il fallback "Anonymous"
            timeTextView.setText(entry.getFormattedTime());
        }
    }
}