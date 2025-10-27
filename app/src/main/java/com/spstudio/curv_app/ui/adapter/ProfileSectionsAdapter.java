package com.spstudio.curv_app.ui.adapter;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.spstudio.curv_app.ui.fragment.MyRoutesFragment;
import com.spstudio.curv_app.ui.fragment.SavedRoutesFragment;

public class ProfileSectionsAdapter extends FragmentStateAdapter {

    private final String userId;
    private final boolean isMyProfile; // Per decidere se mostrare il tab "Salvati"

    public ProfileSectionsAdapter(@NonNull Fragment fragment, String userId, boolean isMyProfile) {
        super(fragment);
        this.userId = userId;
        this.isMyProfile = isMyProfile;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Bundle args = new Bundle();
        args.putString("USER_ID", userId);

        if (isMyProfile) {
            // Se è il mio profilo: Tab 0 = Saved, Tab 1 = Created
            if (position == 0) {
                SavedRoutesFragment fragment = new SavedRoutesFragment();
                fragment.setArguments(args);
                return fragment;
            } else {
                MyRoutesFragment fragment = new MyRoutesFragment();
                fragment.setArguments(args);
                return fragment;
            }
        } else {
            // Se è il profilo di un altro utente: solo Tab 0 = Created
            MyRoutesFragment fragment = new MyRoutesFragment();
            fragment.setArguments(args);
            return fragment;
        }
    }

    @Override
    public int getItemCount() {
        // 2 tab (Saved, Created) per il mio profilo
        // 1 tab (Created) per gli altri
        return isMyProfile ? 2 : 1;
    }
}