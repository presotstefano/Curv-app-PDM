package com.spstudio.curv_app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit; // Per formattare il tempo

public class TimeEntry {
    private final String id;
    private final String routeId;
    private final String userId;
    @Nullable private final String userName;
    private final long tempoMs;
    @Nullable private final Timestamp dataRegistrazione;

    // Costruttore privato
    private TimeEntry(String id, String routeId, String userId, @Nullable String userName, long tempoMs, @Nullable Timestamp dataRegistrazione) {
        this.id = id;
        this.routeId = routeId;
        this.userId = userId;
        this.userName = userName;
        this.tempoMs = tempoMs;
        this.dataRegistrazione = dataRegistrazione;
    }

    // Factory method
    @NonNull
    public static TimeEntry fromFirestore(@NonNull DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) {
            // Ritorna un oggetto di default o lancia eccezione
            return new TimeEntry(doc.getId(), "", "", "Error", 0, null);
        }

        return new TimeEntry(
                doc.getId(),
                (String) data.getOrDefault("routeId", ""),
                (String) data.getOrDefault("userId", ""),
                (String) data.get("userName"), // Può essere null
                ((Number) data.getOrDefault("tempoMs", 0L)).longValue(),
                (Timestamp) data.get("dataRegistrazione")
        );
    }

    // Getters
    public String getId() { return id; }
    public String getRouteId() { return routeId; }
    public String getUserId() { return userId; }
    @Nullable public String getUserName() { return userName != null ? userName : "Anonymous"; } // Fallback
    public long getTempoMs() { return tempoMs; }
    @Nullable public Timestamp getDataRegistrazione() { return dataRegistrazione; }

    // Metodo helper per formattare il tempo
    public String getFormattedTime() {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(tempoMs);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(tempoMs) % 60;
        long hundreds = (tempoMs / 10) % 100; // Calcola i centesimi di secondo

        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundreds);
    }
}