package com.spstudio.curv_app.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.spstudio.curv_app.R;
import com.spstudio.curv_app.databinding.ActivityEmailVerificationBinding;

public class EmailVerificationActivity extends AppCompatActivity {

    private static final String TAG = "EmailVerification";

    private ActivityEmailVerificationBinding binding;
    private FirebaseAuth mAuth;
    private Handler handler;
    private Runnable checkVerificationRunnable;
    private boolean isCheckingVerification = false;
    private boolean canResend = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEmailVerificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        handler = new Handler(Looper.getMainLooper());

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            navigateToLogin();
            return;
        }

        updateUIBasedOnVerificationStatus(user.isEmailVerified(), user.getEmail());

        binding.resendButton.setOnClickListener(v -> sendVerificationEmail());
        binding.cancelButton.setOnClickListener(v -> signOutAndNavigateToLogin());
        binding.continueButton.setOnClickListener(v -> navigateToProfileSetupOrMain());

        if (!user.isEmailVerified()) {
            startCheckingVerificationStatus();
        }
    }

    private void updateUIBasedOnVerificationStatus(boolean isVerified, String email) {
        if (isVerified) {
            binding.iconImageView.setImageResource(R.drawable.ic_mark_email_read_outline);
            binding.iconImageView.setColorFilter(ContextCompat.getColor(this, R.color.successColor));
            binding.titleTextView.setText(R.string.verification_title_success);
            binding.subtitleTextView.setText(R.string.verification_subtitle_success);
            binding.resendButton.setVisibility(View.GONE);
            binding.cancelButton.setVisibility(View.GONE);
            binding.continueButton.setVisibility(View.VISIBLE);
        } else {
            binding.iconImageView.setImageResource(R.drawable.ic_email_outline);
            binding.iconImageView.setColorFilter(ContextCompat.getColor(this, R.color.primaryColor));
            binding.titleTextView.setText(R.string.verification_title);
            // Formatta la stringa con l'email dell'utente
            binding.subtitleTextView.setText(getString(R.string.verification_subtitle, (email != null ? email : "your email")));
            binding.resendButton.setVisibility(View.VISIBLE);
            binding.cancelButton.setVisibility(View.VISIBLE);
            binding.continueButton.setVisibility(View.GONE);
            binding.resendButton.setEnabled(canResend);
        }
    }

    private void startCheckingVerificationStatus() {
        if (isCheckingVerification) return;

        Log.d(TAG, "Starting periodic verification check.");
        isCheckingVerification = true;
        checkVerificationRunnable = new Runnable() {
            @Override
            public void run() {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) {
                    user.reload().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser updatedUser = mAuth.getCurrentUser();
                            if (updatedUser != null && updatedUser.isEmailVerified()) {
                                Log.d(TAG, "Email verified!");
                                stopCheckingVerificationStatus();
                                updateUIBasedOnVerificationStatus(true, updatedUser.getEmail());
                            } else {
                                Log.d(TAG, "Email not verified yet. Rescheduling check.");
                                handler.postDelayed(this, 5000);
                            }
                        } else {
                            Log.e(TAG, "Error reloading user.", task.getException());
                            handler.postDelayed(this, 5000);
                        }
                    });
                } else {
                    Log.w(TAG, "User became null during check. Stopping checks.");
                    stopCheckingVerificationStatus();
                }
            }
        };
        handler.postDelayed(checkVerificationRunnable, 3000);
    }

    private void stopCheckingVerificationStatus() {
        Log.d(TAG, "Stopping verification check.");
        if (checkVerificationRunnable != null) {
            handler.removeCallbacks(checkVerificationRunnable);
        }
        isCheckingVerification = false;
    }


    private void sendVerificationEmail() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null && !user.isEmailVerified()) {
            setLoading(true);
            binding.resendButton.setEnabled(false);
            canResend = false;

            user.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        setLoading(false);
                        if (task.isSuccessful()) {
                            Log.d(TAG, "Verification email sent.");
                            Toast.makeText(EmailVerificationActivity.this, getString(R.string.verification_sent_toast), Toast.LENGTH_SHORT).show();
                            handler.postDelayed(() -> {
                                canResend = true;
                                if(mAuth.getCurrentUser() != null && !mAuth.getCurrentUser().isEmailVerified()) {
                                    binding.resendButton.setEnabled(true);
                                }
                            }, 15000);
                        } else {
                            Log.e(TAG, "sendEmailVerification", task.getException());
                            Toast.makeText(EmailVerificationActivity.this, getString(R.string.verification_failed_toast), Toast.LENGTH_SHORT).show();
                            canResend = true;
                            binding.resendButton.setEnabled(true);
                        }
                    });
        }
    }

    private void signOutAndNavigateToLogin(){
        stopCheckingVerificationStatus();
        mAuth.signOut();
        navigateToLogin();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(EmailVerificationActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateToProfileSetupOrMain() {
        Intent intent = new Intent(EmailVerificationActivity.this, ProfileSetupActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCheckingVerificationStatus();
    }
}