package com.spstudio.curv_app.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem; // Per pulsante indietro
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.databinding.ActivityEditProfileBinding;
import com.spstudio.curv_app.ui.dialog.ChangePasswordBottomSheet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap; // Per ordinare paesi

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";

    private ActivityEditProfileBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseStorage storage;
    private FirebaseUser currentUser;
    private String currentUserId;

    private Uri newImageUri; // URI della nuova immagine selezionata
    private String currentImageUrl; // URL dell'immagine attuale (per confronto)
    private String selectedCountryCode;
    private String selectedDrivingStyle;

    // Mappa Paesi (come in ProfileSetup)
    private final Map<String, String> countries = new TreeMap<>(Map.ofEntries(
            Map.entry("AE", "United Arab Emirates"), Map.entry("AR", "Argentina"), Map.entry("AU", "Australia"),
            Map.entry("BD", "Bangladesh"), Map.entry("BE", "Belgium"), Map.entry("BR", "Brazil"), Map.entry("CA", "Canada"),
            Map.entry("CH", "Switzerland"), Map.entry("CL", "Chile"), Map.entry("CN", "China"), Map.entry("CO", "Colombia"),
            Map.entry("CZ", "Czech Republic"), Map.entry("DE", "Germany"), Map.entry("DK", "Denmark"), Map.entry("EG", "Egypt"),
            Map.entry("ES", "Spain"), Map.entry("FI", "Finland"), Map.entry("FR", "France"), Map.entry("GB", "United Kingdom"),
            Map.entry("GR", "Greece"), Map.entry("HU", "Hungary"), Map.entry("ID", "Indonesia"), Map.entry("IE", "Ireland"),
            Map.entry("IL", "Israel"), Map.entry("IN", "India"), Map.entry("IR", "Iran"), Map.entry("IT", "Italy"), Map.entry("JP", "Japan"),
            Map.entry("KE", "Kenya"), Map.entry("KR", "South Korea"), Map.entry("KW", "Kuwait"), Map.entry("MX", "Mexico"),
            Map.entry("MY", "Malaysia"), Map.entry("NG", "Nigeria"), Map.entry("NL", "Netherlands"), Map.entry("NO", "Norway"),
            Map.entry("NZ", "New Zealand"), Map.entry("OM", "Oman"), Map.entry("PE", "Peru"), Map.entry("PH", "Philippines"),
            Map.entry("PK", "Pakistan"), Map.entry("PL", "Poland"), Map.entry("PT", "Portugal"), Map.entry("QA", "Qatar"),
            Map.entry("RO", "Romania"), Map.entry("RU", "Russia"), Map.entry("SA", "Saudi Arabia"), Map.entry("SE", "Sweden"),
            Map.entry("SG", "Singapore"), Map.entry("TH", "Thailand"), Map.entry("TR", "Turkey"), Map.entry("UA", "Ukraine"),
            Map.entry("US", "United States"), Map.entry("VN", "Vietnam"), Map.entry("ZA", "South Africa")
    ));
    // Lista Stili Guida
    private final List<String> drivingStyles = Arrays.asList(
            "Relaxed", "Sporty", "Explorer" // Usa stringhe fisse o R.string
    );


    // Image Picker
    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    newImageUri = uri;
                    Glide.with(this).load(newImageUri).into(binding.profileImageView);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        storage = FirebaseStorage.getInstance();
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            // Se l'utente non è loggato, torna al Login
            navigateToLogin();
            return;
        }
        currentUserId = currentUser.getUid();

        // Setup Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true); // Mostra titolo
        }

        setupDropdowns();
        setupListeners();
        loadUserProfile(); // Carica i dati attuali
    }

    private void setupDropdowns() {
        // Dropdown Stili Guida
        ArrayAdapter<String> styleAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, drivingStyles);
        AutoCompleteTextView styleDropdown = binding.drivingStyleDropdown;
        styleDropdown.setAdapter(styleAdapter);
        styleDropdown.setOnItemClickListener((parent, view, position, id) -> {
            selectedDrivingStyle = (String) parent.getItemAtPosition(position);
        });

        // Dropdown Paesi
        String[] countryNames = countries.values().toArray(new String[0]);
        ArrayAdapter<String> countryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, countryNames);
        AutoCompleteTextView countryDropdown = binding.countryDropdown;
        countryDropdown.setAdapter(countryAdapter);
        countryDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = (String) parent.getItemAtPosition(position);
            selectedCountryCode = getCountryCodeFromName(selectedName);
        });
    }

    private void setupListeners() {
        binding.editImageFab.setOnClickListener(v -> mGetContent.launch("image/*"));
        binding.saveProfileButton.setOnClickListener(v -> saveProfile());
        binding.changePasswordButton.setOnClickListener(v -> {
            // === MODIFICA QUI ===
            // Mostra il BottomSheet per il cambio password
            ChangePasswordBottomSheet bottomSheet = ChangePasswordBottomSheet.newInstance();
            bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
            // Toast.makeText(this, "Change Password clicked (Implement logic)", Toast.LENGTH_SHORT).show(); // Rimuovi placeholder
            // === FINE MODIFICA ===
        });
    }

    // Carica i dati attuali dell'utente nei campi
    private void loadUserProfile() {
        setLoading(true);
        db.collection("utenti").document(currentUserId).get()
                .addOnSuccessListener(doc -> {
                    setLoading(false);
                    if (doc.exists()) {
                        Map<String, Object> data = doc.getData();
                        if (data != null) {
                            currentImageUrl = (String) data.get("profileImageUrl");
                            Glide.with(this)
                                    .load(currentImageUrl)
                                    .placeholder(R.drawable.ic_person)
                                    .error(R.drawable.ic_person)
                                    .into(binding.profileImageView);

                            binding.usernameEditText.setText((String) data.getOrDefault("nomeUtente", ""));
                            binding.bioEditText.setText((String) data.getOrDefault("bio", ""));
                            binding.vehicleEditText.setText((String) data.getOrDefault("vehicle", ""));

                            // Imposta valore preselezionato Stile Guida
                            selectedDrivingStyle = (String) data.get("drivingStyle");
                            if (selectedDrivingStyle != null) {
                                binding.drivingStyleDropdown.setText(selectedDrivingStyle, false); // false per non filtrare
                            }

                            // Imposta valore preselezionato Paese
                            selectedCountryCode = (String) data.get("preferredCountry");
                            if (selectedCountryCode != null) {
                                String countryName = getCountryNameFromCode(selectedCountryCode);
                                binding.countryDropdown.setText(countryName, false);
                            }
                        }
                    } else {
                        Log.w(TAG, "User document does not exist for loading: " + currentUserId);
                        Toast.makeText(this, R.string.edit_profile_loading_error, Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Log.e(TAG, "Error loading user profile", e);
                    Toast.makeText(this, R.string.edit_profile_loading_error, Toast.LENGTH_SHORT).show();
                });
    }

    // Salva le modifiche su Firestore
    private void saveProfile() {
        String newUsername = binding.usernameEditText.getText().toString().trim();
        String newBio = binding.bioEditText.getText().toString().trim();
        String newVehicle = binding.vehicleEditText.getText().toString().trim();
        // selectedDrivingStyle e selectedCountryCode sono già aggiornati dai listener

        if (TextUtils.isEmpty(newUsername)) {
            binding.usernameInputLayout.setError("Username cannot be empty");
            return;
        } else {
            binding.usernameInputLayout.setError(null);
        }

        setLoading(true);
        Toast.makeText(this, R.string.edit_profile_saving, Toast.LENGTH_SHORT).show();

        // Se l'immagine è cambiata, caricala prima
        if (newImageUri != null) {
            uploadNewImageAndSaveData(newUsername, newBio, newVehicle);
        } else {
            // Altrimenti salva direttamente i dati (l'URL immagine rimane invariato)
            saveDataToFirestore(newUsername, newBio, newVehicle, currentImageUrl);
        }
    }

    // Carica la nuova immagine su Storage
    private void uploadNewImageAndSaveData(String username, String bio, String vehicle) {
        StorageReference storageRef = storage.getReference().child("profile_pics/" + currentUserId + ".jpg");
        storageRef.putFile(newImageUri)
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            String newImageUrl = uri.toString();
                            saveDataToFirestore(username, bio, vehicle, newImageUrl); // Salva dati con nuovo URL
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to get download URL", e);
                            Toast.makeText(this, getString(R.string.edit_profile_image_upload_error, e.getMessage()), Toast.LENGTH_LONG).show();
                            setLoading(false);
                        })
                )
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to upload image", e);
                    Toast.makeText(this, getString(R.string.edit_profile_image_upload_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    setLoading(false);
                });
    }

    // Aggiorna il documento utente su Firestore
    private void saveDataToFirestore(String username, String bio, String vehicle, String imageUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("nomeUtente", username);
        updates.put("bio", bio);
        updates.put("vehicle", vehicle);
        updates.put("drivingStyle", selectedDrivingStyle); // Può essere null se non selezionato
        updates.put("preferredCountry", selectedCountryCode); // Può essere null
        updates.put("profileImageUrl", imageUrl); // Aggiorna URL immagine (nuovo o vecchio)

        db.collection("utenti").document(currentUserId)
                .set(updates, SetOptions.merge()) // Usa merge per aggiornare solo i campi specificati
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile updated successfully!");
                    Toast.makeText(this, R.string.edit_profile_save_success, Toast.LENGTH_SHORT).show();
                    setLoading(false);
                    finish(); // Torna alla schermata precedente (ProfileFragment)
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating profile", e);
                    Toast.makeText(this, getString(R.string.edit_profile_save_error, e.getMessage()), Toast.LENGTH_LONG).show();
                    setLoading(false);
                });
    }

    // Helper per trovare codice paese da nome
    private String getCountryCodeFromName(String countryName) {
        for (Map.Entry<String, String> entry : countries.entrySet()) {
            if (entry.getValue().equals(countryName)) {
                return entry.getKey();
            }
        }
        return null; // Non trovato
    }

    // Helper per trovare nome paese da codice
    private String getCountryNameFromCode(String countryCode) {
        return countries.get(countryCode); // Ritorna null se non trovato
    }


    private void setLoading(boolean loading) {
        binding.progressBarEdit.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.saveProfileButton.setEnabled(!loading);
        binding.editImageFab.setEnabled(!loading);
        binding.usernameEditText.setEnabled(!loading);
        binding.bioEditText.setEnabled(!loading);
        binding.vehicleEditText.setEnabled(!loading);
        binding.drivingStyleDropdown.setEnabled(!loading);
        binding.countryDropdown.setEnabled(!loading);
        binding.changePasswordButton.setEnabled(!loading);
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishAffinity();
    }

    // Gestisce il click sul pulsante indietro della Toolbar
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed(); // O finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}