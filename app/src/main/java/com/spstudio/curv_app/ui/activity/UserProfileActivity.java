package com.spstudio.curv_app.ui.activity;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.spstudio.curv_app.R;

public class UserProfileActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile); // Crea questo layout

        TextView textView = findViewById(R.id.profile_placeholder_text);
        String userId = getIntent().getStringExtra("USER_ID");
        textView.setText("User Profile Placeholder\nUser ID: " + (userId != null ? userId : "N/A"));
    }
}