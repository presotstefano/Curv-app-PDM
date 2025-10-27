package com.spstudio.curv_app.ui.activity;

import android.content.Intent;
import android.content.res.ColorStateList; // Per tinting icone
import android.graphics.Color;
import android.graphics.drawable.Drawable; // Per tinting AppBarLayout offset listener
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat; // Per tinting
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout; // Per AppBar listener
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FieldPath; // <-- AGGIUNGI QUESTA RIGA// Per contatori
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch; // Per Join/Leave
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.EventModel;
import com.spstudio.curv_app.data.model.RouteModel; // Necessario per info percorso
import com.spstudio.curv_app.data.model.UserModel; // Per partecipanti
import com.spstudio.curv_app.databinding.ActivityEventDetailBinding;
import com.spstudio.curv_app.services.SettingsService;
import com.spstudio.curv_app.ui.adapter.EventParticipantAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EventDetailActivity extends AppCompatActivity {

    private static final String TAG = "EventDetailActivity";
    public static final String EXTRA_EVENT_ID = "EVENT_ID";

    private ActivityEventDetailBinding binding;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private SettingsService settingsService;

    private String eventId;
    private EventModel currentEvent;
    private RouteModel currentRoute; // Per info percorso

    private EventParticipantAdapter participantsAdapter;
    private final List<UserModel> participantList = new ArrayList<>();

    private ListenerRegistration eventListener;
    private ListenerRegistration participationListener; // Per stato join/leave
    private ListenerRegistration participantsListener; // Per lista partecipanti

    private boolean isCurrentUserParticipant = false;
    private boolean isProcessingJoinLeave = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        settingsService = SettingsService.getInstance(this);

        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null || eventId.isEmpty()) {
            Log.e(TAG, "Event ID is missing!");
            Toast.makeText(this, R.string.event_detail_loading_error, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbarAndCollapsingBehavior();
        setupParticipantsRecyclerView();
        setupButtonClickListeners();

        listenToEventData(); // Ascolta dati evento principale
        if (currentUser != null) {
            listenToParticipationStatus(); // Ascolta se l'utente è partecipante
        }
        listenToParticipants(); // Ascolta la lista dei partecipanti
    }

    private void setupToolbarAndCollapsingBehavior() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false); // Titolo gestito da CollapsingToolbarLayout
        }

        // Cambia colore freccia indietro in base allo scroll
        binding.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            boolean isCollapsed = Math.abs(verticalOffset) >= appBarLayout.getTotalScrollRange() - binding.toolbar.getHeight();
            Drawable navIcon = binding.toolbar.getNavigationIcon();
            if (navIcon != null) {
                DrawableCompat.setTint(navIcon.mutate(), ContextCompat.getColor(this,
                        isCollapsed ? R.color.textColor : R.color.white));
            }
        });
        binding.collapsingToolbarLayout.setCollapsedTitleTextColor(ContextCompat.getColor(this, R.color.textColor));
        binding.collapsingToolbarLayout.setExpandedTitleColor(Color.TRANSPARENT);
    }

    private void setupParticipantsRecyclerView() {
        participantsAdapter = new EventParticipantAdapter(this);
        binding.participantsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.participantsRecyclerView.setAdapter(participantsAdapter);
        binding.participantsRecyclerView.setNestedScrollingEnabled(false); // Importante
    }

    private void setupButtonClickListeners() {
        binding.joinLeaveButton.setOnClickListener(v -> toggleParticipation());
        binding.routeInfoCard.setOnClickListener(v -> navigateToRouteDetail());
        binding.creatorTextView.setOnClickListener(v -> navigateToCreatorProfile());
        // Aggiungi click listener a creatorImageView se vuoi
        binding.creatorImageView.setOnClickListener(v -> navigateToCreatorProfile());
    }

    // Ascolta i dati principali dell'evento
    private void listenToEventData() {
        binding.progressBarDetail.setVisibility(View.VISIBLE);
        eventListener = db.collection("events").document(eventId)
                .addSnapshotListener((snapshot, error) -> {
                    if (isDestroyed() || binding == null) return; // Controllo sicurezza
                    // Nascondi ProgressBar solo se non c'è errore e snapshot non è null
                    if (error == null && snapshot != null) {
                        binding.progressBarDetail.setVisibility(View.GONE);
                    }

                    if (error != null) {
                        Log.e(TAG, "Event listener failed.", error);
                        Toast.makeText(this, R.string.event_detail_loading_error, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        Log.d(TAG, "Event data updated.");
                        currentEvent = EventModel.fromFirestore(snapshot);
                        updateEventUI(currentEvent);
                        // Carica info percorso SE non già caricate o se routeId è cambiato
                        if (currentRoute == null || !currentRoute.getId().equals(currentEvent.getRouteId())) {
                            fetchRouteDetails(currentEvent.getRouteId());
                        }
                        // Carica info creatore
                        loadCreatorInfo(currentEvent.getCreatedByUid());
                    } else {
                        Log.w(TAG, "Event document does not exist (ID: " + eventId + ")");
                        Toast.makeText(this, R.string.event_detail_not_found, Toast.LENGTH_SHORT).show();
                        binding.progressBarDetail.setVisibility(View.GONE);
                        // Potresti voler chiudere l'activity
                        // finish();
                    }
                });
    }

    // Ascolta se l'utente corrente è partecipante
    private void listenToParticipationStatus() {
        if (currentUser == null) return;
        participationListener = db.collection("events").document(eventId)
                .collection("participants").document(currentUser.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    if (isDestroyed() || binding == null) return;
                    if (error != null) {
                        Log.w(TAG, "Participation listener failed.", error);
                        isCurrentUserParticipant = false; // Fallback
                    } else {
                        isCurrentUserParticipant = snapshot != null && snapshot.exists();
                    }
                    updateJoinLeaveButtonState(); // Aggiorna UI pulsante
                });
    }

    // Ascolta la lista dei partecipanti (UIDs)
    private void listenToParticipants() {
        participantsListener = db.collection("events").document(eventId)
                .collection("participants")
                .limit(20) // Limita per performance
                .addSnapshotListener((snapshots, error) -> {
                    if (isDestroyed() || binding == null) return;
                    if (error != null) {
                        Log.e(TAG, "Participants listener failed.", error);
                        updateParticipantsUI(new ArrayList<>()); // Mostra lista vuota
                        return;
                    }
                    if (snapshots != null) {
                        List<String> participantIds = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            participantIds.add(doc.getId());
                        }
                        fetchParticipantDetails(participantIds); // Recupera i dati completi
                    } else {
                        updateParticipantsUI(new ArrayList<>());
                    }
                });
    }

    // Recupera i dati degli utenti partecipanti dagli ID
    private void fetchParticipantDetails(List<String> participantIds) {
        if (participantIds.isEmpty()) {
            updateParticipantsUI(new ArrayList<>());
            return;
        }

        // Firestore limita 'whereIn' a 10 (ora 30), dividi se necessario
        List<String> queryIds = participantIds.size() > 30 ? participantIds.subList(0, 30) : participantIds;

        db.collection("utenti").whereIn(FieldPath.documentId(), queryIds)
                .get()
                .addOnSuccessListener(userSnapshots -> {
                    if (isDestroyed() || binding == null) return;
                    List<UserModel> fetchedParticipants = new ArrayList<>();
                    for (DocumentSnapshot userDoc : userSnapshots.getDocuments()) {
                        fetchedParticipants.add(UserModel.fromFirestore(userDoc));
                    }
                    updateParticipantsUI(fetchedParticipants);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching participant details", e);
                    // Mostra lista vuota o messaggio di errore
                    updateParticipantsUI(new ArrayList<>());
                });
    }


    // Aggiorna la UI con i dati dell'evento
    private void updateEventUI(EventModel event) {
        binding.collapsingToolbarLayout.setTitle(event.getName());
        binding.eventNameTextView.setText(event.getName());

        if (event.getDateTime() != null) {
            Date date = event.getDateTime().toDate();
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
            binding.dateTimeTextView.setText(sdf.format(date));
        } else {
            binding.dateTimeTextView.setText("Date TBD");
        }

        if (!TextUtils.isEmpty(event.getDescription())) {
            binding.eventDescriptionTextView.setText(event.getDescription());
            binding.eventDescriptionTextView.setVisibility(View.VISIBLE);
        } else {
            binding.eventDescriptionTextView.setVisibility(View.GONE);
        }

        // Aggiorna titolo sezione partecipanti con il conteggio
        binding.participantsTitleTextView.setText(getString(R.string.event_detail_participants_title, event.getParticipantCount()));

        // Carica banner
        String bannerUrl = event.getBannerUrl();
        if (bannerUrl != null && !bannerUrl.isEmpty()) {
            Glide.with(this).load(bannerUrl).centerCrop().into(binding.eventBannerImageView);
        } else {
            // Metti un'immagine di default o colore
            binding.eventBannerImageView.setImageResource(R.drawable.ic_map); // Placeholder
            binding.eventBannerImageView.setScaleType(ImageView.ScaleType.CENTER);
        }
    }

    // Carica dati del percorso associato
    private void fetchRouteDetails(String routeIdToFetch) {
        if (routeIdToFetch == null || routeIdToFetch.isEmpty()) {
            Log.w(TAG, "Route ID is missing for the event.");
            binding.routeInfoNameTextView.setText("Route info unavailable");
            binding.routeInfoDistanceTextView.setText("");
            binding.routeInfoCard.setClickable(false);
            return;
        }
        db.collection("percorsi").document(routeIdToFetch).get()
                .addOnSuccessListener(doc -> {
                    if (isDestroyed() || binding == null) return;
                    if (doc.exists()) {
                        currentRoute = RouteModel.fromFirestore(doc);
                        binding.routeInfoNameTextView.setText(currentRoute.getNome());
                        binding.routeInfoDistanceTextView.setText("(" + settingsService.formatDistance(currentRoute.getDistanzaKm()) + ")");
                        binding.routeInfoCard.setClickable(true);
                    } else {
                        Log.w(TAG, "Associated route document not found: " + routeIdToFetch);
                        binding.routeInfoNameTextView.setText("Route not found");
                        binding.routeInfoDistanceTextView.setText("");
                        binding.routeInfoCard.setClickable(false);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching route details", e);
                    binding.routeInfoNameTextView.setText("Error loading route info");
                    binding.routeInfoDistanceTextView.setText("");
                    binding.routeInfoCard.setClickable(false);
                });
    }

    // Carica info creatore (simile a RouteDetailActivity)
    private void loadCreatorInfo(String creatorUid) {
        if (creatorUid == null || creatorUid.isEmpty()) {
            binding.creatorTextView.setText(R.string.unknown_user);
            binding.creatorImageView.setImageResource(R.drawable.ic_person);
            return;
        }
        db.collection("utenti").document(creatorUid).get()
                .addOnSuccessListener(doc -> {
                    if (isDestroyed() || binding == null) return;
                    if (doc.exists()) {
                        String name = doc.getString("nomeUtente");
                        String imageUrl = doc.getString("profileImageUrl");
                        binding.creatorTextView.setText(getString(R.string.created_by_placeholder, name != null ? name : getString(R.string.unknown_user)));
                        Glide.with(this).load(imageUrl).placeholder(R.drawable.ic_person).error(R.drawable.ic_person).circleCrop().into(binding.creatorImageView);
                    } else {
                        binding.creatorTextView.setText(R.string.unknown_user);
                        binding.creatorImageView.setImageResource(R.drawable.ic_person);
                    }
                });
        // Aggiungere OnFailureListener se necessario
    }

    // Aggiorna la UI della lista partecipanti
    private void updateParticipantsUI(List<UserModel> participants) {
        participantList.clear();
        if (participants != null) {
            participantList.addAll(participants);
        }
        participantsAdapter.updateData(participantList); // Aggiorna adapter
        binding.participantsEmptyTextView.setVisibility(participantList.isEmpty() ? View.VISIBLE : View.GONE);
        binding.participantsRecyclerView.setVisibility(participantList.isEmpty() ? View.GONE : View.VISIBLE);
        // Aggiorna il titolo (anche se il conteggio viene da currentEvent)
        binding.participantsTitleTextView.setText(getString(R.string.event_detail_participants_title, participantList.size()));

    }

    // Aggiorna aspetto e testo del pulsante Join/Leave
    private void updateJoinLeaveButtonState() {
        if (isCurrentUserParticipant) {
            binding.joinLeaveButton.setText(R.string.event_detail_leave_button);
            // Stile "unisciti" (es. Outlined)
            binding.joinLeaveButton.setIconResource(R.drawable.ic_logout); // Icona leave
            binding.joinLeaveButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.dangerColor))); // Sfondo Rosso
            binding.joinLeaveButton.setTextColor(ContextCompat.getColor(this, R.color.white));
            binding.joinLeaveButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white)));
        } else {
            binding.joinLeaveButton.setText(R.string.event_detail_join_button);
            // Stile normale (es. Filled)
            binding.joinLeaveButton.setIconResource(R.drawable.ic_login); // Icona join (o altra)
            binding.joinLeaveButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.primaryColor))); // Sfondo primario
            binding.joinLeaveButton.setTextColor(ContextCompat.getColor(this, R.color.white));
            binding.joinLeaveButton.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white)));
        }
        binding.joinLeaveButton.setEnabled(!isProcessingJoinLeave); // Abilita/Disabilita
    }


    // Logica per Join/Leave
    private void toggleParticipation() {
        if (currentUser == null || eventId == null || isProcessingJoinLeave) return;

        isProcessingJoinLeave = true;
        updateJoinLeaveButtonState(); // Disabilita pulsante
        boolean targetJoinState = !isCurrentUserParticipant; // Stato desiderato

        WriteBatch batch = db.batch();
        DocumentReference participantRef = db.collection("events").document(eventId)
                .collection("participants").document(currentUser.getUid());
        DocumentReference eventRef = db.collection("events").document(eventId);
        FieldValue countChange = targetJoinState ? FieldValue.increment(1) : FieldValue.increment(-1);

        if (targetJoinState) { // Join
            Toast.makeText(this, R.string.event_detail_joining, Toast.LENGTH_SHORT).show();
            // Aggiungi utente alla sottocollezione
            Map<String, Object> participantData = new HashMap<>();
            participantData.put("joinedAt", Timestamp.now());
            // Aggiungi altri dati se necessario (es. nome utente per query future)
            batch.set(participantRef, participantData);
            // Incrementa contatore
            batch.update(eventRef, "participantCount", countChange);
        } else { // Leave
            Toast.makeText(this, R.string.event_detail_leaving, Toast.LENGTH_SHORT).show();
            // Rimuovi utente dalla sottocollezione
            batch.delete(participantRef);
            // Decrementa contatore
            batch.update(eventRef, "participantCount", countChange);
        }

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Participation toggled successfully.");
                    isProcessingJoinLeave = false;
                    // La UI del pulsante si aggiornerà automaticamente grazie al participationListener
                    Toast.makeText(this, targetJoinState ? R.string.event_detail_join_success : R.string.event_detail_leave_success, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error toggling participation", e);
                    isProcessingJoinLeave = false;
                    // Riabilita e ripristina lo stato UI precedente
                    isCurrentUserParticipant = !targetJoinState; // Ripristina stato logico
                    updateJoinLeaveButtonState();
                    Toast.makeText(this, targetJoinState ? getString(R.string.event_detail_join_error, e.getMessage()) : getString(R.string.event_detail_leave_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
    }

    // Naviga a RouteDetailActivity
    private void navigateToRouteDetail() {
        if (currentEvent != null && !currentEvent.getRouteId().isEmpty()) {
            Intent intent = new Intent(this, RouteDetailActivity.class);
            intent.putExtra(RouteDetailActivity.EXTRA_ROUTE_ID, currentEvent.getRouteId());
            startActivity(intent);
        } else {
            Toast.makeText(this, "Route information unavailable.", Toast.LENGTH_SHORT).show();
        }
    }

    // Naviga a UserProfileActivity
    private void navigateToCreatorProfile() {
        if (currentEvent != null && !currentEvent.getCreatedByUid().isEmpty()) {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra("USER_ID", currentEvent.getCreatedByUid());
            startActivity(intent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Rimuovi tutti i listener
        if (eventListener != null) eventListener.remove();
        if (participationListener != null) participationListener.remove();
        if (participantsListener != null) participantsListener.remove();
        binding = null; // Pulisci ViewBinding
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}