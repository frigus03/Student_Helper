package com.example.studenthelper;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvGreeting;
    private TextView tvTasksCount;
    private TextView tvStudyHours;
    private TextView tvPlanTitle;
    private TextView tvPlanSubtitle;

    private BottomNavigationView bottomNavigation;
    
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration tasksListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupGreeting();
        setupClickListeners();
        setupBottomNavigation();
        setupDashboard();
    }

    private void initializeViews() {
        tvGreeting = findViewById(R.id.tvGreeting);
        tvTasksCount = findViewById(R.id.tvTasksCount);
        tvStudyHours = findViewById(R.id.tvStudyHours);
        tvPlanTitle = findViewById(R.id.tvPlanTitle);
        tvPlanSubtitle = findViewById(R.id.tvPlanSubtitle);

        bottomNavigation = findViewById(R.id.bottomNavigation);
    }

    private void setupGreeting() {
        int hour = Integer.parseInt(
                new SimpleDateFormat("H", Locale.getDefault())
                        .format(new Date())
        );

        String greeting;
        if (hour < 12) {
            greeting = "Good morning";
        } else if (hour < 17) {
            greeting = "Good afternoon";
        } else {
            greeting = "Good evening";
        }

        String displayName = "Student";
        if (mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getDisplayName() != null && !mAuth.getCurrentUser().getDisplayName().isEmpty()) {
            displayName = mAuth.getCurrentUser().getDisplayName();
        }

        tvGreeting.setText(greeting + ", " + displayName + " 👋");
    }

    private void setupDashboard() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        tasksListener = db.collection("users").document(userId).collection("study_tasks")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        return;
                    }
                    if (value != null) {
                        int completed = 0;
                        int pending = 0;
                        for (com.google.firebase.firestore.DocumentSnapshot doc : value.getDocuments()) {
                            Boolean isCompleted = doc.getBoolean("completed");
                            if (isCompleted != null && isCompleted) {
                                completed++;
                            } else {
                                pending++;
                            }
                        }
                        updateDashboardStats(completed, pending);
                    }
                });
    }

    private void updateDashboardStats(int completed, int pending) {
        tvTasksCount.setText(String.valueOf(completed));

        if (pending > 0) {
            tvPlanTitle.setText(pending + (pending == 1 ? " task remaining" : " tasks remaining"));
            tvPlanSubtitle.setText("Keep going! Complete your next study task.");
        } else {
            tvPlanTitle.setText("You're all caught up!");
            tvPlanSubtitle.setText("Add a new study task whenever you're ready.");
        }
        tvStudyHours.setText("0 min"); // Static for now
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tasksListener != null) {
            tasksListener.remove();
        }
    }

    private void setupClickListeners() {
        MaterialButton btnOpenAssistant = findViewById(R.id.btnOpenAssistant);
        MaterialCardView cardNotes = findViewById(R.id.cardNotes);
        MaterialCardView cardPlanner = findViewById(R.id.cardPlanner);
        MaterialCardView cardQuiz = findViewById(R.id.cardQuiz);

        btnOpenAssistant.setOnClickListener(v -> openAssistant());
        cardNotes.setOnClickListener(v -> openNotes());
        cardPlanner.setOnClickListener(v -> openPlanner());
        cardQuiz.setOnClickListener(v -> openQuiz());
    }

    private void setupBottomNavigation() {
        bottomNavigation.setSelectedItemId(R.id.nav_home);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                return true;
            } else if (itemId == R.id.nav_notes) {
                openNotes();
                return true;
            } else if (itemId == R.id.nav_planner) {
                openPlanner();
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }

    private void openNotes() {
        startActivity(new Intent(this, NotesActivity.class));
    }

    private void openPlanner() {
        startActivity(new Intent(this, PlannerActivity.class));
    }

    private void openQuiz() {
        startActivity(new Intent(this, QuizActivity.class));
    }
    
    private void openAssistant() {
        startActivity(new Intent(this, AssistantActivity.class));
    }
}
