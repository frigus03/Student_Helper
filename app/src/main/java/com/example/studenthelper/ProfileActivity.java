package com.example.studenthelper;

import android.content.Intent;
import android.os.Bundle;
import android.content.res.Configuration;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvProfileName;
    private TextView tvProfileEmail;
    private TextView tvProfileAvatar;
    private MaterialButton btnLogout;
    private MaterialButton btnBack;
    private MaterialButton btnChangePassword;
    private MaterialButton btnDeleteAccount;
    private MaterialButtonToggleGroup themeToggleGroup;

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
        tvProfileAvatar = findViewById(R.id.tvProfileAvatar);
        btnLogout = findViewById(R.id.btnLogout);
        btnBack = findViewById(R.id.btnBack);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);
        themeToggleGroup = findViewById(R.id.themeToggleGroup);

        loadUserProfile();
        btnLogout.setOnClickListener(v -> logout());
        btnBack.setOnClickListener(v -> finish());
        btnChangePassword.setOnClickListener(v -> sendPasswordReset());
        btnDeleteAccount.setOnClickListener(v -> confirmAccountDeletion());
        setupAppearanceSelector();
    }

    private void setupAppearanceSelector() {
        int savedMode = getSharedPreferences("app_settings", MODE_PRIVATE)
                .getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        boolean useDark = savedMode == AppCompatDelegate.MODE_NIGHT_YES
                || (savedMode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES);
        int selectedId = useDark ? R.id.btnDarkMode : R.id.btnLightMode;
        themeToggleGroup.check(selectedId);
        themeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            int mode = checkedId == R.id.btnDarkMode
                    ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
            getSharedPreferences("app_settings", MODE_PRIVATE).edit().putInt("theme_mode", mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
        });
    }

    private void loadUserProfile() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            logout();
            return;
        }

        String emailFallback = currentUser.getEmail();
        tvProfileEmail.setText(emailFallback == null ? "" : emailFallback);
        String userId = currentUser.getUid();

        db.collection("users").document(userId).get()
                .addOnSuccessListener(snapshot -> {
                    if (isFinishing() || isDestroyed()) return;
                    String name = snapshot.getString("fullName");
                    String email = null;
                    if (snapshot.contains("encryptedEmail")) {
                        email = CryptoUtils.decryptEmail(snapshot.getString("encryptedEmail"));
                    } else if (snapshot.contains("email")) {
                        email = snapshot.getString("email");
                    }
                    if (email == null || email.isEmpty()) email = emailFallback;
                    String displayName = name == null || name.trim().isEmpty() ? "Student" : name.trim();
                    tvProfileName.setText(displayName);
                    tvProfileEmail.setText(email == null ? "" : email);
                    tvProfileAvatar.setText(initials(displayName));
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Couldn't load account details", Toast.LENGTH_SHORT).show());
    }

    private String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "S";
        return parts.length > 1
                ? ("" + Character.toUpperCase(parts[0].charAt(0)) + Character.toUpperCase(parts[1].charAt(0)))
                : String.valueOf(Character.toUpperCase(parts[0].charAt(0)));
    }

    private void sendPasswordReset() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "Sign in again to change your password", Toast.LENGTH_LONG).show();
            return;
        }
        btnChangePassword.setEnabled(false);
        mAuth.sendPasswordResetEmail(user.getEmail())
                .addOnCompleteListener(task -> {
                    btnChangePassword.setEnabled(true);
                    if (task.isSuccessful()) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Check your email")
                                .setMessage("We sent a secure password reset link to " + user.getEmail() + ". Follow the link to choose a new password.")
                                .setPositiveButton("Done", null)
                                .show();
                    } else {
                        Toast.makeText(this, "Couldn't send reset email. Please try again.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void confirmAccountDeletion() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete account?")
                .setMessage("This permanently deletes your StudySphere profile, notes, planner tasks and saved chats. You will need to confirm your password to continue.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> promptForPassword())
                .show();
    }

    private void promptForPassword() {
        EditText passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setHint("Current password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int horizontal = (int) (24 * getResources().getDisplayMetrics().density);
        passwordInput.setPadding(horizontal, horizontal / 2, horizontal, horizontal / 2);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Confirm your identity")
                .setMessage("Enter your current password to permanently delete this account.")
                .setView(passwordInput)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete account", (dialog, which) -> {
                    String password = passwordInput.getText().toString();
                    if (password.isEmpty()) {
                        Toast.makeText(this, "Enter your password to continue", Toast.LENGTH_SHORT).show();
                    } else {
                        reauthenticateAndDelete(password);
                    }
                })
                .show();
    }

    private void reauthenticateAndDelete(String password) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            logout();
            return;
        }
        setAccountActionsEnabled(false);
        user.reauthenticate(EmailAuthProvider.getCredential(user.getEmail(), password))
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) throw task.getException();
                    String uid = user.getUid();
                    DocumentReference userDoc = db.collection("users").document(uid);
                    return deleteUserData(userDoc)
                            .continueWithTask(cleanup -> {
                                if (!cleanup.isSuccessful()) throw cleanup.getException();
                                return user.delete();
                            });
                })
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Account deleted", Toast.LENGTH_LONG).show();
                        logout();
                    } else {
                        setAccountActionsEnabled(true);
                        Toast.makeText(this, "Account wasn't deleted. Check your password and connection, then try again.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private Task<Void> deleteUserData(DocumentReference userDoc) {
        List<Task<Void>> jobs = new ArrayList<>();
        jobs.add(deleteCollection(userDoc.collection("notes")));
        jobs.add(deleteCollection(userDoc.collection("study_tasks")));
        jobs.add(deleteCollection(userDoc.collection("subject_mastery")));
        jobs.add(deleteSessions(userDoc.collection("chat_sessions")));
        jobs.add(deleteLegacyChats(userDoc.collection("chats")));
        return Tasks.whenAll(jobs).continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            return userDoc.delete();
        });
    }

    private Task<Void> deleteCollection(CollectionReference collection) {
        return collection.get().continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            List<Task<Void>> commits = new ArrayList<>();
            WriteBatch batch = db.batch();
            int pending = 0;
            for (DocumentSnapshot document : task.getResult()) {
                batch.delete(document.getReference());
                pending++;
                if (pending == 450) {
                    commits.add(batch.commit());
                    batch = db.batch();
                    pending = 0;
                }
            }
            if (pending > 0) commits.add(batch.commit());
            return Tasks.whenAll(commits);
        });
    }

    private Task<Void> deleteSessions(CollectionReference sessions) {
        return sessions.get().continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            List<Task<Void>> jobs = new ArrayList<>();
            for (DocumentSnapshot session : task.getResult()) {
                DocumentReference sessionRef = session.getReference();
                jobs.add(deleteCollection(sessionRef.collection("messages")).continueWithTask(messages -> {
                    if (!messages.isSuccessful()) throw messages.getException();
                    return sessionRef.delete();
                }));
            }
            return Tasks.whenAll(jobs);
        });
    }

    private Task<Void> deleteLegacyChats(CollectionReference chats) {
        return chats.get().continueWithTask(task -> {
            if (!task.isSuccessful()) throw task.getException();
            List<Task<Void>> jobs = new ArrayList<>();
            for (DocumentSnapshot chat : task.getResult()) {
                DocumentReference chatRef = chat.getReference();
                jobs.add(deleteCollection(chatRef.collection("messages")).continueWithTask(messages -> {
                    if (!messages.isSuccessful()) throw messages.getException();
                    return chatRef.delete();
                }));
            }
            return Tasks.whenAll(jobs);
        });
    }

    private void setAccountActionsEnabled(boolean enabled) {
        btnChangePassword.setEnabled(enabled);
        btnLogout.setEnabled(enabled);
        btnDeleteAccount.setEnabled(enabled);
        btnBack.setEnabled(enabled);
    }

    private void logout() {
        mAuth.signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
