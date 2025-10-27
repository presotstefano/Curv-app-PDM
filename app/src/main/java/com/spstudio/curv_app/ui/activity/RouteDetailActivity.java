package com.spstudio.curv_app.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat; // Per colorare icone stat card

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.gms.maps.model.LatLng; // Importa LatLng
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.RouteModel;
import com.spstudio.curv_app.databinding.ActivityRouteDetailBinding; // Usa ViewBinding
import com.spstudio.curv_app.databinding.IncludeStatCardBinding; // Binding per il layout incluso
import com.spstudio.curv_app.services.SettingsService;

import com.google.android.gms.tasks.Task; // Aggiungi questo import
import com.google.android.gms.tasks.Tasks; // Aggiungi questo import
import com.google.firebase.firestore.FieldValue; // Aggiungi questo import
import com.google.firebase.firestore.SetOptions; // Aggiungi questo import

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean; // Per gestire lo stato iniziale
import java.util.concurrent.atomic.AtomicInteger; // Per gestire lo stato iniziale
import androidx.recyclerview.widget.LinearLayoutManager; // Import per RecyclerView
import androidx.recyclerview.widget.RecyclerView; // Import per RecyclerView
import com.spstudio.curv_app.data.model.TimeEntry; // Import modello TimeEntry
import com.spstudio.curv_app.ui.adapter.LeaderboardAdapter;
import com.spstudio.curv_app.data.model.Review; // Import Review
import com.spstudio.curv_app.ui.adapter.ReviewAdapter; // Import ReviewAdapter
import com.spstudio.curv_app.data.model.Comment; // Import Comment
import com.spstudio.curv_app.ui.adapter.CommentAdapter; // Import CommentAdapter
import com.google.android.material.textfield.TextInputEditText; // Per campo commento
import com.spstudio.curv_app.ui.dialog.AddReviewBottomSheet;
import com.spstudio.curv_app.ui.dialog.AddToGroupBottomSheet;
import com.spstudio.curv_app.ui.dialog.ReportRouteBottomSheet;

import android.view.Menu; // Aggiungi import
import android.view.MenuInflater; // Aggiungi import
import android.view.MenuItem; // Aggiungi import
import androidx.core.view.MenuProvider; // Aggiungi import
import androidx.lifecycle.Lifecycle;
import android.view.inputmethod.InputMethodManager;
import java.util.Locale;

import de.hdodenhof.circleimageview.CircleImageView;

public class RouteDetailActivity extends AppCompatActivity {

    private static final String TAG = "RouteDetailActivity";
    public static final String EXTRA_ROUTE_ID = "ROUTE_ID"; // Chiave per passare l'ID

    private ActivityRouteDetailBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private SettingsService settingsService;
    private ListenerRegistration routeListener; // Per ascoltare cambiamenti sul documento

    private String routeId;
    private RouteModel currentRoute; // Mantiene il modello corrente
    private FirebaseUser currentUser;

    private boolean isLiked = false;
    private boolean isSaved = false;
    private boolean isLoadingLikeStatus = true;
    private boolean isLoadingSaveStatus = true;
    private ListenerRegistration likeStatusListener; // Listener per stato like
    private ListenerRegistration saveStatusListener; // Listener per stato save
    private LeaderboardAdapter leaderboardAdapter;
    private final List<TimeEntry> leaderboardEntries = new ArrayList<>();
    private ListenerRegistration leaderboardListener;
    // Variabili per Recensioni
    private ReviewAdapter reviewAdapter;
    private final List<Review> reviewEntries = new ArrayList<>();
    private ListenerRegistration reviewsListener;

    // Variabili per Commenti
    private CommentAdapter commentAdapter;
    private final List<Comment> commentEntries = new ArrayList<>();
    private ListenerRegistration commentsListener;
    private boolean isPostingComment = false;
    private Menu optionsMenu; // Riferimento al menu
    private boolean isUserGroupAdmin = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRouteDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        settingsService = SettingsService.getInstance(this);

        // Recupera l'ID del percorso dall'Intent
        routeId = getIntent().getStringExtra(EXTRA_ROUTE_ID);

        if (routeId == null || routeId.isEmpty()) {
            Log.e(TAG, "Route ID is missing!");
            Toast.makeText(this, R.string.error_loading_route_details, Toast.LENGTH_SHORT).show();
            finish(); // Chiudi l'activity se l'ID manca
            return;
        }

