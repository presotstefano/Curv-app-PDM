package com.spstudio.curv_app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.GeoPoint;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RouteModel {
    private final String id;
    private final String nome;
    private final String descrizione;
    private final String creatoreUid;
    private final String difficulty;
    private final double distanzaKm;
    private final LatLng startPoint;
    private final List<LatLng> tracciato;
    private final String status;
    @Nullable private final String rejectionReason;
    private final double averageRating;
    private final int reviewCount;
    private final int saveCount;
    private final int likeCount;
    private final int commentCount;
    @Nullable private final String countryCode;
    @Nullable private final String staticMapUrl;
    @Nullable private final Timestamp dataCreazione; // Aggiunto per ordinamento

    // Costruttore privato, usare il factory method
    private RouteModel(String id, String nome, String descrizione, String creatoreUid,
                       String difficulty, double distanzaKm, LatLng startPoint, List<LatLng> tracciato,
                       String status, @Nullable String rejectionReason, double averageRating,
                       int reviewCount, int saveCount, int likeCount, int commentCount,
                       @Nullable String countryCode, @Nullable String staticMapUrl, @Nullable Timestamp dataCreazione) {
        this.id = id;
        this.nome = nome;
        this.descrizione = descrizione;
        this.creatoreUid = creatoreUid;
        this.difficulty = difficulty;
        this.distanzaKm = distanzaKm;
        this.startPoint = startPoint; // Aggiornato
        this.tracciato = tracciato;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.averageRating = averageRating;
        this.reviewCount = reviewCount;
        this.saveCount = saveCount;
        this.likeCount = likeCount;
        this.commentCount = commentCount;
        this.countryCode = countryCode;
        this.staticMapUrl = staticMapUrl;
        this.dataCreazione = dataCreazione;
    }

    // Factory method per creare un oggetto RouteModel da un DocumentSnapshot di Firestore
    public static RouteModel fromFirestore(@NonNull DocumentSnapshot doc) {
        Map<String, Object> data = doc.getData();
        if (data == null) {
            return new RouteModel(doc.getId(), "Error", "", "", "Easy", 0.0, new LatLng(0, 0),
                    new ArrayList<>(), "error", null, 0.0, 0, 0, 0, 0, null, null, null);
        }

        List<LatLng> points = new ArrayList<>();
        List<Map<String, Double>> tracciatoData = (List<Map<String, Double>>) data.getOrDefault("tracciato", new ArrayList<>());
        if (tracciatoData != null) {
            for (Map<String, Double> pointData : tracciatoData) {
                // Firestore salva lat/lng, Google Maps usa LatLng(lat, lng)
                if (pointData != null && pointData.containsKey("lat") && pointData.containsKey("lng")) {
                    points.add(new LatLng(pointData.get("lat"), pointData.get("lng"))); // Ordine corretto per LatLng
                }
            }
        }

        LatLng start;
        GeoPoint startGeoPoint = (GeoPoint) data.get("startPoint");
        if (startGeoPoint != null) {
            start = new LatLng(startGeoPoint.getLatitude(), startGeoPoint.getLongitude()); // Ordine corretto per LatLng
        } else if (!points.isEmpty()) {
            start = points.get(0);
        } else {
            start = new LatLng(0, 0); // Default
        }

        return new RouteModel(
                doc.getId(),
                (String) data.getOrDefault("nome", "Unnamed Route"),
                (String) data.getOrDefault("descrizione", ""),
                (String) data.getOrDefault("creatoreUid", ""),
                (String) data.getOrDefault("difficulty", "Easy"),
                ((Number) data.getOrDefault("distanzaKm", 0.0)).doubleValue(),
                start,
                points,
                (String) data.getOrDefault("status", "approved"),
                (String) data.get("rejectionReason"),
                ((Number) data.getOrDefault("averageRating", 0.0)).doubleValue(),
                ((Number) data.getOrDefault("reviewCount", 0)).intValue(),
                ((Number) data.getOrDefault("saveCount", 0)).intValue(),
                ((Number) data.getOrDefault("likeCount", 0)).intValue(),
                ((Number) data.getOrDefault("commentCount", 0)).intValue(),
                (String) data.get("countryCode"),
                (String) data.get("staticMapUrl"),
                (Timestamp) data.get("dataCreazione")
        );
    }

    // --- Getters ---
    public String getId() { return id; }
    public String getNome() { return nome; }
    public String getDescrizione() { return descrizione; }
    public String getCreatoreUid() { return creatoreUid; }
    public String getDifficulty() { return difficulty; }
    public double getDistanzaKm() { return distanzaKm; }
    public LatLng getStartPoint() { return startPoint; }
    public List<LatLng> getTracciato() { return tracciato; }
    public String getStatus() { return status; }
    @Nullable public String getRejectionReason() { return rejectionReason; }
    public double getAverageRating() { return averageRating; }
    public int getReviewCount() { return reviewCount; }
    public int getSaveCount() { return saveCount; }
    public int getLikeCount() { return likeCount; }
    public int getCommentCount() { return commentCount; }
    @Nullable public String getCountryCode() { return countryCode; }
    @Nullable public String getStaticMapUrl() { return staticMapUrl; }
    @Nullable public Timestamp getDataCreazione() { return dataCreazione; }
}