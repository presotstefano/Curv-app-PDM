package com.spstudio.curv_app.ui.activity;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.DatePicker;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp; // Importa Timestamp
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.RouteModel; // Importa RouteModel
import com.spstudio.curv_app.databinding.ActivityCreateEventBinding;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateEventActivity extends AppCompatActivity {

    private static final String TAG = "CreateEventActivity";
    private ActivityCreateEventBinding binding;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseStorage storage;
    private FirebaseUser currentUser;

    private String routeId;
    private String routeName;
    private double routeDistanceKm; // Per denormalizzazione

    private Calendar selectedDateTime = Calendar.getInstance(); // Memorizza data e ora selezionate
    private Uri bannerImageUri; // URI dell'immagine banner selezionata
    private boolean isLoading = false;

    // Image Picker
    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    bannerImageUri = uri;
                    binding.bannerImageView.setVisibility(View.VISIBLE);
                    Glide.with(this).load(bannerImageUri).centerCrop().into(binding.bannerImageView);
                    binding.addBannerButton.setText("Change Banner Image"); // Cambia testo pulsante
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCreateEventBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        currentUser = auth.getCurrentUser();

        // Setup Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Recupera dati dall'Intent
        routeId = getIntent().getStringExtra("ROUTE_ID");
        routeName = getIntent().getStringExtra("ROUTE_NAME");

        if (routeId == null || routeName == null) {
            Log.e(TAG, "Route ID or Name missing in Intent.");
            Toast.makeText(this, "Error: Route information missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        binding.routeNameTextView.setText(routeName);
        fetchRouteDistance(); // Recupera la distanza per denormalizzazione

        setupDateTimePickers();
        setupListeners();
    }

    // Recupera la distanza del percorso da Firestore
    private void fetchRouteDistance() {
        db.collection("percorsi").document(routeId).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        RouteModel route = RouteModel.fromFirestore(doc);
                        routeDistanceKm = route.getDistanzaKm();
                    } else {
                        Log.w(TAG, "Route document not found while fetching distance: " + routeId);
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error fetching route distance", e));
    }


    private void setupDateTimePickers() {
        // Imposta l'ora corrente + 1 ora come default
        selectedDateTime.add(Calendar.HOUR_OF_DAY, 1);
        selectedDateTime.set(Calendar.MINUTE, 0); // Arrotonda all'ora
        updateDateEditText();
        updateTimeEditText();

        binding.dateEditText.setOnClickListener(v -> showDatePicker());
        binding.timeEditText.setOnClickListener(v -> showTimePicker());
    }

    private void showDatePicker() {
        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDateTime.set(Calendar.YEAR, year);
                    selectedDateTime.set(Calendar.MONTH, month);
                    selectedDateTime.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    updateDateEditText();
                },
                selectedDateTime.get(Calendar.YEAR),
                selectedDateTime.get(Calendar.MONTH),
                selectedDateTime.get(Calendar.DAY_OF_MONTH)
        );
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000); // Impedisce date passate
        datePickerDialog.show();
    }

    private void showTimePicker() {
        TimePickerDialog timePickerDialog = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    selectedDateTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedDateTime.set(Calendar.MINUTE, minute);
                    updateTimeEditText();
                },
                selectedDateTime.get(Calendar.HOUR_OF_DAY),
                selectedDateTime.get(Calendar.MINUTE),
                false // Usa formato 12 ore AM/PM o 24 ore in base alle impostazioni locali
        );
        timePickerDialog.show();
    }

    private void updateDateEditText() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        binding.dateEditText.setText(dateFormat.format(selectedDateTime.getTime()));
    }

    private void updateTimeEditText() {
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault()); // Es: 03:00 PM
        binding.timeEditText.setText(timeFormat.format(selectedDateTime.getTime()));
    }


    private void setupListeners() {
        binding.addBannerButton.setOnClickListener(v -> mGetContent.launch("image/*"));
        binding.publishEventButton.setOnClickListener(v -> publishEvent());
    }

    private void publishEvent() {
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isLoading) return;

        String eventName = binding.eventNameEditText.getText().toString().trim();
        String description = binding.eventDescriptionEditText.getText().toString().trim();
        Timestamp eventTimestamp = new Timestamp(selectedDateTime.getTime());

        // Validazione
        boolean valid = true;
        if (TextUtils.isEmpty(eventName)) {
            binding.eventNameInputLayout.setError(getString(R.string.error_event_name_empty));
            valid = false;
        } else {
            binding.eventNameInputLayout.setError(null);
        }
        // Controlla che la data/ora selezionata sia nel futuro
        if (eventTimestamp.toDate().before(new Date())) {
            Toast.makeText(this, "Event date and time must be in the future.", Toast.LENGTH_SHORT).show();
            // Potresti evidenziare i campi data/ora
            valid = false;
        }

        if (!valid) return;

        setLoading(true);
        Toast.makeText(this, R.string.event_saving, Toast.LENGTH_SHORT).show();

        // Se è stata selezionata un'immagine, caricala prima
        if (bannerImageUri != null) {
            uploadBannerAndSaveEvent(eventName, description, eventTimestamp);
        } else {
            saveEventToFirestore(eventName, description, eventTimestamp, null);
        }
    }

    // Carica immagine banner su Storage
    private void uploadBannerAndSaveEvent(String eventName, String description, Timestamp eventTimestamp) {
        String eventIdPlaceholder = "temp_" + System.currentTimeMillis(); // ID temporaneo per nome file
        StorageReference storageRef = storage.getReference().child("event_banners/" + eventIdPlaceholder + ".jpg");

        storageRef.putFile(bannerImageUri)
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            String bannerUrl = uri.toString();
                            saveEventToFirestore(eventName, description, eventTimestamp, bannerUrl); // Salva evento con URL
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to get banner download URL", e);
                            Toast.makeText(this, getString(R.string.event_image_upload_error, e.getMessage()), Toast.LENGTH_LONG).show();
                            setLoading(false);
                        }))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to upload banner image", e);
                    Toast.makeText(this, getString(R.string.event_image_upload_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    setLoading(false);
                });
    }


    // Salva i dati dell'evento su Firestore
    private void saveEventToFirestore(String eventName, String description, Timestamp eventTimestamp, @Nullable String bannerUrl) {
        // Recupera nome utente attuale (per denormalizzazione)
        db.collection("utenti").document(currentUser.getUid()).get()
                .addOnSuccessListener(userDoc -> {
                    String creatorName = "A User"; // Default
                    if (userDoc.exists() && userDoc.getString("nomeUtente") != null) {
                        creatorName = userDoc.getString("nomeUtente");
                    }

                    Map<String, Object> eventData = new HashMap<>();
                    eventData.put("name", eventName);
                    eventData.put("description", description);
                    eventData.put("dateTime", eventTimestamp);
                    eventData.put("routeId", routeId);
                    eventData.put("routeName", routeName); // Denormalizzato
                    eventData.put("routeDistanceKm", routeDistanceKm); // Denormalizzato
                    eventData.put("createdByUid", currentUser.getUid());
                    eventData.put("createdByName", creatorName); // Denormalizzato
                    eventData.put("createdAt", Timestamp.now());
                    eventData.put("participantCount", 0); // Inizia a 0
                    eventData.put("reminderSent", false);
                    if (bannerUrl != null) {
                        eventData.put("bannerUrl", bannerUrl);
                    }

                    db.collection("events").add(eventData) // Aggiungi nuovo documento evento
                            .addOnSuccessListener(documentReference -> {
                                Log.d(TAG, "Event published successfully with ID: " + documentReference.getId());
                                Toast.makeText(this, R.string.event_save_success, Toast.LENGTH_SHORT).show();
                                setLoading(false);
                                finish(); // Torna a RouteDetailActivity
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error saving event", e);
                                Toast.makeText(this, getString(R.string.event_save_error, e.getMessage()), Toast.LENGTH_LONG).show();
                                setLoading(false);
                            });
                })
                .addOnFailureListener(e -> {
                    // Errore nel recuperare dati utente
                    Log.e(TAG, "Error fetching user data for event creation", e);
                    Toast.makeText(this, getString(R.string.event_save_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    setLoading(false);
                });
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        binding.progressBarCreateEvent.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.publishEventButton.setEnabled(!loading);
        binding.eventNameEditText.setEnabled(!loading);
        binding.eventDescriptionEditText.setEnabled(!loading);
        binding.dateEditText.setEnabled(!loading);
        binding.timeEditText.setEnabled(!loading);
        binding.addBannerButton.setEnabled(!loading);
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}