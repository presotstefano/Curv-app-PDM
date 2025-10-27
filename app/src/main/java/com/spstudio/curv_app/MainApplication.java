package com.spstudio.curv_app;

import android.app.Application;

import com.spstudio.curv_app.services.SettingsService;

public class MainApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Inizializza il SettingsService quando l'app parte
        SettingsService.getInstance(this).loadUserSettings(); // Carica subito le impostazioni utente
    }
}