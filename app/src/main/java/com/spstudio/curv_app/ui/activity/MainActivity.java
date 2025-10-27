package com.spstudio.curv_app.ui.activity;

import android.content.Intent; // Import Intent
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.spstudio.curv_app.R;
import com.spstudio.curv_app.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupNavigation();

        binding.fab.setOnClickListener(view -> {
            // Avvia CreateRouteActivity
            Intent intent = new Intent(this, CreateRouteActivity.class);
            startActivity(intent);
            // Toast.makeText(this, R.string.create_route_fab_description, Toast.LENGTH_SHORT).show(); // Rimuovi placeholder
        });

        // Impedisce alla voce placeholder del menu di essere selezionabile
        binding.bottomNavigationView.getMenu().findItem(R.id.placeholder).setEnabled(false);
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            // Collega BottomNavigationView al NavController
            NavigationUI.setupWithNavController(binding.bottomNavigationView, navController);
        }
    }
}