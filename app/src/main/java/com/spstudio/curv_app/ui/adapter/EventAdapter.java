package com.spstudio.curv_app.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.util.Log; // Aggiungi import per Log
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.EventModel;
import com.spstudio.curv_app.services.SettingsService;
import com.spstudio.curv_app.ui.activity.EventDetailActivity;
// import com.spstudio.curv_app.ui.activity.EventDetailActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    // === MODIFICA 1: Lista interna final e inizializzata ===
    private final List<EventModel> events = new ArrayList<>();
    private final Context context;
    private final SettingsService settingsService;

    // Costruttore riceve solo Context
    public EventAdapter(Context context) {
        this.context = context;
        this.settingsService = SettingsService.getInstance(context);
        // NON passare la lista nel costruttore
    }

    // === MODIFICA 2: updateData pulisce e copia nella lista interna ===
    public void updateData(List<EventModel> newEvents) {
        Log.d("EventAdapter", "Adapter updateData called with " + (newEvents != null ? newEvents.size() : 0) + " events.");

        this.events.clear(); // Pulisce la lista INTERNA dell'adapter
        if (newEvents != null) {
            this.events.addAll(newEvents); // Aggiunge i nuovi elementi alla lista INTERNA
        }

        Log.d("EventAdapter", "Adapter internal list size is now: " + this.events.size());
        notifyDataSetChanged(); // Notifica al RecyclerView
        Log.d("EventAdapter", "notifyDataSetChanged() called.");
    }
    // === FINE MODIFICHE ===

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        EventModel event = events.get(position);
        holder.bind(event, context, settingsService);

        holder.itemView.setOnClickListener(v -> {
            // === MODIFICA QUI ===
            Intent intent = new Intent(context, EventDetailActivity.class);
            intent.putExtra(EventDetailActivity.EXTRA_EVENT_ID, event.getId()); // Passa l'ID
            context.startActivity(intent);
            // Toast.makeText(context, "View event: " + event.getName(), Toast.LENGTH_SHORT).show(); // Rimuovi placeholder
            // === FINE MODIFICA ===
        });
    }

    @Override
    public int getItemCount() {
        Log.d("EventAdapter", "getItemCount() called, returning: " + events.size());
        return events.size(); // Ritorna la dimensione della lista interna
    }

    // --- ViewHolder (Invariato) ---
    static class EventViewHolder extends RecyclerView.ViewHolder {
        ImageView eventBannerImageView;
        TextView eventNameTextView;
        TextView dateTimeTextView;
        TextView routeInfoTextView;
        TextView participantsTextView;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            eventBannerImageView = itemView.findViewById(R.id.eventBannerImageView);
            eventNameTextView = itemView.findViewById(R.id.eventNameTextView);
            dateTimeTextView = itemView.findViewById(R.id.dateTimeTextView);
            routeInfoTextView = itemView.findViewById(R.id.routeInfoTextView);
            participantsTextView = itemView.findViewById(R.id.participantsTextView);
        }

        public void bind(EventModel event, Context context, SettingsService settingsService) {
            eventNameTextView.setText(event.getName());

            if (event.getDateTime() != null) {
                Date date = event.getDateTime().toDate();
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
                dateTimeTextView.setText(sdf.format(date));
            } else {
                dateTimeTextView.setText("Date TBD");
            }

            String routeName = event.getRouteName() != null ? event.getRouteName() : "Unknown Route";
            String distanceFormatted = settingsService.formatDistance(event.getRouteDistanceKm());
            routeInfoTextView.setText(context.getString(R.string.event_detail_route_info, routeName, distanceFormatted));

            int count = event.getParticipantCount();
            participantsTextView.setText(context.getResources().getQuantityString(R.plurals.participant_count, count, count));

            String bannerUrl = event.getBannerUrl();
            if (bannerUrl != null && !bannerUrl.isEmpty()) {
                eventBannerImageView.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(bannerUrl)
                        .centerCrop()
                        .placeholder(R.color.backgroundColor)
                        .error(R.color.borderColor)
                        .into(eventBannerImageView);
            } else {
                eventBannerImageView.setVisibility(View.GONE);
            }
        }
    }
}