        setupToolbar();
        setupMenu();
        setupButtonClickListeners(); // Imposta i listener per i pulsanti inferiori

        // Inizia ad ascoltare i dati del percorso
        listenToRouteData();
        loadInitialLikeSaveStatus();
        setupLeaderboardRecyclerView();
        listenToLeaderboardData();
        // === NUOVO: Setup RecyclerView Recensioni e Commenti ===
        setupReviewsRecyclerView();
        listenToReviewsData();
        setupCommentsRecyclerView();
        listenToCommentsData();
        // === FINE NUOVO ===

        // Listener per postare commento
        binding.postCommentButton.setOnClickListener(v -> postComment());
    }

    private void setupMenu() {
        addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.route_detail_menu, menu);
                optionsMenu = menu;
                updateMenuVisibility();
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.action_report) {
                    showReportSheet();
                    return true;
                } else if (itemId == R.id.action_add_to_group) {
                    showAddToGroupSheet();
                    return true;
                    // === AGGIUNGI QUESTO BLOCCO ===
                } else if (itemId == R.id.action_create_event) {
                    if (currentRoute != null) {
                        Intent intent = new Intent(RouteDetailActivity.this, CreateEventActivity.class);
                        intent.putExtra("ROUTE_ID", currentRoute.getId());
                        intent.putExtra("ROUTE_NAME", currentRoute.getNome()); // Passa anche il nome
                        startActivity(intent);
                    } else {
                        Toast.makeText(RouteDetailActivity.this, R.string.error_loading_route_details, Toast.LENGTH_SHORT).show();
                    }
                    return true;
                    // === FINE BLOCCO AGGIUNTO ===
                } else if (itemId == android.R.id.home) {
                    finish(); // Usa finish() invece di onBackPressed() per coerenza
                    return true;
                }
                return false;
            }
        }, this, Lifecycle.State.RESUMED);
    }

    // NUOVO: Mostra il BottomSheet per le segnalazioni
    private void showReportSheet() {
        if (currentRoute == null || currentUser == null) return;

        // Non permettere all'autore di segnalare il proprio percorso (opzionale)
        if (currentRoute.getCreatoreUid().equals(currentUser.getUid())) {
            Toast.makeText(this, "You cannot report your own route.", Toast.LENGTH_SHORT).show();
            return;
        }

        ReportRouteBottomSheet bottomSheet = ReportRouteBottomSheet.newInstance(
                currentRoute.getId(),
                currentRoute.getNome(),
                currentRoute.getCreatoreUid()
        );
        bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
    }

    // NUOVO: Mostra il BottomSheet per aggiungere a un gruppo
    private void showAddToGroupSheet() {
        if (currentRoute == null) return;
        AddToGroupBottomSheet bottomSheet = AddToGroupBottomSheet.newInstance(currentRoute.getId());
        bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
    }

    // NUOVO: Controlla se l'utente è admin di qualche gruppo
    private void checkIfUserIsAdmin() {
        if (currentUser == null) {
            isUserGroupAdmin = false;
            updateMenuVisibility();
            return;
        }
        // Query semplificata: controlla solo se l'utente ha *creato* gruppi
        db.collection("groups")
                .whereEqualTo("creatorUid", currentUser.getUid())
                .limit(1) // Ci basta sapere se esiste almeno un gruppo
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        isUserGroupAdmin = true; // L'utente è admin di almeno un gruppo
                    } else {
                        isUserGroupAdmin = false;
                    }
                    updateMenuVisibility(); // Aggiorna la UI del menu
                });
    }

    // NUOVO: Aggiorna la visibilità del pulsante "Aggiungi a Gruppo"
    private void updateMenuVisibility() {
        if (optionsMenu != null) {
            MenuItem item = optionsMenu.findItem(R.id.action_add_to_group);
            if (item != null) {
                item.setVisible(isUserGroupAdmin); // Mostra solo se l'utente è admin
            }
        }
    }

    // NUOVO: Setup RecyclerView Recensioni
    private void setupReviewsRecyclerView() {
        reviewAdapter = new ReviewAdapter(this, routeId, reviewEntries);
        binding.reviewsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.reviewsRecyclerView.setAdapter(reviewAdapter);
        binding.reviewsRecyclerView.setNestedScrollingEnabled(false);
    }

    // NUOVO: Ascolta dati recensioni
    private void listenToReviewsData() {
        if (routeId == null || routeId.isEmpty()) return;
        Log.d(TAG, "Setting up reviews listener for route: " + routeId);

        reviewsListener = db.collection("percorsi").document(routeId)
                .collection("recensioni")
                .orderBy("timestamp", Query.Direction.DESCENDING) // Le più recenti prima
                .limit(10) // Limita il numero iniziale
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Reviews listener failed.", error);
                        binding.reviewsEmptyTextView.setVisibility(View.VISIBLE);
                        binding.reviewsRecyclerView.setVisibility(View.GONE);
                        return;
                    }
                    if (snapshots != null) {
                        List<Review> newReviews = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            newReviews.add(Review.fromFirestore(doc));
                        }
                        reviewAdapter.updateData(newReviews);
                        binding.reviewsEmptyTextView.setVisibility(newReviews.isEmpty() ? View.VISIBLE : View.GONE);
                        binding.reviewsRecyclerView.setVisibility(newReviews.isEmpty() ? View.GONE : View.VISIBLE);
                        // Aggiorna il titolo della sezione con il conteggio (opzionale, richiede accesso al routeModel)
                        // if (currentRoute != null) binding.reviewsTitle.setText(...);
                    } else {
                        reviewAdapter.updateData(new ArrayList<>());
                        binding.reviewsEmptyTextView.setVisibility(View.VISIBLE);
                        binding.reviewsRecyclerView.setVisibility(View.GONE);
                    }
                });
    }

    // NUOVO: Setup RecyclerView Commenti
    private void setupCommentsRecyclerView() {
        commentAdapter = new CommentAdapter(this, commentEntries);
        binding.commentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.commentsRecyclerView.setAdapter(commentAdapter);
        binding.commentsRecyclerView.setNestedScrollingEnabled(false);
    }

    // NUOVO: Ascolta dati commenti
    private void listenToCommentsData() {
        if (routeId == null || routeId.isEmpty()) return;
        Log.d(TAG, "Setting up comments listener for route: " + routeId);

        commentsListener = db.collection("percorsi").document(routeId)
                .collection("comments")
                .orderBy("timestamp", Query.Direction.DESCENDING) // I più recenti prima
                .limit(20) // Limita il numero iniziale
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Comments listener failed.", error);
                        binding.commentsEmptyTextView.setVisibility(View.VISIBLE);
                        binding.commentsRecyclerView.setVisibility(View.GONE);
                        return;
                    }
                    if (snapshots != null) {
                        List<Comment> newComments = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            newComments.add(Comment.fromFirestore(doc));
                        }
                        commentAdapter.updateData(newComments);
                        binding.commentsEmptyTextView.setVisibility(newComments.isEmpty() ? View.VISIBLE : View.GONE);
                        binding.commentsRecyclerView.setVisibility(newComments.isEmpty() ? View.GONE : View.VISIBLE);
                        // Aggiorna il titolo della sezione
                        // if (currentRoute != null) binding.discussionTitle.setText(...);
                    } else {
                        commentAdapter.updateData(new ArrayList<>());
                        binding.commentsEmptyTextView.setVisibility(View.VISIBLE);
                        binding.commentsRecyclerView.setVisibility(View.GONE);
                    }
                });
    }

    // NUOVO: Logica per postare un commento
    private void postComment() {
        if (currentUser == null) {
            Toast.makeText(this, "You need to be logged in to comment.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isPostingComment) return; // Evita doppi click

        String commentText = binding.commentEditText.getText().toString().trim();
        if (commentText.isEmpty()) {
            binding.commentInputLayout.setError("Comment cannot be empty"); // Mostra errore
            return;
        } else {
            binding.commentInputLayout.setError(null); // Rimuovi errore
        }

        isPostingComment = true;
        binding.postCommentButton.setEnabled(false); // Disabilita pulsante
        // Opzionale: mostra ProgressBar

        // Recupera nome utente e avatar URL (potresti memorizzarli all'avvio dell'app)
        db.collection("utenti").document(currentUser.getUid()).get()
                .addOnSuccessListener(userDoc -> {
                    String authorName = "A User"; // Default
                    String authorAvatarUrl = null;
                    if (userDoc.exists()) {
                        authorName = userDoc.getString("nomeUtente");
                        authorAvatarUrl = userDoc.getString("profileImageUrl");
                    }

                    Map<String, Object> commentData = new HashMap<>();
                    commentData.put("text", commentText);
                    commentData.put("authorUid", currentUser.getUid());
                    commentData.put("authorName", authorName);
                    commentData.put("authorAvatarUrl", authorAvatarUrl);
                    commentData.put("timestamp", com.google.firebase.Timestamp.now());

                    db.collection("percorsi").document(routeId).collection("comments")
                            .add(commentData)
                            .addOnSuccessListener(docRef -> {
                                Log.d(TAG, "Comment posted successfully");
                                binding.commentEditText.setText(""); // Pulisci campo
                                hideKeyboard(); // Nascondi tastiera
                                isPostingComment = false;
                                binding.postCommentButton.setEnabled(true);
                                // Il listener aggiornerà automaticamente la lista
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Error posting comment", e);
                                Toast.makeText(this, R.string.route_detail_comment_post_error, Toast.LENGTH_SHORT).show();
                                isPostingComment = false;
                                binding.postCommentButton.setEnabled(true);
                            });
                })
                .addOnFailureListener(e -> {
                    // Errore nel recuperare i dati utente
                    Log.e(TAG, "Error getting user data for comment", e);
                    Toast.makeText(this, R.string.route_detail_comment_post_error, Toast.LENGTH_SHORT).show();
                    isPostingComment = false;
                    binding.postCommentButton.setEnabled(true);
                });
    }

    // NUOVO: Helper per nascondere la tastiera
    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
            view.clearFocus(); // Rimuove il focus dal campo di testo
        }
    }

    private void setupLeaderboardRecyclerView() {
        leaderboardAdapter = new LeaderboardAdapter(this, leaderboardEntries);
        binding.leaderboardRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.leaderboardRecyclerView.setAdapter(leaderboardAdapter);
        // Opzionale: Aggiungere ItemDecoration per spaziatura/divisori
    }

    // NUOVO: Ascolta i dati dei tempi da Firestore
    private void listenToLeaderboardData() {
        if (routeId == null || routeId.isEmpty()) return;

        leaderboardListener = db.collection("tempi")
                .whereEqualTo("routeId", routeId)
                .orderBy("tempoMs", Query.Direction.ASCENDING) // Ordina dal più veloce
                .limit(10) // Mostra solo i top 10
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Leaderboard listener failed.", error);
                        // Gestisci errore (es. mostra messaggio)
                        binding.leaderboardEmptyTextView.setVisibility(View.VISIBLE);
                        binding.leaderboardRecyclerView.setVisibility(View.GONE);
                        return;
                    }

                    if (snapshots != null) {
                        Log.d(TAG, "Received leaderboard update with " + snapshots.size() + " entries.");
                        leaderboardEntries.clear(); // Pulisci la lista vecchia
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            leaderboardEntries.add(TimeEntry.fromFirestore(doc));
                        }
                        leaderboardAdapter.notifyDataSetChanged(); // Notifica all'adapter che i dati sono cambiati

                        // Mostra/Nascondi messaggio vuoto
                        if (leaderboardEntries.isEmpty()) {
                            binding.leaderboardEmptyTextView.setVisibility(View.VISIBLE);
                            binding.leaderboardRecyclerView.setVisibility(View.GONE);
                        } else {
                            binding.leaderboardEmptyTextView.setVisibility(View.GONE);
                            binding.leaderboardRecyclerView.setVisibility(View.VISIBLE);
                        }

                    } else {
                        Log.d(TAG, "Leaderboard snapshot is null.");
                        leaderboardEntries.clear();
                        leaderboardAdapter.notifyDataSetChanged();
                        binding.leaderboardEmptyTextView.setVisibility(View.VISIBLE);
                        binding.leaderboardRecyclerView.setVisibility(View.GONE);
                    }
                });
    }

    private void loadInitialLikeSaveStatus() {
        if (currentUser == null || routeId == null) {
            updateLikeSaveButtonsState(); // Aggiorna UI con stato default (non loggato)
            return;
        }

        isLoadingLikeStatus = true;
        isLoadingSaveStatus = true;
        updateLikeSaveButtonsState(); // Mostra pulsanti disabilitati/in caricamento

        // Controlla Like
        likeStatusListener = db.collection("percorsi").document(routeId)
                .collection("likes").document(currentUser.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Like listener failed.", error);
                        // Gestisci errore, magari mostra stato vecchio o default
                        isLiked = false; // Fallback
                    } else {
                        isLiked = snapshot != null && snapshot.exists();
                    }
                    isLoadingLikeStatus = false;
                    updateLikeSaveButtonsState(); // Aggiorna UI del pulsante Like
                });

        // Controlla Save
        saveStatusListener = db.collection("utenti").document(currentUser.getUid())
                .collection("savedRoutes").document(routeId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Save listener failed.", error);
                        isSaved = false; // Fallback
                    } else {
                        isSaved = snapshot != null && snapshot.exists();
                    }
                    isLoadingSaveStatus = false;
                    updateLikeSaveButtonsState(); // Aggiorna UI del pulsante Save
                });

        // Ora che abbiamo caricato lo stato (o iniziato ad ascoltarlo), possiamo impostare i listener dei pulsanti
        setupButtonClickListeners();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Mostra freccia indietro
            getSupportActionBar().setDisplayShowTitleEnabled(false); // Nascondi titolo inizialmente
        }
        // Il titolo verrà impostato quando la barra collassa
        binding.collapsingToolbarLayout.setCollapsedTitleTextColor(ContextCompat.getColor(this, R.color.textColor));
        binding.collapsingToolbarLayout.setExpandedTitleColor(Color.TRANSPARENT); // Nascondi titolo espanso
    }

    private void listenToRouteData() {
        binding.progressBarDetail.setVisibility(View.VISIBLE);
        routeListener = db.collection("percorsi").document(routeId)
                .addSnapshotListener((snapshot, error) -> {
                    binding.progressBarDetail.setVisibility(View.GONE);
                    if (error != null) {
                        Log.e(TAG, "Listen failed.", error);
                        Toast.makeText(RouteDetailActivity.this, R.string.error_loading_route_details, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        Log.d(TAG, "Route data updated.");
                        currentRoute = RouteModel.fromFirestore(snapshot);
                        updateUI(currentRoute);
                    } else {
                        Log.w(TAG, "Route document does not exist (ID: " + routeId + ")");
                        Toast.makeText(RouteDetailActivity.this, R.string.route_detail_not_found, Toast.LENGTH_SHORT).show();
                        // Potresti voler chiudere l'activity o mostrare uno stato vuoto
                        // finish();
                    }
                });
    }

    // Aggiorna la UI con i dati del RouteModel
    private void updateUI(RouteModel route) {
        // Imposta titolo barra collassata
        binding.collapsingToolbarLayout.setTitle(route.getNome());

        // Carica immagine statica mappa
        String mapUrl = route.getStaticMapUrl();
        // TODO: Implementare fallback URL se staticMapUrl è null (come in Flutter)
        if (mapUrl != null && !mapUrl.isEmpty()) {
            Glide.with(this)
                    .load(mapUrl)
                    .centerCrop()
                    .placeholder(R.color.borderColor) // Placeholder grigio
                    .error(R.drawable.ic_map) // Icona mappa in caso di errore
                    .listener(new RequestListener<Drawable>() { // Log per debug immagine
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                            Log.e(TAG, "Glide: Image load failed: " + e);
                            return false; // let Glide handle setting the error placeholder
                        }
                        @Override
                        public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                            Log.d(TAG, "Glide: Image loaded successfully");
                            return false; // let Glide handle setting the image
                        }
                    })
                    .into(binding.staticMapImageView);
        } else {
            binding.staticMapImageView.setImageResource(R.drawable.ic_map); // Immagine di fallback
            binding.staticMapImageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE); // Adatta l'icona
            binding.staticMapImageView.setBackgroundColor(ContextCompat.getColor(this, R.color.borderColor));
        }


        // Popola i campi testo
        binding.routeNameTextView.setText(route.getNome());
        binding.routeDescriptionTextView.setText(
                TextUtils.isEmpty(route.getDescrizione()) ? getString(R.string.no_description_provided) : route.getDescrizione()
        );

        // Aggiorna rating (logica semplificata)
        // TODO: Implementare UI stelle dinamica
        binding.ratingTextView.setText(String.format(Locale.US, "%.1f (%s)",
                route.getAverageRating(),
                getResources().getQuantityString(R.plurals.review_count, route.getReviewCount(), route.getReviewCount()) // Usa plurals
        ));

        // Aggiorna info creatore (usando un layout inflato o accesso diretto se non è complesso)
        // Esempio semplice:
        loadCreatorInfo(route.getCreatoreUid());


        // Aggiorna le Stat Cards
        updateStatCard(binding.distanceStatCard, R.drawable.ic_linear_scale, settingsService.formatDistance(route.getDistanzaKm()), getString(R.string.route_detail_distance), R.color.stat_blue);
        updateStatCard(binding.difficultyStatCard, R.drawable.ic_speed, route.getDifficulty(), getString(R.string.route_detail_difficulty), getDifficultyColorRes(route.getDifficulty()));
        updateStatCard(binding.likesStatCard, R.drawable.ic_favorite, String.valueOf(route.getLikeCount()), getString(R.string.route_detail_likes), R.color.stat_pink); // Usa getLikeCount()
        updateStatCard(binding.savesStatCard, R.drawable.ic_bookmark, String.valueOf(route.getSaveCount()), getString(R.string.route_detail_saves), R.color.stat_purple); // Usa getSaveCount()

        // TODO: Aggiornare stato pulsanti Like/Save (richiede logica separata o Custom View)
        // TODO: Caricare e visualizzare Leaderboard, Recensioni, Discussione
    }

    // Carica e mostra info del creatore
    private void loadCreatorInfo(String creatorUid) {
        if (creatorUid == null || creatorUid.isEmpty()) {
            binding.creatorTextView.setText(R.string.unknown_user); // Stringa per utente sconosciuto
            binding.creatorImageView.setImageResource(R.drawable.ic_person);
            return;
        }

        db.collection("utenti").document(creatorUid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String name = doc.getString("nomeUtente");
                        String imageUrl = doc.getString("profileImageUrl");
                        binding.creatorTextView.setText(getString(R.string.created_by_placeholder, name != null ? name : getString(R.string.unknown_user))); // Crea @string/created_by_placeholder="By: %1$s"

                        Glide.with(this)
                                .load(imageUrl) // Glide gestisce URL null o vuoti
                                .placeholder(R.drawable.ic_person)
                                .error(R.drawable.ic_person)
                                .circleCrop() // Rende l'immagine circolare
                                .into(binding.creatorImageView);

                        // Rendi cliccabile per andare al profilo
                        binding.creatorLayout.setOnClickListener(v -> {
                            // === SCOMMENTA E VERIFICA QUESTE RIGHE ===
                            Intent intent = new Intent(this, UserProfileActivity.class);
                            intent.putExtra("USER_ID", creatorUid); // Passa l'ID del creatore
                            startActivity(intent);
                            // Toast.makeText(this, "Go to profile: " + name, Toast.LENGTH_SHORT).show(); // Rimuovi Toast
                            // === FINE MODIFICA ===
                        });

                    } else {
                        binding.creatorTextView.setText(R.string.unknown_user);
                        binding.creatorImageView.setImageResource(R.drawable.ic_person);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading creator info", e);
                    binding.creatorTextView.setText(R.string.unknown_user);
                    binding.creatorImageView.setImageResource(R.drawable.ic_person);
                });
    }

    // Aggiorna una singola card delle statistiche
    private void updateStatCard(IncludeStatCardBinding statBinding, int iconRes, String value, String label, int colorRes) {
        int color = ContextCompat.getColor(this, colorRes);
        statBinding.getRoot().setCardBackgroundColor(color & 0x1AFFFFFF); // Colore con bassa opacità
        statBinding.statIcon.setImageResource(iconRes);
        ImageViewCompat.setImageTintList(statBinding.statIcon, ColorStateList.valueOf(color)); // Colora l'icona
        statBinding.statValue.setText(value);
        statBinding.statValue.setTextColor(color);
        statBinding.statLabel.setText(label);
        statBinding.statLabel.setTextColor(color); // Usa colore pieno per l'etichetta
    }

    // Ottiene l'ID risorsa colore per la difficoltà
    private int getDifficultyColorRes(String difficulty) {
        if (difficulty == null) return R.color.subtleTextColor;
        switch (difficulty.toLowerCase()) {
            case "easy": return R.color.successColor;
            case "medium": return R.color.warningColor; // Assicurati sia definito
            case "hard": return R.color.dangerColor;
            default: return R.color.subtleTextColor;
        }
    }


    private void setupButtonClickListeners() {
        // Listener vengono impostati solo una volta, ma l'azione dipende dallo stato corrente
        binding.likeButton.setOnClickListener(v -> toggleLike());
        binding.saveButton.setOnClickListener(v -> toggleSave());

        // Listener per Navigazione e Timer (invariati)
        binding.navigateButton.setOnClickListener(v -> launchGoogleMapsNavigation());
        binding.timerButton.setOnClickListener(v -> {
            if (currentRoute != null) {
                Intent intent = new Intent(this, CronometroActivity.class);
                // NOTA: Per passare currentRoute, deve implementare Parcelable
                // Per ora, passiamo solo l'ID e CronometroActivity lo recupererà da Firestore
                intent.putExtra(RouteDetailActivity.EXTRA_ROUTE_ID, currentRoute.getId());
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.error_loading_route_details, Toast.LENGTH_SHORT).show();
            }
        });

        // Pulsante "Aggiungi Recensione"
        binding.addReviewButton.setOnClickListener(v -> {
            AddReviewBottomSheet bottomSheet = AddReviewBottomSheet.newInstance(routeId);
            bottomSheet.show(getSupportFragmentManager(), bottomSheet.getTag());
        });
    }

    private void updateLikeSaveButtonsState() {
        // Pulsante Like
        if (isLoadingLikeStatus) {
            binding.likeButton.setEnabled(false);
            binding.likeButton.setIconResource(R.drawable.ic_hourglass_empty); // Icona caricamento (opzionale)
        } else {
            binding.likeButton.setEnabled(true);
            binding.likeButton.setIconResource(isLiked ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
            // Cambia colore icona/bordo se necessario (puoi usare ColorStateList)
            binding.likeButton.setIconTintResource(isLiked ? R.color.stat_pink : R.color.subtleTextColor);
            binding.likeButton.setStrokeColorResource(isLiked ? R.color.stat_pink_light : R.color.borderColor); // Crea stat_pink_light
        }

        // Pulsante Save
        if (isLoadingSaveStatus) {
            binding.saveButton.setEnabled(false);
            binding.saveButton.setIconResource(R.drawable.ic_hourglass_empty); // Icona caricamento (opzionale)
        } else {
            binding.saveButton.setEnabled(true);
            binding.saveButton.setIconResource(isSaved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
            binding.saveButton.setIconTintResource(isSaved ? R.color.stat_purple : R.color.subtleTextColor);
            binding.saveButton.setStrokeColorResource(isSaved ? R.color.stat_purple_light : R.color.borderColor); // Crea stat_purple_light
        }
    }

    private void toggleLike() {
        if (currentUser == null || routeId == null || isLoadingLikeStatus) return; // Non fare nulla se non loggato o in caricamento

        // Blocca temporaneamente il pulsante per evitare click multipli
        binding.likeButton.setEnabled(false);
        boolean targetState = !isLiked; // Lo stato che vogliamo raggiungere

        Task<Void> task;
        if (targetState) { // Mettere Like
            task = db.collection("percorsi").document(routeId)
                    .collection("likes").document(currentUser.getUid())
                    .set(new HashMap<String, Object>() {{
                        put("likedAt", com.google.firebase.Timestamp.now());
                    }});
        } else { // Togliere Like
            task = db.collection("percorsi").document(routeId)
                    .collection("likes").document(currentUser.getUid())
                    .delete();
        }

        task.addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Like status toggled successfully in Firestore.");
            // Lo stato 'isLiked' e la UI verranno aggiornati automaticamente dal listener
            // Riabilita il pulsante (il listener aggiornerà l'icona)
            if (!isLoadingLikeStatus) binding.likeButton.setEnabled(true);
            // Non è necessario aggiornare il contatore qui, ci pensa la Cloud Function (aggregateLikes)
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error toggling like in Firestore", e);
            Toast.makeText(this, "Error updating like status", Toast.LENGTH_SHORT).show();
            // Ripristina lo stato precedente e riabilita il pulsante
            isLiked = !targetState; // Ripristina stato logico
            if (!isLoadingLikeStatus) {
                updateLikeSaveButtonsState(); // Aggiorna UI allo stato precedente
                binding.likeButton.setEnabled(true);
            }
        });
    }

    // NUOVO: Logica per il toggle del Save
    private void toggleSave() {
        if (currentUser == null || routeId == null || isLoadingSaveStatus) return;

        binding.saveButton.setEnabled(false);
        boolean targetState = !isSaved; // Lo stato che vogliamo raggiungere

        // Riferimenti ai documenti
        DocumentReference userSavedRouteRef = db.collection("utenti").document(currentUser.getUid())
                .collection("savedRoutes").document(routeId);
        DocumentReference routeRef = db.collection("percorsi").document(routeId);

        Task<Void> task;
        FieldValue countChange = targetState ? FieldValue.increment(1) : FieldValue.increment(-1);

        if (targetState) { // Salvare
            task = db.runTransaction(transaction -> {
                transaction.set(userSavedRouteRef, new HashMap<String, Object>() {{
                    put("savedAt", com.google.firebase.Timestamp.now());
                }});
                transaction.update(routeRef, "saveCount", countChange);
                return null; // Successo per la transazione
            });
        } else { // Rimuovere salvataggio
            task = db.runTransaction(transaction -> {
                transaction.delete(userSavedRouteRef);
                transaction.update(routeRef, "saveCount", countChange);
                return null;
            });
        }

        task.addOnSuccessListener(aVoid -> {
            Log.d(TAG, "Save status toggled successfully.");
            // Lo stato 'isSaved' e la UI verranno aggiornati automaticamente dal listener
            if (!isLoadingSaveStatus) binding.saveButton.setEnabled(true);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error toggling save status", e);
            Toast.makeText(this, "Error updating save status", Toast.LENGTH_SHORT).show();
            isSaved = !targetState; // Ripristina
            if (!isLoadingSaveStatus) {
                updateLikeSaveButtonsState();
                binding.saveButton.setEnabled(true);
            }
        });
    }

    private void launchGoogleMapsNavigation() {
        if (currentRoute == null || currentRoute.getTracciato().isEmpty()) {
            Toast.makeText(this, R.string.route_detail_no_coordinates, Toast.LENGTH_SHORT).show();
            return;
        }
        LatLng startPoint = currentRoute.getStartPoint();
        // Uri per avviare la navigazione verso il punto di partenza
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + startPoint.latitude + "," + startPoint.longitude);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Toast.makeText(this, R.string.route_detail_maps_error, Toast.LENGTH_SHORT).show();
            // Fallback: Apri nel browser
            Uri webUri = Uri.parse("https://developers.google.com/maps/documentation/places/web-service/photos" + startPoint.latitude + "," + startPoint.longitude);
            Intent webIntent = new Intent(Intent.ACTION_VIEW, webUri);
            startActivity(webIntent);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Rimuovi TUTTI i listener di Firestore per evitare leak
        if (routeListener != null) {
            routeListener.remove();
        }
        if (likeStatusListener != null) {
            likeStatusListener.remove();
        }
        if (saveStatusListener != null) {
            saveStatusListener.remove();
        }
    }

    // Gestisce il click sulla freccia indietro nella Toolbar
    @Override
    public boolean onSupportNavigateUp() {
        // Non chiamare onBackPressed() due volte, è gestito dal MenuProvider
        finish(); // Chiude semplicemente l'activity
        return true;
    }
}

// TODO: Creare le classi/layout per le sezioni Leaderboard, Reviews, Discussion
// TODO: Creare le Activity mancanti (RouteSearch, Settings, UserProfile, Cronometro, ecc.)
// TODO: Implementare la logica per Like/Save (interazione Firestore e aggiornamento UI)
// TODO: Implementare il modello RouteModel come Parcelable se vuoi passarlo tramite Intent
// TODO: Creare/definire i colori stat_blue, stat_pink, stat_purple in colors.xml
// TODO: Creare la risorsa plurals "review_count" in values/plurals.xml