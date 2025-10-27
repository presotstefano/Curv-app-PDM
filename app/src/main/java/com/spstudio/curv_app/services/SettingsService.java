package com.spstudio.curv_app.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class SettingsService {
    private static final String TAG = "SettingsService";
    private static final String PREFS_NAME = "CurvAppSettings";
    private static final String KEY_MAP_STYLE = "mapStyle";
    private static final String KEY_USE_MILES = "useMiles";
    private static final String KEY_NOTIFICATIONS_ENABLED = "notificationsEnabled";

    private static SettingsService instance;
    private final SharedPreferences sharedPreferences;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    // LiveData per osservare i cambiamenti
    private final MutableLiveData<String> mapStyleUriLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> useMilesLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> notificationsEnabledLiveData = new MutableLiveData<>();
    private final MutableLiveData<String> preferredCountryLiveData = new MutableLiveData<>();

    // Valori attuali (cache)
    private String mapStyleUri;
    private boolean useMiles;
    private boolean notificationsEnabled;
    private String preferredCountry;

    private SettingsService(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        loadLocalPreferences(); // Carica le preferenze locali all'avvio
    }

    public static synchronized SettingsService getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsService(context);
        }
        return instance;
    }

    private void loadLocalPreferences() {
        mapStyleUri = sharedPreferences.getString(KEY_MAP_STYLE, "mapbox://styles/mapbox/outdoors-v11");
        useMiles = sharedPreferences.getBoolean(KEY_USE_MILES, false);
        notificationsEnabled = sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, true);

        // Aggiorna LiveData all'avvio
        mapStyleUriLiveData.postValue(mapStyleUri);
        useMilesLiveData.postValue(useMiles);
        notificationsEnabledLiveData.postValue(notificationsEnabled);

        Log.d(TAG, "Local preferences loaded: Style=" + mapStyleUri + ", Miles=" + useMiles + ", Notifs=" + notificationsEnabled);
    }

    // Carica le impostazioni specifiche dell'utente da Firestore
    public void loadUserSettings() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            db.collection("utenti").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String country = documentSnapshot.getString("preferredCountry");
                            Boolean notifs = documentSnapshot.getBoolean("notificationsEnabled");

                            if (country != null && !country.equals(preferredCountry)) {
                                preferredCountry = country;
                                preferredCountryLiveData.postValue(preferredCountry);
                                Log.d(TAG, "User preferred country loaded: " + preferredCountry);
                            }
                            if (notifs != null && notifs != notificationsEnabled) {
                                notificationsEnabled = notifs;
                                sharedPreferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, notificationsEnabled).apply();
                                notificationsEnabledLiveData.postValue(notificationsEnabled);
                                Log.d(TAG, "User notification setting loaded: " + notificationsEnabled);
                            }
                        } else {
                            Log.w(TAG, "User document does not exist for UID: " + user.getUid());
                            clearUserSettings(); // Pulisce se il doc non esiste (caso strano)
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error loading user settings from Firestore", e);
                        // Non pulire le impostazioni in caso di errore di rete temporaneo
                    });
        } else {
            Log.w(TAG, "Cannot load user settings, user is null.");
            clearUserSettings(); // Pulisce se l'utente fa logout
        }
    }

    // Pulisce le impostazioni specifiche dell'utente (es. al logout)
    public void clearUserSettings() {
        if (preferredCountry != null) {
            preferredCountry = null;
            preferredCountryLiveData.postValue(null);
            Log.d(TAG, "User specific settings cleared.");
        }
    }


    // --- Getters per i LiveData (per osservare i cambiamenti) ---
    public LiveData<String> getMapStyleUriLiveData() { return mapStyleUriLiveData; }
    public LiveData<Boolean> getUseMilesLiveData() { return useMilesLiveData; }
    public LiveData<Boolean> getNotificationsEnabledLiveData() { return notificationsEnabledLiveData; }
    public LiveData<String> getPreferredCountryLiveData() { return preferredCountryLiveData; }

    // --- Getters per i valori attuali (per accesso immediato) ---
    public String getMapStyleUri() { return mapStyleUri; }
    public boolean isUsingMiles() { return useMiles; }
    public boolean areNotificationsEnabled() { return notificationsEnabled; }
    @Nullable
    public String getPreferredCountry() { return preferredCountry; }


    // --- Setters (salvano su SharedPreferences e notificano i LiveData) ---
    public void setMapStyleUri(String newUri) {
        if (newUri != null && !newUri.equals(mapStyleUri)) {
            mapStyleUri = newUri;
            sharedPreferences.edit().putString(KEY_MAP_STYLE, mapStyleUri).apply();
            mapStyleUriLiveData.postValue(mapStyleUri);
        }
    }

    public void setUseMiles(boolean useMiles) {
        if (this.useMiles != useMiles) {
            this.useMiles = useMiles;
            sharedPreferences.edit().putBoolean(KEY_USE_MILES, this.useMiles).apply();
            useMilesLiveData.postValue(this.useMiles);
        }
    }

    public void setNotificationsEnabled(boolean enabled) {
        if (this.notificationsEnabled != enabled) {
            this.notificationsEnabled = enabled;
            sharedPreferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, this.notificationsEnabled).apply();
            notificationsEnabledLiveData.postValue(this.notificationsEnabled);
            // Sincronizza anche con Firestore
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                db.collection("utenti").document(user.getUid())
                        .update("notificationsEnabled", enabled)
                        .addOnFailureListener(e -> Log.w(TAG, "Failed to sync notification setting to Firestore", e));
            }
        }
    }

    public void updatePreferredCountry(String newCountryCode) {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && newCountryCode != null && !newCountryCode.equals(preferredCountry)) {
            preferredCountry = newCountryCode;
            preferredCountryLiveData.postValue(preferredCountry);
            db.collection("utenti").document(user.getUid())
                    .update("preferredCountry", newCountryCode)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Preferred country updated in Firestore to " + newCountryCode))
                    .addOnFailureListener(e -> Log.w(TAG, "Failed to update preferred country in Firestore", e));
        }
    }

    // --- Metodo di utilità per formattare le distanze ---
    public String formatDistance(double distanceKm) {
        if (useMiles) {
            double distanceMiles = distanceKm * 0.621371;
            return String.format(Locale.US, "%.1f mi", distanceMiles);
        } else {
            return String.format(Locale.US, "%.1f km", distanceKm);
        }
    }
}