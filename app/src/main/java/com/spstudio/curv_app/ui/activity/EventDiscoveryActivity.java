package com.spstudio.curv_app.ui.activity;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.Timestamp; // Importa Timestamp
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.EventModel;
import com.spstudio.curv_app.databinding.ActivityEventDiscoveryBinding;
import com.spstudio.curv_app.ui.adapter.EventAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EventDiscoveryActivity extends AppCompatActivity implements SwipeRefreshLayout.OnRefreshListener {

    private static final String TAG = "EventDiscoveryActivity";

    private ActivityEventDiscoveryBinding binding;
    private FirebaseFirestore db;
    private EventAdapter adapter;
    private final List<EventModel> eventList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEventDiscoveryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        db = FirebaseFirestore.getInstance();

        // Setup Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        setupRecyclerView();
        binding.swipeRefreshLayout.setOnRefreshListener(this); // Imposta listener per pull-to-refresh
        binding.swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.primaryColor));

        loadEvents(); // Carica eventi all'avvio
    }

    private void setupRecyclerView() {
        // === VERIFICA QUESTA RIGA ===
        adapter = new EventAdapter(this); // Passa SOLO il contesto
        // === FINE VERIFICA ===
        binding.eventsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.eventsRecyclerView.setAdapter(adapter);
    }

    // Metodo chiamato sia all'avvio che dal pull-to-refresh
    private void loadEvents() {
        Log.d(TAG, "Loading events...");
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.eventsRecyclerView.setVisibility(View.GONE);
        binding.emptyTextView.setVisibility(View.GONE);
        // === LOG Timestamp usato nella query ===
        Timestamp now = Timestamp.now();
        Log.d(TAG, "Querying events with dateTime >= " + now.toDate().toString());
        // Query: prendi eventi futuri, ordina per data
        db.collection("events")
                .whereGreaterThanOrEqualTo("dateTime", now) // Usa la variabile 'now'
                .orderBy("dateTime", Query.Direction.ASCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (isDestroyed() || binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);
                    binding.swipeRefreshLayout.setRefreshing(false);

                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        eventList.clear();
                        if (querySnapshot != null && !querySnapshot.isEmpty()) {
                            Log.d(TAG, "Query successful, found " + querySnapshot.size() + " documents."); // Log esistente
                            for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                // === LOG prima del parsing ===
                                Log.d(TAG, "  -> Processing event doc ID: " + doc.getId());
                                Map<String, Object> data = doc.getData(); // Prendi i dati
                                Log.d(TAG, "     Raw data: " + (data != null ? data.toString() : "null")); // Stampa i dati grezzi
                                // ===========================
                                try {
                                    EventModel event = EventModel.fromFirestore(doc);
                                    eventList.add(event);
                                    // === LOG dopo il parsing ===
                                    Log.d(TAG, "     Parsed successfully: " + event.getName());
                                    // ==========================
                                } catch (Exception e) {
                                    // Errore durante il parsing
                                    Log.e(TAG, "     ERROR parsing event document " + doc.getId(), e);
                                }
                            }
                        } else {
                            Log.d(TAG, "Query successful, but no upcoming events found (snapshot empty or null).");
                        }
                        adapter.updateData(eventList);
                        updateEmptyState();
                    } else {
                        Log.e(TAG, "Error loading events", task.getException());
                        Toast.makeText(EventDiscoveryActivity.this, R.string.event_discovery_error, Toast.LENGTH_SHORT).show();
                        updateEmptyState();
                    }
                });
    }

    private void updateEmptyState() {
        // === AGGIUNGI QUESTI LOG ===
        Log.d(TAG, "updateEmptyState called. eventList size: " + eventList.size());
        if (eventList.isEmpty()) {
            binding.eventsRecyclerView.setVisibility(View.GONE);
            binding.emptyTextView.setVisibility(View.VISIBLE);
            Log.d(TAG, "  -> Showing empty text view.");
        } else {
            binding.eventsRecyclerView.setVisibility(View.VISIBLE);
            binding.emptyTextView.setVisibility(View.GONE);
            Log.d(TAG, "  -> Showing recycler view.");
        }
        // === FINE LOG AGGIUNTI ===
    }

    // Implementazione OnRefreshListener
    @Override
    public void onRefresh() {
        Log.d(TAG, "Pull to refresh triggered.");
        loadEvents(); // Ricarica gli eventi
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null; // Pulisci ViewBinding
    }

    // Gestisce il click sulla freccia indietro
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}