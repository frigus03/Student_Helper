package com.example.studenthelper;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvProfileName;
    private TextView tvProfileEmail;
    private MaterialButton btnLogout;
    private MaterialButton btnBack;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        btnLogout = findViewById(R.id.btnLogout);
        btnBack = findViewById(R.id.btnBack);

        loadUserProfile();

        btnLogout.setOnClickListener(v -> logout());
        btnBack.setOnClickListener(v -> finish());
    }

    private void loadUserProfile() {
        if (mAuth.getCurrentUser() == null) {
            logout();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String name = documentSnapshot.getString("fullName");
                        String email = null;
                        if (documentSnapshot.contains("encryptedEmail")) {
                            email = CryptoUtils.decryptEmail(documentSnapshot.getString("encryptedEmail"));
                        } else if (documentSnapshot.contains("email")) {
                            email = documentSnapshot.getString("email");
                        }
                        if ((email == null || email.isEmpty()) && mAuth.getCurrentUser() != null) {
                            email = mAuth.getCurrentUser().getEmail();
                        }
                        tvProfileName.setText(name != null ? name : "Student");
                        tvProfileEmail.setText(email != null ? email : "");
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
                });
    }

    private void logout() {
        mAuth.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
