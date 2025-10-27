package com.spstudio.curv_app.ui.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText; // Importa EditText
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CustomCap; // Per estremità polilinea
import com.google.android.gms.maps.model.JointType; // Per giunzioni polilinea
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.RoundCap; // Per estremità polilinea
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.databinding.ActivityCreateRouteBinding; // Assicurati sia corretto
import com.spstudio.curv_app.services.SettingsService; // Per formattare distanza

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateRouteActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "CreateRouteActivity";

    private ActivityCreateRouteBinding binding;
    private GoogleMap googleMap;
    private SupportMapFragment mapFragment;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private SettingsService settingsService; // Per formattare la distanza nel dialogo

    private final List<LatLng> waypoints = new ArrayList<>();
    private final List<Marker> waypointMarkers = new ArrayList<>();
    private Polyline routePolyline; // Oggetto per la linea sulla mappa

    // Per la posizione utente
    private FusedLocationProviderClient fusedLocationProviderClient;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    enableMyLocation();
                    centerMapOnUserLocation(true); // Centra dopo aver ottenuto il permesso
                } else {
                    Toast.makeText(this, R.string.home_location_permission_denied, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateRouteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        settingsService = SettingsService.getInstance(this); // Ottieni istanza
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Ottieni il SupportMapFragment e registra il callback
        FragmentManager fm = getSupportFragmentManager();
        mapFragment = (SupportMapFragment) fm.findFragmentById(R.id.map_container_create);
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            fm.beginTransaction().replace(R.id.map_container_create, mapFragment).commit();
        }
        mapFragment.getMapAsync(this); // Chiama onMapReady

        setupButtonClickListeners();
        updateInfoBanner(); // Imposta testo iniziale banner
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        Log.d(TAG, "GoogleMap is ready for route creation.");
        this.googleMap = map;

        // Configura interazioni e UI
        googleMap.getUiSettings().setMapToolbarEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.getUiSettings().setCompassEnabled(true); // Utile per orientarsi

        // Listener per i click sulla mappa
        googleMap.setOnMapClickListener(latLng -> {
            addWaypoint(latLng);
            // TODO: Idealmente qui dovresti chiamare Directions API per ricalcolare il percorso
            drawRoute(); // Per ora disegna linee dirette
        });

        enableMyLocation();
        centerMapOnUserLocation(true); // Centra all'avvio
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (googleMap != null && hasLocationPermission()) {
            googleMap.setMyLocationEnabled(true);
        }
    }

    private void setupButtonClickListeners() {
        binding.backButton.setOnClickListener(v -> finish()); // Chiude l'activity
        binding.locationButton.setOnClickListener(v -> centerMapOnUserLocation(false));
        binding.undoButton.setOnClickListener(v -> undoLastWaypoint());
        binding.clearButton.setOnClickListener(v -> clearAllWaypoints());
        binding.saveButton.setOnClickListener(v -> showSaveDialog());
    }

    private void addWaypoint(LatLng latLng) {
        waypoints.add(latLng);

        Marker marker = googleMap.addMarker(new MarkerOptions()
                .position(latLng)
                // Puoi personalizzare l'icona del marker qui se vuoi
                // .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_waypoint_marker))
                .anchor(0.5f, 0.5f)); // Centra icona

        if (marker != null) {
            waypointMarkers.add(marker);
        }
        updateInfoBanner();
    }

    // Disegna (o ridisegna) la polilinea sulla mappa
    private void drawRoute() {
        if (googleMap == null) return;

        // Rimuovi la vecchia polilinea se esiste
        if (routePolyline != null) {
            routePolyline.remove();
        }

        if (waypoints.size() >= 2) {
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(waypoints)
                    .color(ContextCompat.getColor(this, R.color.primaryColor)) // Usa il colore primario
                    .width(10) // Larghezza linea in pixel
                    .jointType(JointType.ROUND) // Giunzioni arrotondate
                    .startCap(new RoundCap()) // Estremità arrotondate
                    .endCap(new RoundCap());

            routePolyline = googleMap.addPolyline(polylineOptions);
        } else {
            routePolyline = null; // Nessuna linea se ci sono meno di 2 punti
        }
    }


    private void undoLastWaypoint() {
        if (!waypoints.isEmpty()) {
            waypoints.remove(waypoints.size() - 1);
            if (!waypointMarkers.isEmpty()) {
                Marker lastMarker = waypointMarkers.remove(waypointMarkers.size() - 1);
                lastMarker.remove(); // Rimuovi il marker dalla mappa
            }
            drawRoute(); // Ridisegna la polilinea senza l'ultimo punto
            updateInfoBanner();
        }
    }

    private void clearAllWaypoints() {
        waypoints.clear();
        for (Marker marker : waypointMarkers) {
            marker.remove();
        }
        waypointMarkers.clear();
        if (routePolyline != null) {
            routePolyline.remove();
            routePolyline = null;
        }
        updateInfoBanner();
    }

    // Aggiorna il testo nel banner superiore
    private void updateInfoBanner() {
        if (waypoints.isEmpty()) {
            binding.infoBannerText.setText(R.string.create_route_banner_tap_to_start);
        } else {
            binding.infoBannerText.setText(R.string.create_route_banner_add_more_points);
        }
    }


    // Mostra il dialogo per salvare il percorso
    private void showSaveDialog() {
        if (waypoints.size() < 2) {
            Toast.makeText(this, R.string.error_min_waypoints, Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_save_route, null); // Crea questo layout
        builder.setView(dialogView);

        // Trova le viste nel layout del dialogo
        TextView distanceTextView = dialogView.findViewById(R.id.distanceTextView);
        EditText nameEditText = dialogView.findViewById(R.id.routeNameEditText);
        EditText descriptionEditText = dialogView.findViewById(R.id.routeDescriptionEditText);
        AutoCompleteTextView difficultyDropdown = dialogView.findViewById(R.id.difficultyDropdown);

        // Calcola e mostra la distanza (approssimata per ora)
        double totalDistanceKm = calculateApproxDistanceKm();
        distanceTextView.setText(getString(R.string.save_route_dialog_distance,
                settingsService.formatDistance(totalDistanceKm)));

        // Configura il dropdown della difficoltà
        String[] difficulties = {
                getString(R.string.save_route_dialog_difficulty_easy),
                getString(R.string.save_route_dialog_difficulty_medium),
                getString(R.string.save_route_dialog_difficulty_hard)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, difficulties);
        difficultyDropdown.setAdapter(adapter);
        difficultyDropdown.setText(difficulties[0], false); // Seleziona "Easy" di default

        builder.setTitle(R.string.save_route_dialog_title)
                .setPositiveButton(R.string.dialog_save_button, null) // Gestito manualmente per validazione
                .setNegativeButton(R.string.dialog_cancel_button, (dialog, id) -> dialog.dismiss());

        AlertDialog dialog = builder.create();

        // Sovrascrivi il listener del pulsante positivo per fare la validazione prima di chiudere
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String name = nameEditText.getText().toString().trim();
                String description = descriptionEditText.getText().toString().trim();
                String difficulty = difficultyDropdown.getText().toString(); // Ottiene il testo selezionato

                if (name.isEmpty()) {
                    // Mostra errore direttamente nel dialogo se possibile, o usa Toast
                    Toast.makeText(this, R.string.error_route_name_empty, Toast.LENGTH_SHORT).show();
                    return; // Non chiudere il dialogo
                }

                // Se valido, procedi con il salvataggio
                saveRouteToFirestore(name, description, difficulty, totalDistanceKm);
                dialog.dismiss(); // Chiudi il dialogo
            });
        });

        dialog.show();
    }

    // Calcola la distanza approssimativa sommando le distanze tra waypoint consecutivi
    private double calculateApproxDistanceKm() {
        double totalDistanceMeters = 0;
        if (waypoints.size() >= 2) {
            for (int i = 0; i < waypoints.size() - 1; i++) {
                LatLng p1 = waypoints.get(i);
                LatLng p2 = waypoints.get(i + 1);
                float[] results = new float[1];
                Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results);
                totalDistanceMeters += results[0];
            }
        }
        return totalDistanceMeters / 1000.0;
    }

    // Salva i dati del percorso su Firestore
    private void saveRouteToFirestore(String name, String description, String difficulty, double distanceKm) {
        FirebaseUser user = auth.getCurrentUser();
        String preferredCountry = settingsService.getPreferredCountry(); // Prendi il paese dalle impostazioni

        if (user == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (waypoints.isEmpty()) {
            Toast.makeText(this, "No waypoints added.", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBarCreate.setVisibility(View.VISIBLE); // Mostra caricamento

        // Converti List<LatLng> in List<Map<String, Double>> per Firestore
        List<Map<String, Double>> tracciatoForFirestore = new ArrayList<>();
        for (LatLng latLng : waypoints) {
            Map<String, Double> pointMap = new HashMap<>();
            pointMap.put("lat", latLng.latitude);
            pointMap.put("lng", latLng.longitude);
            tracciatoForFirestore.add(pointMap);
        }

        Map<String, Object> routeData = new HashMap<>();
        routeData.put("nome", name);
        routeData.put("descrizione", description);
        routeData.put("difficulty", difficulty);
        routeData.put("distanzaKm", distanceKm);
        routeData.put("creatoreUid", user.getUid());
        routeData.put("dataCreazione", com.google.firebase.Timestamp.now());
        routeData.put("tracciato", tracciatoForFirestore);
        routeData.put("startPoint", new GeoPoint(waypoints.get(0).latitude, waypoints.get(0).longitude));
        routeData.put("status", "pending"); // Stato iniziale
        routeData.put("averageRating", 0.0);
        routeData.put("reviewCount", 0);
        routeData.put("saveCount", 0);
        routeData.put("likeCount", 0);
        routeData.put("commentCount", 0);
        if (preferredCountry != null) {
            routeData.put("countryCode", preferredCountry); // Aggiungi il codice paese
        }
        // staticMapUrl verrà generato dalle Cloud Functions dopo l'approvazione

        db.collection("percorsi").add(routeData)
                .addOnSuccessListener(documentReference -> {
                    binding.progressBarCreate.setVisibility(View.GONE);
                    Log.d(TAG, "Route saved with ID: " + documentReference.getId());
                    Toast.makeText(this, R.string.route_save_success, Toast.LENGTH_LONG).show();
                    finish(); // Torna alla schermata precedente (MainActivity/HomeFragment)
                })
                .addOnFailureListener(e -> {
                    binding.progressBarCreate.setVisibility(View.GONE);
                    Log.e(TAG, "Error saving route", e);
                    Toast.makeText(this, R.string.route_save_failed, Toast.LENGTH_SHORT).show();
                });
    }

    // --- Gestione Permessi e Posizione ---
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @SuppressLint("MissingPermission")
    private void centerMapOnUserLocation(boolean initialLoad) {
        if (!hasLocationPermission()) {
            if (!initialLoad) requestLocationPermission();
            else Log.d(TAG, "Location permission denied on initial load.");
            return;
        }
        fusedLocationProviderClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null && googleMap != null) {
                        Log.d(TAG, "Centering map on user location.");
                        googleMap.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(location.getLatitude(), location.getLongitude()),
                                        initialLoad ? 12.0f : 15.0f // Zoom diverso
                                ), 1000, null);
                    } else {
                        Log.w(TAG, "Could not get last known location or map not ready for centering.");
                        if (!initialLoad) Toast.makeText(this, "Could not get current location", Toast.LENGTH_SHORT).show();
                    }
                });
    }

}