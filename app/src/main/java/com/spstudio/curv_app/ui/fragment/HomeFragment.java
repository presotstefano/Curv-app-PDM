package com.spstudio.curv_app.ui.fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Observer;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.data.model.RouteModel;
import com.spstudio.curv_app.databinding.FragmentHomeBinding; // Importa ViewBinding per il Fragment
import com.spstudio.curv_app.services.SettingsService;
import com.spstudio.curv_app.ui.activity.RouteDetailActivity;
// Importa le activity che creerai (o commenta temporaneamente)
// import com.spstudio.curv_app.ui.activity.RouteDetailActivity;
// import com.spstudio.curv_app.ui.activity.RouteSearchActivity;
// import com.spstudio.curv_app.ui.activity.SettingsActivity;
// import com.spstudio.curv_app.ui.activity.UserProfileActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class HomeFragment extends Fragment implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private static final String TAG = "HomeFragment";

    private FragmentHomeBinding binding; // Usa ViewBinding per il Fragment
    private GoogleMap googleMap;
    private SupportMapFragment mapFragment;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private SettingsService settingsService;

    private final List<RouteModel> allRoutes = new ArrayList<>();
    private final Map<Marker, RouteModel> markerRouteMap = new HashMap<>();
    private String selectedDifficultyFilter = null;
    private int currentMapStyleRawId = R.raw.map_style_standard; // Default stile mappa (crea questo file in res/raw)

    // Per la posizione utente
    private FusedLocationProviderClient fusedLocationClient;
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    enableMyLocation();
                    centerMapOnUserLocation(true); // Centra dopo aver ottenuto il permesso
                } else {
                    Toast.makeText(requireContext(), R.string.home_location_permission_denied, Toast.LENGTH_LONG).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        // Ottieni l'istanza del SettingsService (assicurati sia inizializzato nell'Application)
        settingsService = SettingsService.getInstance(requireContext());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // Ottieni il SupportMapFragment e registra il callback
        FragmentManager fm = getChildFragmentManager();
        mapFragment = (SupportMapFragment) fm.findFragmentById(R.id.map_container);
        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            fm.beginTransaction().replace(R.id.map_container, mapFragment).commit();
        }
        // getMapAsync notifica quando la mappa è pronta tramite onMapReady
        mapFragment.getMapAsync(this);

        setupButtonClickListeners();
        observeSettings();

        // Carica i percorsi SE il paese è già disponibile all'avvio del fragment
        if (settingsService.getPreferredCountry() != null) {
            loadRoutesBasedOnCountry(settingsService.getPreferredCountry());
        } else {
            Log.d(TAG, "Preferred country not yet available. Waiting for SettingsService update.");
            binding.progressBarRoutes.setVisibility(View.VISIBLE); // Mostra caricamento iniziale
        }
    }

    // Questo metodo viene chiamato quando la mappa è pronta per essere usata
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        Log.d(TAG, "GoogleMap is ready.");
        this.googleMap = map;

        // Configura interazioni e UI della mappa
        googleMap.getUiSettings().setMapToolbarEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false); // Useremo il nostro FAB
        googleMap.getUiSettings().setCompassEnabled(false);
        googleMap.setOnMarkerClickListener(this); // Imposta questo Fragment come listener per i click sui marker

        // Applica lo stile iniziale (recuperato da SettingsService)
        currentMapStyleRawId = getStyleRawId(settingsService.getMapStyleUri());
        applyMapStyle();

        // Abilita il layer della posizione utente se i permessi sono concessi
        enableMyLocation();

        // Centra la mappa sulla posizione utente (se possibile) o su una posizione di default all'avvio
        centerMapOnUserLocation(true);

        // Se i percorsi sono già stati caricati nel frattempo, disegnali
        if (!allRoutes.isEmpty()) {
            redrawMarkersOnMap();
        }
    }

    // Osserva i cambiamenti nelle preferenze utente tramite SettingsService
    private void observeSettings() {
        // Osserva cambio stile mappa
        settingsService.getMapStyleUriLiveData().observe(getViewLifecycleOwner(), styleIdentifier -> {
            int newStyleRawId = getStyleRawId(styleIdentifier);
            if (googleMap != null && newStyleRawId != currentMapStyleRawId) {
                Log.d(TAG, "Map style changed, applying new style.");
                currentMapStyleRawId = newStyleRawId;
                applyMapStyle();
            }
        });

        // Osserva cambio paese preferito
        settingsService.getPreferredCountryLiveData().observe(getViewLifecycleOwner(), countryCode -> {
            if (countryCode != null) {
                Log.d(TAG, "Preferred country changed/loaded: " + countryCode);
                // Ricarica i percorsi solo se il paese è cambiato effettivamente
                // Questo evita ricaricamenti non necessari se il LiveData notifica lo stesso valore
                if (allRoutes.isEmpty() || !Objects.equals(allRoutes.get(0).getCountryCode(), countryCode)) {
                    loadRoutesBasedOnCountry(countryCode);
                }
            } else {
                Log.d(TAG, "Preferred country removed.");
                allRoutes.clear();
                markerRouteMap.clear();
                if (googleMap != null) {
                    googleMap.clear();
                }
                binding.progressBarRoutes.setVisibility(View.GONE);
            }
        });
    }

    // Mappa gli identificatori salvati agli ID delle risorse raw JSON
    private int getStyleRawId(String styleIdentifier) {
        if (styleIdentifier == null) return R.raw.map_style_standard; // Default (crea questo file)

        switch (styleIdentifier) {
            // Usa gli identificatori che salvi in SettingsService
            case "mapbox://styles/mapbox/streets-v11": return R.raw.map_style_standard;
            case "mapbox://styles/mapbox/outdoors-v11": return R.raw.map_style_outdoors; // Crea map_style_outdoors.json
            case "mapbox://styles/mapbox/light-v10": return R.raw.map_style_light;   // Crea map_style_light.json
            case "mapbox://styles/mapbox/dark-v10": return R.raw.map_style_dark;    // Crea map_style_dark.json
            case "mapbox://styles/mapbox/satellite-v9": return R.raw.map_style_satellite; // Questo richiede tipo mappa SATELLITE
            default: return R.raw.map_style_standard;
        }
    }

    // Applica lo stile alla mappa
    private void applyMapStyle() {
        if (googleMap == null) return;

        if (currentMapStyleRawId == R.raw.map_style_satellite) {
            googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            googleMap.setMapStyle(null); // Rimuove stili JSON personalizzati
            Log.d(TAG, "Applied Satellite map type.");
        } else {
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            try {
                boolean success = googleMap.setMapStyle(
                        MapStyleOptions.loadRawResourceStyle(requireContext(), currentMapStyleRawId));
                if (!success) {
                    Log.e(TAG, "Style parsing failed for resource ID: " + currentMapStyleRawId);
                } else {
                    Log.d(TAG, "Applied map style from raw resource: " + currentMapStyleRawId);
                }
            } catch (Exception e) { // Era Resources.NotFoundException
                Log.e(TAG, "Can't find style JSON file for resource ID: " + currentMapStyleRawId, e);
            }
        }
    }

    // Abilita il punto blu della posizione utente sulla mappa
    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (googleMap != null && hasLocationPermission()) {
            googleMap.setMyLocationEnabled(true);
            Log.d(TAG, "My Location layer enabled.");
            // Le UI settings per il pulsante MyLocation sono già false
        } else {
            Log.d(TAG, "My Location layer cannot be enabled: Map not ready or permission denied.");
        }
    }

    // Imposta i listener per i click sui pulsanti
    private void setupButtonClickListeners() {
        binding.searchCardView.setOnClickListener(v -> {
            // TODO: Navigare a RouteSearchActivity
            // Intent intent = new Intent(requireActivity(), RouteSearchActivity.class);
            // startActivity(intent);
            Toast.makeText(requireContext(), R.string.home_search_hint, Toast.LENGTH_SHORT).show();
        });

        binding.profileButton.setOnClickListener(v -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user != null) {
                // TODO: Navigare a UserProfileActivity
                // Intent intent = new Intent(requireActivity(), UserProfileActivity.class);
                // intent.putExtra("USER_ID", user.getUid()); // Passa l'ID utente
                // startActivity(intent);
                Toast.makeText(requireContext(), R.string.main_nav_profile, Toast.LENGTH_SHORT).show();
            }
        });

        binding.refreshButton.setOnClickListener(v -> {
            Toast.makeText(requireContext(), R.string.home_refreshing_routes, Toast.LENGTH_SHORT).show();
            if (settingsService.getPreferredCountry() != null) {
                loadRoutesBasedOnCountry(settingsService.getPreferredCountry());
            } else {
                Toast.makeText(requireContext(), "Select preferred country first", Toast.LENGTH_SHORT).show();
            }
        });
        binding.layersButton.setOnClickListener(v -> showStyleSelector());
        binding.filterButton.setOnClickListener(v -> showDifficultyFilterSheet());
        binding.settingsButton.setOnClickListener(v -> {
            // TODO: Navigare a SettingsActivity
            // Intent intent = new Intent(requireActivity(), SettingsActivity.class);
            // startActivity(intent);
            Toast.makeText(requireContext(), "Settings Clicked", Toast.LENGTH_SHORT).show();
        });
        binding.locationButton.setOnClickListener(v -> centerMapOnUserLocation(false)); // Non è il caricamento iniziale
    }

    // Carica i percorsi da Firestore filtrando per paese
    private void loadRoutesBasedOnCountry(String countryCode) {
        Log.d(TAG, "Loading routes for country: " + countryCode);
        if (countryCode == null || countryCode.isEmpty()) {
            Log.w(TAG, "Cannot load routes: Country code is null or empty.");
            binding.progressBarRoutes.setVisibility(View.GONE);
            // Potresti mostrare un messaggio all'utente per selezionare un paese
            return;
        }
        binding.progressBarRoutes.setVisibility(View.VISIBLE);
        db.collection("percorsi")
                .whereEqualTo("status", "approved")
                .whereEqualTo("countryCode", countryCode)
                .orderBy("dataCreazione", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded()) return; // Controlla se il fragment è ancora attaccato all'activity
                    binding.progressBarRoutes.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        QuerySnapshot snapshot = task.getResult();
                        allRoutes.clear();
                        if (snapshot != null && !snapshot.isEmpty()) {
                            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                                allRoutes.add(RouteModel.fromFirestore(doc));
                            }
                            Log.d(TAG, "Loaded " + allRoutes.size() + " routes.");
                        } else {
                            Log.d(TAG, "No approved routes found for country: " + countryCode);
                        }
                        redrawMarkersOnMap(); // Aggiorna la mappa con i nuovi percorsi
                    } else {
                        // Stampa l'eccezione completa per un debug migliore
                        Log.e(TAG, "Error loading routes: ", task.getException());
                        // Mostra un messaggio generico all'utente
                        if (getContext() != null) { // Controlla se il contesto è ancora valido
                            Toast.makeText(getContext(), "Error loading routes", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    // Ridisegna i marker sulla mappa (pulendo quelli vecchi)
    private void redrawMarkersOnMap() {
        if (googleMap == null) return; // Mappa non ancora pronta
        Log.d(TAG, "Redrawing markers...");

        googleMap.clear(); // Rimuove tutti i marker, polilinee, ecc. precedenti
        markerRouteMap.clear(); // Pulisce la mappa di associazione

        List<RouteModel> routesToDisplay = new ArrayList<>();
        if (selectedDifficultyFilter != null) {
            for (RouteModel route : allRoutes) {
                // Confronto case-insensitive
                if (route.getDifficulty().equalsIgnoreCase(selectedDifficultyFilter)) {
                    routesToDisplay.add(route);
                }
            }
        } else {
            routesToDisplay.addAll(allRoutes);
        }

        if (routesToDisplay.isEmpty()) {
            Log.d(TAG,"No routes to display after filtering.");
            return;
        }

        for (RouteModel route : routesToDisplay) {
            BitmapDescriptor markerIcon = createMarkerIcon(getDifficultyColor(route.getDifficulty()));
            if (markerIcon != null) {
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(route.getStartPoint()) // Usa LatLng da RouteModel
                        .icon(markerIcon)
                        .anchor(0.5f, 0.5f); // Centra l'icona sul punto geografico

                Marker marker = googleMap.addMarker(markerOptions);
                if (marker != null) {
                    marker.setTag(route.getId()); // Memorizza l'ID route nel tag del marker
                    markerRouteMap.put(marker, route); // Associa l'oggetto Marker all'oggetto RouteModel
                }
            } else {
                Log.w(TAG, "Could not create marker icon for route: " + route.getId());
            }
        }
        Log.d(TAG, "Added " + markerRouteMap.size() + " markers to the map.");
    }

    // Crea un'icona Bitmap personalizzata per il marker
    private BitmapDescriptor createMarkerIcon(int color) {
        // Dimensioni dell'icona (più piccola per Google Maps rispetto a Mapbox di solito)
        int diameter = 64; // Diametro totale in pixel
        int coreDiameter = 32;
        int borderDiameter = 40;
        Bitmap bitmap = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Calcola alpha (trasparenza) per l'alone (es. 30%)
        int alpha = (int) (255 * 0.3);
        haloPaint.setColor(color);
        haloPaint.setAlpha(alpha);

        borderPaint.setColor(ContextCompat.getColor(requireContext(), R.color.white)); // Colore bianco per il bordo
        corePaint.setColor(color); // Colore pieno per il nucleo

        float center = diameter / 2f;
        canvas.drawCircle(center, center, diameter / 2f, haloPaint); // Disegna l'alone esterno
        canvas.drawCircle(center, center, borderDiameter / 2f, borderPaint); // Disegna il bordo bianco
        canvas.drawCircle(center, center, coreDiameter / 2f, corePaint); // Disegna il nucleo colorato

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    // Ottiene il colore (@ColorInt) in base alla stringa di difficoltà
    private int getDifficultyColor(String difficulty) {
        Context context = getContext();
        if (context == null || difficulty == null) return Color.GRAY; // Fallback sicuro

        switch (difficulty.toLowerCase()) {
            case "easy": return ContextCompat.getColor(context, R.color.successColor);
            case "medium": return ContextCompat.getColor(context, R.color.warningColor);
            case "hard": return ContextCompat.getColor(context, R.color.dangerColor);
            default: return ContextCompat.getColor(context, R.color.subtleTextColor);
        }
    }

    // Gestisce il click su un marker della mappa
    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        Log.d(TAG, "Marker clicked: " + marker.getId() + ", Tag: " + marker.getTag());
        RouteModel clickedRoute = markerRouteMap.get(marker); // Recupera RouteModel dalla mappa usando l'oggetto Marker
        if (clickedRoute != null) {
            showRoutePreviewSheet(clickedRoute);
        } else {
            // Potrebbe essere il marker "My Location", gestiscilo se necessario
            Log.w(TAG, "No RouteModel found for marker: " + marker.getId());
        }
        return true; // Consuma l'evento, evita che appaia l'InfoWindow di default di Google Maps
    }

    // Mostra il BottomSheet con l'anteprima del percorso
    private void showRoutePreviewSheet(RouteModel route) {
        // Controlla se il contesto è valido prima di creare il Dialog
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "Context is null, cannot show BottomSheet.");
            return;
        }

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
        // Usa ViewBinding anche per il layout del BottomSheet per sicurezza
        // Assumi che il layout si chiami bottom_sheet_route_preview.xml
        // Crea una classe di binding: BottomSheetRoutePreviewBinding
        // Esempio: BottomSheetRoutePreviewBinding sheetBinding = BottomSheetRoutePreviewBinding.inflate(LayoutInflater.from(context));
        View sheetView = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_route_preview, null);
        bottomSheetDialog.setContentView(sheetView);

        // Trova le view tramite ID (o usa sheetBinding se hai creato la classe)
        TextView routeName = sheetView.findViewById(R.id.routeNameTextView);
        TextView distanceText = sheetView.findViewById(R.id.distanceTextView);
        TextView difficultyText = sheetView.findViewById(R.id.difficultyTextView);
        TextView ratingText = sheetView.findViewById(R.id.ratingTextView);
        MaterialButton viewDetailsButton = sheetView.findViewById(R.id.viewDetailsButton);
        View difficultyChip = sheetView.findViewById(R.id.difficultyChipView);

        routeName.setText(route.getNome());
        distanceText.setText(settingsService.formatDistance(route.getDistanzaKm()));
        difficultyText.setText(route.getDifficulty());
        ratingText.setText(String.format(Locale.US, "%.1f (%d)", route.getAverageRating(), route.getReviewCount()));

        // Imposta colore background chip difficoltà
        Drawable chipBackground = ContextCompat.getDrawable(context, R.drawable.chip_background_colored);
        if (chipBackground != null) {
            // Clona il drawable prima di modificarlo per evitare effetti collaterali
            Drawable mutableBackground = chipBackground.mutate();
            mutableBackground.setTint(getDifficultyColor(route.getDifficulty()));
            difficultyChip.setBackground(mutableBackground);
            // Imposta il colore del testo su bianco per leggibilità
            difficultyText.setTextColor(ContextCompat.getColor(context, R.color.white));
            // Fai lo stesso per l'icona nel chip se necessario
            ImageView difficultyIcon = sheetView.findViewById(R.id.difficultyIconView); // Assumendo tu abbia un ID
            if(difficultyIcon != null) {
                difficultyIcon.setColorFilter(ContextCompat.getColor(context, R.color.white));
            }

        }

        viewDetailsButton.setText(R.string.home_view_details_button);
        viewDetailsButton.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            // === SCOMMENTA E VERIFICA QUESTE RIGHE ===
            Intent intent = new Intent(requireActivity(), RouteDetailActivity.class);
            intent.putExtra(RouteDetailActivity.EXTRA_ROUTE_ID, route.getId()); // Usa la costante definita in RouteDetailActivity
            startActivity(intent);
            // Toast.makeText(context, "View Details for " + route.getNome(), Toast.LENGTH_SHORT).show(); // Rimuovi il Toast
            // === FINE MODIFICA ===
        });

        bottomSheetDialog.show();
    }


    // Mostra il selettore dello stile mappa
    private void showStyleSelector() {
        final String[] styleNames = {
                getString(R.string.home_style_outdoors),
                getString(R.string.home_style_streets),
                getString(R.string.home_style_light),
                getString(R.string.home_style_dark),
                getString(R.string.home_style_satellite)
        };
        // Usa gli identificatori che abbiamo definito in getStyleRawId
        final String[] styleIdentifiers = {
                "mapbox://styles/mapbox/outdoors-v11", // Manteniamo questi ID per coerenza con SettingsService
                "mapbox://styles/mapbox/streets-v11",
                "mapbox://styles/mapbox/light-v10",
                "mapbox://styles/mapbox/dark-v10",
                "mapbox://styles/mapbox/satellite-v9"
        };

        String currentStyleIdentifier = settingsService.getMapStyleUri();
        int currentSelection = 0; // Default a standard/streets
        for (int i = 0; i < styleIdentifiers.length; i++) {
            if (styleIdentifiers[i].equals(currentStyleIdentifier)) {
                currentSelection = i;
                break;
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.home_select_style_title)
                .setSingleChoiceItems(styleNames, currentSelection, (dialog, which) -> {
                    settingsService.setMapStyleUri(styleIdentifiers[which]); // Salva l'identificatore scelto
                    // L'observer in observeSettings applicherà lo stile alla mappa
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }


    // Mostra il selettore per filtrare per difficoltà
    private void showDifficultyFilterSheet() {
        final String[] difficulties = {
                getString(R.string.home_filter_all), // Indice 0
                getString(R.string.save_route_dialog_difficulty_easy), // Indice 1
                getString(R.string.save_route_dialog_difficulty_medium), // Indice 2
                getString(R.string.save_route_dialog_difficulty_hard) // Indice 3
        };
        // Valori da usare nel filtro (null per "All")
        final String[] filterValues = {null, "Easy", "Medium", "Hard"};

        int currentSelection = 0; // Default: All
        if (selectedDifficultyFilter != null) {
            for (int i = 1; i < filterValues.length; i++) {
                if (filterValues[i].equalsIgnoreCase(selectedDifficultyFilter)) {
                    currentSelection = i;
                    break;
                }
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.home_filter_title)
                .setSingleChoiceItems(difficulties, currentSelection, (dialog, which) -> {
                    selectedDifficultyFilter = filterValues[which]; // Imposta il filtro selezionato
                    Log.d(TAG, "Difficulty filter set to: " + selectedDifficultyFilter);
                    redrawMarkersOnMap(); // Ridisegna i marker applicando il filtro
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // Controlla se il permesso di localizzazione è stato concesso
    private boolean hasLocationPermission() {
        // Controlla sia FINE che COARSE location
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // Richiede il permesso di localizzazione all'utente
    private void requestLocationPermission() {
        Log.d(TAG, "Requesting location permission.");
        requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    // Centra la mappa sulla posizione attuale dell'utente
    @SuppressLint("MissingPermission")
    private void centerMapOnUserLocation(boolean initialLoad) {
        if (!hasLocationPermission()) {
            // Richiedi permesso solo se l'utente clicca il pulsante, non all'avvio iniziale
            if (!initialLoad) {
                requestLocationPermission();
            } else {
                Log.d(TAG, "Location permission not granted on initial load, using default map view.");
                // Imposta una vista di default (es. centro Europa)
                if (googleMap != null) {
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(47.0, 8.0), 4)); // Centro Europa
                }
            }
            return;
        }

        // Ottieni l'ultima posizione conosciuta
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(requireActivity(), location -> {
                    if (!isAdded()) return; // Controlla se il fragment è ancora attached
                    if (location != null && googleMap != null) {
                        Log.d(TAG, "Centering map on user location: " + location.getLatitude() + ", " + location.getLongitude());
                        googleMap.animateCamera( // Anima la camera per un effetto più fluido
                                CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(location.getLatitude(), location.getLongitude()),
                                        initialLoad ? 9.0f : 14.0f // Zoom minore all'avvio, maggiore al click
                                ),
                                1500, // Durata animazione in ms
                                null // Callback opzionale
                        );
                    } else {
                        Log.w(TAG, "Could not get last known location or map not ready.");
                        // Se è l'avvio iniziale e non c'è l'ultima posizione, usa default
                        if (initialLoad && googleMap != null) {
                            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(47.0, 8.0), 4));
                        } else if (!initialLoad) { // Mostra toast solo se l'utente ha cliccato
                            Toast.makeText(getContext(), "Could not get current location", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .addOnFailureListener(requireActivity(), e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Error getting location", e);
                    // Se è l'avvio iniziale e c'è errore, usa default
                    if (initialLoad && googleMap != null) {
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(47.0, 8.0), 4));
                    } else if (!initialLoad) { // Mostra toast solo se l'utente ha cliccato
                        Toast.makeText(getContext(), "Error getting location", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // Pulisce il binding quando la view del fragment viene distrutta
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // NECESSARIO per evitare memory leak con ViewBinding nei Fragment
        googleMap = null; // Rilascia riferimento alla mappa
        Log.d(TAG, "onDestroyView called, binding and googleMap set to null.");
    }

    // Non servono onStart, onStop, onLowMemory, onDestroy specifici per SupportMapFragment
    // perché il suo ciclo di vita è legato a quello del Fragment che lo ospita.
}