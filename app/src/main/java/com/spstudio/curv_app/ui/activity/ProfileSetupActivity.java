package com.spstudio.curv_app.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide; // Importa Glide
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.databinding.ActivityProfileSetupBinding;

import java.util.HashMap;
import java.util.Map;

public class ProfileSetupActivity extends AppCompatActivity {

    private static final String TAG = "ProfileSetupActivity";

    private ActivityProfileSetupBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private Uri imageUri;

    // Modern way to handle activity results (picking an image)
    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    imageUri = uri;
                    // Usa Glide per caricare l'immagine selezionata nella CircleImageView
                    Glide.with(this)
                            .load(imageUri)
                            .placeholder(R.drawable.ic_person) // Placeholder
                            .error(R.drawable.ic_person) // Immagine di errore
                            .into(binding.profileImageView);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileSetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // Imposta la Toolbar come ActionBar
        setSupportActionBar(binding.toolbar);
        // Nasconde il titolo sulla toolbar espansa
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        setupListeners();
    }

    private void setupListeners() {
        binding.editImageFab.setOnClickListener(v -> mGetContent.launch("image/*"));
        binding.countryEditText.setOnClickListener(v -> {
            // Placeholder for country selection
            Toast.makeText(this, "Country selection to be implemented", Toast.LENGTH_SHORT).show();
            // In a real app, you would show a BottomSheet or a new Activity here
            // For now, let's just set a default value for testing
            binding.countryEditText.setText("Italy"); // Placeholder, usa getString per "Italy" se tradotto
        });
        binding.saveProfileButton.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {
        String username = binding.usernameEditText.getText().toString().trim();
        String country = binding.countryEditText.getText().toString().trim(); // Placeholder logic

        if (username.isEmpty()) {
            binding.usernameInputLayout.setError(getString(R.string.profile_setup_username_validator));
            return;
        } else {
            binding.usernameInputLayout.setError(null);
        }

        if (country.isEmpty()) {
            binding.countryInputLayout.setError(getString(R.string.profile_setup_country_error));
            // Qui mostreresti il selettore del paese invece di un errore
            Toast.makeText(this, R.string.profile_setup_country_error, Toast.LENGTH_SHORT).show();
            return;
        } else {
            binding.countryInputLayout.setError(null);
        }

        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            navigateToLogin();
            return;
        }

        setLoading(true);

        if (imageUri != null) {
            uploadImageAndSaveData(firebaseUser, username, country);
        } else {
            saveDataToFirestore(firebaseUser, username, country, null);
        }
    }

    private void uploadImageAndSaveData(FirebaseUser user, String username, String country) {
        // Usa un nome file univoco o sovrascrivi quello esistente
        StorageReference storageRef = storage.getReference().child("profile_pics/" + user.getUid() + ".jpg");
        storageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            String imageUrl = uri.toString();
                            saveDataToFirestore(user, username, country, imageUrl);
                        })
                        .addOnFailureListener(e -> { // Failure getting download URL
                            setLoading(false);
                            Toast.makeText(ProfileSetupActivity.this, "Failed to get image URL: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }))
                .addOnFailureListener(e -> { // Failure uploading file
                    setLoading(false);
                    Toast.makeText(ProfileSetupActivity.this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void saveDataToFirestore(FirebaseUser user, String username, String country, String imageUrl) {
        Map<String, Object> userDataUpdates = new HashMap<>();
        userDataUpdates.put("nomeUtente", username);
        // !!! IMPORTANTE: Mappa il nome del paese selezionato (es. "Italy") al suo codice ISO (es. "IT")
        // Dovresti avere una mappa o una logica per fare questa conversione.
        // Uso un placeholder qui.
        userDataUpdates.put("preferredCountry", getCountryCodeFromName(country)); // Implementa getCountryCodeFromName
        if (imageUrl != null) {
            userDataUpdates.put("profileImageUrl", imageUrl);
        }
        userDataUpdates.put("isProfileComplete", true);
        // Aggiungi gli altri campi vuoti o con valori di default se necessario
        // userDataUpdates.put("bio", "");
        // userDataUpdates.put("vehicle", "");
        // userDataUpdates.put("drivingStyle", null);

        db.collection("utenti").document(user.getUid())
                .update(userDataUpdates) // Usa update invece di set per non sovrascrivere campi esistenti come email, uid, dataCreazione
                .addOnSuccessListener(aVoid -> {
                    setLoading(false);
                    navigateToMain();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(ProfileSetupActivity.this, getString(R.string.profile_setup_save_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }

    // Funzione placeholder da implementare per mappare nome paese a codice
    private String getCountryCodeFromName(String countryName) {
        // Implementa la logica per trovare il codice ISO ("IT") dal nome ("Italy")
        // Ad esempio, iterando sulla mappa _countries usata nel BottomSheet
        // Ritorna un valore di default o null se non trovato
        if ("Italy".equalsIgnoreCase(countryName)) {
            return "IT";
        }
        return null; // O gestisci l'errore
    }


    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.saveProfileButton.setEnabled(!isLoading);
        binding.editImageFab.setEnabled(!isLoading);
        binding.usernameEditText.setEnabled(!isLoading);
        binding.countryEditText.setEnabled(!isLoading);
    }

    private void navigateToMain() {
        Intent intent = new Intent(ProfileSetupActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}