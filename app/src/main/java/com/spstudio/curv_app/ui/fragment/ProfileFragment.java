package com.spstudio.curv_app.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment; // Non necessario qui, ma utile altrove

import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot; // Importa QuerySnapshot
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.RouteModel;
import com.spstudio.curv_app.databinding.FragmentProfileBinding;
import com.spstudio.curv_app.databinding.IncludeStatCardVerticalBinding;
import com.spstudio.curv_app.services.SettingsService;
import com.spstudio.curv_app.ui.activity.EditProfileActivity;
import com.spstudio.curv_app.ui.activity.LoginActivity;
// import com.spstudio.curv_app.ui.activity.SettingsActivity;
// import com.spstudio.curv_app.ui.activity.EditProfileActivity;
import com.spstudio.curv_app.ui.adapter.ProfileSectionsAdapter;

import java.util.Locale;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";

    private FragmentProfileBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private SettingsService settingsService;
    private FirebaseUser currentUser;
    private String currentUserId;

    private ProfileSectionsAdapter viewPagerAdapter;
    private ListenerRegistration userListener;

    // Variabili per le statistiche
    private int routesCreated = 0;
    private int routesDriven = 0;
    private double totalDistanceCreated = 0.0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        // Controlla se il contesto è valido prima di accedere a SettingsService
        if (getContext() != null) {
            settingsService = SettingsService.getInstance(requireContext());
        }

        if (currentUser == null) {
            navigateToLogin(); // Vai al login se non autenticato
            return;
        }
        currentUserId = currentUser.getUid();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (currentUserId == null || settingsService == null) {
            Log.e(TAG, "User ID or SettingsService is null in onViewCreated");
            // Potrebbe essere necessario mostrare un errore o tornare indietro
            if (currentUser == null) navigateToLogin(); // Sicurezza aggiuntiva
            return;
        }

        setupUIForMyProfile(); // Imposta UI e listener per il profilo utente loggato
        setupViewPager();      // Imposta i tab e il ViewPager

        listenToUserData();          // Ascolta i dati dell'utente
        calculateAggregateStats(); // Calcola le statistiche
    }

    // Imposta la UI specifica per il profilo dell'utente loggato
    private void setupUIForMyProfile() {
        binding.myProfileActionsLayout.setVisibility(View.VISIBLE); // Mostra layout pulsanti Settings/Logout
        binding.editProfileButton.setVisibility(View.VISIBLE);      // Mostra bottone Edit
        binding.followButton.setVisibility(View.GONE);          // Nascondi Follow
        binding.unfollowButton.setVisibility(View.GONE);        // Nascondi Unfollow

        // Listener per i pulsanti
        binding.logoutButton.setOnClickListener(v -> showLogoutConfirmationDialog());
        binding.settingsButton.setOnClickListener(v -> {
            // TODO: Navigare a SettingsActivity
            // Intent intent = new Intent(requireActivity(), SettingsActivity.class);
            // startActivity(intent);
            Toast.makeText(getContext(), R.string.settings_title, Toast.LENGTH_SHORT).show();
        });
        binding.editProfileButton.setOnClickListener(v -> {
            // === SCOMMENTA/MODIFICA QUESTE RIGHE ===
            Intent intent = new Intent(requireActivity(), EditProfileActivity.class);
            startActivity(intent);
            // Toast.makeText(getContext(), R.string.user_profile_edit_button, Toast.LENGTH_SHORT).show();
            // === FINE MODIFICA ===
        });
    }

    // Imposta il ViewPager e i Tab
    private void setupViewPager() {
        // isMyProfile è 'true' perché questo è ProfileFragment
        viewPagerAdapter = new ProfileSectionsAdapter(this, currentUserId, true);
        binding.viewPager.setAdapter(viewPagerAdapter);

        // Collega i Tab al ViewPager
        new TabLayoutMediator(binding.tabLayout, binding.viewPager, (tab, position) -> {
            // Tab 0 = Saved, Tab 1 = Created
            if (position == 0) {
                tab.setText(R.string.user_profile_tab_saved);
            } else {
                tab.setText(R.string.user_profile_tab_created);
            }
        }).attach();
    }

    // Si mette in ascolto sul documento dell'utente per aggiornamenti
    private void listenToUserData() {
        binding.progressBar.setVisibility(View.VISIBLE);
        userListener = db.collection("utenti").document(currentUserId)
                .addSnapshotListener((snapshot, error) -> {
                    // Controlla se il fragment è ancora attivo e il binding esiste
                    if (!isAdded() || binding == null) {
                        Log.w(TAG, "Fragment detached or binding null during Firestore listener callback.");
                        return;
                    }
                    binding.progressBar.setVisibility(View.GONE);

                    if (error != null) {
                        Log.e(TAG, "Listen failed.", error);
                        Toast.makeText(getContext(), "Error loading profile data.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        Log.d(TAG, "User data updated.");
                        Map<String, Object> userData = snapshot.getData();
                        if (userData != null) {
                            updateProfileUI(userData); // Aggiorna la UI con i nuovi dati
                        } else {
                            Log.w(TAG, "User document data is null for user: " + currentUserId);
                        }
                    } else {
                        Log.w(TAG, "User document does not exist: " + currentUserId);
                        // L'utente potrebbe aver cancellato l'account mentre era loggato?
                        signOut(); // Forza logout se il documento utente non esiste più
                    }
                });
    }

    // Aggiorna la UI con i dati dell'utente da Firestore
    private void updateProfileUI(Map<String, Object> data) {
        if (getContext() == null || binding == null) return; // Sicurezza aggiuntiva

        // Nome Utente
        String username = data.getOrDefault("nomeUtente", getString(R.string.user_profile_unnamed_pilot)).toString();
        binding.usernameTextView.setText(username);

        // Immagine Profilo
        String imageUrl = (String) data.get("profileImageUrl");
        Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .circleCrop()
                .into(binding.profileImageView);

        // Bio
        String bio = (String) data.get("bio");
        if (bio != null && !bio.isEmpty()) {
            binding.bioTextView.setText(bio);
            binding.bioTextView.setVisibility(View.VISIBLE);
        } else {
            //binding.bioTextView.setText(R.string.user_profile_bio_empty); // O nascondi
            binding.bioTextView.setVisibility(View.GONE);
        }

        // Veicolo
        String vehicle = (String) data.get("vehicle");
        if (vehicle != null && !vehicle.isEmpty()) {
            binding.vehicleIcon.setVisibility(View.VISIBLE);
            binding.vehicleTextView.setText(vehicle);
            binding.vehicleTextView.setVisibility(View.VISIBLE);
        } else {
            binding.vehicleIcon.setVisibility(View.GONE);
            //binding.vehicleTextView.setText(R.string.user_profile_vehicle_empty); // O nascondi
            binding.vehicleTextView.setVisibility(View.GONE);
        }

        // Stile di guida (Chip)
        String drivingStyle = (String) data.get("drivingStyle");
        updateDrivingStyleChip(drivingStyle);

        // Follower / Following Counts
        int followerCount = ((Number) data.getOrDefault("followerCount", 0)).intValue();
        int followingCount = ((Number) data.getOrDefault("followingCount", 0)).intValue();
        // Usa getResources().getQuantityString per gestire plurale se necessario
        binding.followerCountTextView.setText(String.format(Locale.US, "%d %s", followerCount, getString(R.string.user_profile_followers)));
        binding.followingCountTextView.setText(String.format(Locale.US, "%d %s", followingCount, getString(R.string.user_profile_following)));
        // TODO: Aggiungere OnClickListener per navigare a FollowListActivity (passando tipo e userId)
    }

    // Calcola statistiche aggregate (chiamato una volta in onViewCreated)
    private void calculateAggregateStats() {
        // 1. Percorsi Creati e Distanza Totale
        db.collection("percorsi")
                .whereEqualTo("creatoreUid", currentUserId)
                .whereEqualTo("status", "approved")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) return;
                    routesCreated = snapshot.size();
                    totalDistanceCreated = 0.0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        try { // Aggiungi try-catch per sicurezza nel parsing
                            RouteModel route = RouteModel.fromFirestore(doc);
                            totalDistanceCreated += route.getDistanzaKm();
                        } catch(Exception e) {
                            Log.e(TAG, "Error parsing route " + doc.getId() + " for stats", e);
                        }
                    }
                    updateStatCards(); // Aggiorna UI quando i dati sono pronti
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error calculating created routes stats", e));

        // 2. Percorsi Guidati (Conteggio 'tempi')
        db.collection("tempi")
                .whereEqualTo("userId", currentUserId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) return;
                    routesDriven = snapshot.size(); // Conta il numero di tempi registrati
                    updateStatCards(); // Aggiorna UI quando i dati sono pronti
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error calculating driven routes stats", e));
    }

    // Aggiorna le tre card delle statistiche
    private void updateStatCards() {
        // Assicurati che il binding sia ancora valido
        if (!isAdded() || binding == null || getContext() == null) return;

        updateStatCard(binding.routesCreatedStat, R.drawable.ic_alt_route, String.valueOf(routesCreated), getString(R.string.user_profile_routes_created), R.color.stat_blue);
        updateStatCard(binding.totalDistanceStat, R.drawable.ic_linear_scale, settingsService.formatDistance(totalDistanceCreated), getString(R.string.user_profile_total_distance), R.color.stat_green);
        updateStatCard(binding.routesDrivenStat, R.drawable.ic_directions_car, String.valueOf(routesDriven), getString(R.string.user_profile_routes_driven), R.color.stat_purple);
    }

    // Helper per aggiornare una singola Stat Card Verticale
    private void updateStatCard(IncludeStatCardVerticalBinding statBinding, int iconRes, String value, String label, int colorRes) {
        if (getContext() == null) return;
        int color = ContextCompat.getColor(requireContext(), colorRes);
        statBinding.getRoot().setStrokeColor(color & 0x4DFFFFFF); // Bordo trasparente
        statBinding.getRoot().setCardBackgroundColor(color & 0x1AFFFFFF); // Sfondo trasparente
        statBinding.statIcon.setImageResource(iconRes);
        ImageViewCompat.setImageTintList(statBinding.statIcon, ColorStateList.valueOf(color));
        statBinding.statValue.setText(value);
        statBinding.statValue.setTextColor(color);
        statBinding.statLabel.setText(label);
        statBinding.statLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.subtleTextColor)); // Etichetta grigia
    }

    // Helper per aggiornare il Chip Stile di Guida
    private void updateDrivingStyleChip(String style) {
        if (!isAdded() || binding == null || getContext() == null) return; // Controlli aggiuntivi

        if (style == null || style.isEmpty()) {
            binding.drivingStyleChip.getRoot().setVisibility(View.GONE);
            return;
        }

        int iconRes;
        int colorRes;
        String styleText;

        // Usa equalsIgnoreCase per confronto sicuro
        if (style.equalsIgnoreCase(getString(R.string.profile_style_relaxed))) {
            iconRes = R.drawable.ic_coffee;
            colorRes = R.color.style_relaxed;
            styleText = getString(R.string.profile_style_relaxed);
        } else if (style.equalsIgnoreCase(getString(R.string.profile_style_sporty))) {
            iconRes = R.drawable.ic_speed;
            colorRes = R.color.style_sporty;
            styleText = getString(R.string.profile_style_sporty);
        } else if (style.equalsIgnoreCase(getString(R.string.profile_style_explorer))) {
            iconRes = R.drawable.ic_explore;
            colorRes = R.color.style_explorer;
            styleText = getString(R.string.profile_style_explorer);
        } else {
            Log.w(TAG, "Unknown driving style: " + style);
            binding.drivingStyleChip.getRoot().setVisibility(View.GONE);
            return;
        }

        binding.drivingStyleChip.getRoot().setVisibility(View.VISIBLE);
        binding.drivingStyleChip.chipText.setText(styleText);
        binding.drivingStyleChip.chipIcon.setImageResource(iconRes);

        int color = ContextCompat.getColor(requireContext(), colorRes);
        binding.drivingStyleChip.chipText.setTextColor(color);
        ImageViewCompat.setImageTintList(binding.drivingStyleChip.chipIcon, ColorStateList.valueOf(color));

        // Assicurati che il drawable esista prima di mutarlo
        Drawable background = ContextCompat.getDrawable(requireContext(), R.drawable.chip_background_grey);
        if (background != null) {
            GradientDrawable chipBackground = (GradientDrawable) background.mutate();
            chipBackground.setColor(color & 0x1AFFFFFF); // Sfondo trasparente
            binding.drivingStyleChip.getRoot().setBackground(chipBackground);
        }
    }

    // Mostra dialogo conferma Logout
    private void showLogoutConfirmationDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.logout_dialog_title)
                .setMessage(R.string.logout_dialog_content)
                .setNegativeButton(R.string.logout_dialog_cancel, null) // null chiude semplicemente
                .setPositiveButton(R.string.logout_dialog_confirm, (dialog, which) -> signOut())
                .show();
    }

    // Esegue il logout e torna alla LoginActivity
    private void signOut() {
        if (getContext() == null) return;

        // Pulisci impostazioni utente locali
        SettingsService.getInstance(requireContext()).clearUserSettings();

        // TODO: Chiamare il servizio notifiche per rimuovere il token FCM da Firestore
        // Esempio: NotificationService.removeFcmToken(db, currentUser.getUid());

        auth.signOut(); // Logout da Firebase Auth

        navigateToLogin(); // Torna alla schermata di Login
    }

    // Naviga a LoginActivity e pulisce lo stack di navigazione
    private void navigateToLogin() {
        if (getActivity() == null) return; // Assicurati che l'activity esista
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        getActivity().finishAffinity(); // Chiude tutte le activity dell'app
    }

    // Rimuovi il listener Firestore quando la vista viene distrutta
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (userListener != null) {
            userListener.remove(); // Stop listening to user data
            userListener = null; // Rilascia riferimento
        }
        binding = null; // Pulisci il ViewBinding
        Log.d(TAG, "onDestroyView called, listener removed and binding set to null.");
    }
}