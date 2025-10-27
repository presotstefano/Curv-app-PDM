package com.spstudio.curv_app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.Map;

public class EventModel {
    private final String id;
    private final String name;
    @Nullable private final String description;
    @Nullable private final String bannerUrl;
    private final String routeId;
    @Nullable private final String routeName; // Campo denormalizzato per comodità
    private final double routeDistanceKm; // Campo denormalizzato per comodità
    private final String createdByUid;
    @Nullable private final String createdByName; // Denormalizzato
    @Nullable private final Timestamp dateTime;
    private final int participantCount;
    private final boolean reminderSent; // Campo aggiunto dalle Cloud Functions

    private EventModel(String id, String name, @Nullable String description, @Nullable String bannerUrl,
                       String routeId, @Nullable String routeName, double routeDistanceKm,
                       String createdByUid, @Nullable String createdByName,
                       @Nullable Timestamp dateTime, int participantCount, boolean reminderSent) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.bannerUrl = bannerUrl;
        this.routeId = routeId;
        this.routeName = routeName;
        this.routeDistanceKm = routeDistanceKm;
        this.createdByUid = createdByUid;
        this.createdByName = createdByName;
        this.dateTime = dateTime;
        this.participantCount = participantCount;
        this.reminderSent = reminderSent;
    }

    @NonNull
    public static EventModel fromFirestore(@NonNull DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) {
            return new EventModel(doc.getId(), "Error", null, null, "", null, 0.0, "", null, null, 0, false);
        }
        return new EventModel(
                doc.getId(),
                (String) data.getOrDefault("name", "Unnamed Event"),
                (String) data.get("description"),
                (String) data.get("bannerUrl"),
                (String) data.getOrDefault("routeId", ""),
                (String) data.get("routeName"), // Denormalizzato
                ((Number) data.getOrDefault("routeDistanceKm", 0.0)).doubleValue(), // Denormalizzato
                (String) data.getOrDefault("createdByUid", ""),
                (String) data.get("createdByName"), // Denormalizzato
                (Timestamp) data.get("dateTime"),
                ((Number) data.getOrDefault("participantCount", 0)).intValue(), // Contatore aggiornato da Function
                (Boolean) data.getOrDefault("reminderSent", false) // Gestito da Function
        );
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    @Nullable public String getDescription() { return description; }
    @Nullable public String getBannerUrl() { return bannerUrl; }
    public String getRouteId() { return routeId; }
    @Nullable public String getRouteName() { return routeName; }
    public double getRouteDistanceKm() { return routeDistanceKm; }
    public String getCreatedByUid() { return createdByUid; }
    @Nullable public String getCreatedByName() { return createdByName != null ? createdByName : "A User"; }
    @Nullable public Timestamp getDateTime() { return dateTime; }
    public int getParticipantCount() { return participantCount; }
    public boolean isReminderSent() { return reminderSent; }
}