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
    private TextView tvTasksLabel;
    private TextView tvStudyHours;
    private TextView tvStudyHoursLabel;
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
        tvTasksLabel = findViewById(R.id.tvTasksLabel);
        tvStudyHours = findViewById(R.id.tvStudyHours);
        tvStudyHoursLabel = findViewById(R.id.tvStudyHoursLabel);
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
            greeting = getString(R.string.greeting_morning);
        } else if (hour < 17) {
            greeting = getString(R.string.greeting_afternoon);
        } else {
            greeting = getString(R.string.greeting_evening);
        }

        String displayName = getString(R.string.student_default);
        if (mAuth.getCurrentUser() != null && mAuth.getCurrentUser().getDisplayName() != null && !mAuth.getCurrentUser().getDisplayName().isEmpty()) {
            displayName = mAuth.getCurrentUser().getDisplayName();
        }

        tvGreeting.setText(greeting + ", " + displayName);
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
                        int pendingMinutes = 0;
                        int completedMinutes = 0;

                        for (com.google.firebase.firestore.DocumentSnapshot doc : value.getDocuments()) {
                            Boolean isCompleted = doc.getBoolean("completed");
                            Long dur = doc.getLong("durationMinutes");
                            int minutes = dur != null ? dur.intValue() : 45;

                            if (isCompleted != null && isCompleted) {
                                completed++;
                                completedMinutes += minutes;
                            } else {
                                pending++;
                                pendingMinutes += minutes;
                            }
                        }
                        updateDashboardStats(completed, pending, pendingMinutes, completedMinutes);
                    }
                });
    }

    private void updateDashboardStats(int completed, int pending, int pendingMinutes, int completedMinutes) {
        if (pending > 0) {
            tvTasksCount.setText(String.valueOf(pending));
            if (tvTasksLabel != null) {
                tvTasksLabel.setText(getString(R.string.overview_tasks_to_do));
            }

            if (pendingMinutes >= 60) {
                int hours = pendingMinutes / 60;
                int remMinutes = pendingMinutes % 60;
                if (remMinutes == 0) {
                    tvStudyHours.setText(hours + " hrs");
                } else {
                    tvStudyHours.setText(String.format(Locale.getDefault(), "%.1f hrs", pendingMinutes / 60.0));
                }
            } else {
                tvStudyHours.setText(pendingMinutes + " min");
            }
            if (tvStudyHoursLabel != null) {
                tvStudyHoursLabel.setText(getString(R.string.overview_study_time));
            }

            tvPlanTitle.setText(pending == 1 ? getString(R.string.plan_single_task_remaining) : getString(R.string.plan_multiple_tasks_remaining, pending));
            tvPlanSubtitle.setText(getString(R.string.plan_keep_going));
        } else if (completed > 0) {
            tvTasksCount.setText(String.valueOf(completed));
            if (tvTasksLabel != null) {
                tvTasksLabel.setText(getString(R.string.overview_tasks_completed));
            }

            if (completedMinutes >= 60) {
                int hours = completedMinutes / 60;
                int remMinutes = completedMinutes % 60;
                if (remMinutes == 0) {
                    tvStudyHours.setText(hours + " hrs");
                } else {
                    tvStudyHours.setText(String.format(Locale.getDefault(), "%.1f hrs", completedMinutes / 60.0));
                }
            } else {
                tvStudyHours.setText(completedMinutes + " min");
            }
            if (tvStudyHoursLabel != null) {
                tvStudyHoursLabel.setText(getString(R.string.overview_study_time_today));
            }

            tvPlanTitle.setText(getString(R.string.plan_all_caught_up));
            tvPlanSubtitle.setText(getString(R.string.plan_add_new));
        } else {
            tvTasksCount.setText("0");
            if (tvTasksLabel != null) {
                tvTasksLabel.setText(getString(R.string.overview_tasks_to_do));
            }
            tvStudyHours.setText("0 min");
            if (tvStudyHoursLabel != null) {
                tvStudyHoursLabel.setText(getString(R.string.overview_study_time));
            }

            tvPlanTitle.setText(getString(R.string.plan_all_caught_up));
            tvPlanSubtitle.setText(getString(R.string.plan_add_new));
        }
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
        MaterialButton btnOpenPlanner = findViewById(R.id.btnOpenPlanner);
        MaterialCardView cardNotes = findViewById(R.id.cardNotes);
        MaterialCardView cardPlanner = findViewById(R.id.cardPlanner);
        MaterialCardView cardQuiz = findViewById(R.id.cardQuiz);

        btnOpenAssistant.setOnClickListener(v -> openAssistant());
        btnOpenPlanner.setOnClickListener(v -> openPlanner());
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
