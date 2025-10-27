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
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.EventModel;
import com.spstudio.curv_app.databinding.ActivityEventDiscoveryBinding;
import com.spstudio.curv_app.ui.adapter.EventAdapter;

import java.util.ArrayList;
import java.util.List;

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
        adapter = new EventAdapter(this, eventList);
        binding.eventsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.eventsRecyclerView.setAdapter(adapter);
    }

    // Metodo chiamato sia all'avvio che dal pull-to-refresh
    private void loadEvents() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.eventsRecyclerView.setVisibility(View.GONE);
        binding.emptyTextView.setVisibility(View.GONE);

        // Query: prendi eventi futuri, ordina per data
        db.collection("events")
                .whereGreaterThanOrEqualTo("dateTime", Timestamp.now()) // Solo eventi futuri o in corso
                .orderBy("dateTime", Query.Direction.ASCENDING)
                // .limit(20) // Considera paginazione futura
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (isDestroyed()) return;
                    binding.progressBar.setVisibility(View.GONE);
                    binding.swipeRefreshLayout.setRefreshing(false);
                    eventList.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        eventList.add(EventModel.fromFirestore(doc));
                    }
                    adapter.updateData(eventList);
                    updateEmptyState();
                    Log.d(TAG, "Loaded " + eventList.size() + " upcoming events.");
                })
                .addOnFailureListener(e -> {
                    if (isDestroyed()) return;
                    Log.e(TAG, "Error loading events", e);
                    binding.progressBar.setVisibility(View.GONE);
                    binding.swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(EventDiscoveryActivity.this, R.string.event_discovery_error, Toast.LENGTH_SHORT).show();
                    updateEmptyState();
                });
    }

    private void updateEmptyState() {
        if (eventList.isEmpty()) {
            binding.eventsRecyclerView.setVisibility(View.GONE);
            binding.emptyTextView.setVisibility(View.VISIBLE);
        } else {
            binding.eventsRecyclerView.setVisibility(View.VISIBLE);
            binding.emptyTextView.setVisibility(View.GONE);
        }
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