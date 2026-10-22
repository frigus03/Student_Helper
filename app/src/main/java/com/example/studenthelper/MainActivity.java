package com.example.studenthelper;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupGreeting();
        setupDashboard();
        setupClickListeners();
        setupBottomNavigation();
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
            greeting = "Good morning!";
        } else if (hour < 17) {
            greeting = "Good afternoon!";
        } else {
            greeting = "Good evening!";
        }

        tvGreeting.setText(greeting + " 👋");
    }

    private void setupDashboard() {
        refreshDashboardStats();
    }

    private void refreshDashboardStats() {
        DatabaseHelper helper = new DatabaseHelper(this);

        try {
            int completed = helper.getCompletedTaskCount();
            int pending = helper.getPendingTaskCount();

            tvTasksCount.setText(String.valueOf(completed));

            if (pending > 0) {
                tvPlanTitle.setText(
                        pending + (pending == 1
                                ? " task remaining"
                                : " tasks remaining")
                );

                tvPlanSubtitle.setText(
                        "Keep going! Complete your next study task."
                );
            } else {
                tvPlanTitle.setText("You're all caught up!");

                tvPlanSubtitle.setText(
                        "Add a new study task whenever you're ready."
                );
            }

        } finally {
            helper.close();
        }

        // Study-time tracking will be implemented in a later step.
        tvStudyHours.setText("0 min");
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (tvTasksCount != null) {
            refreshDashboardStats();
        }
    }

    private void setupClickListeners() {

        MaterialButton btnOpenAssistant =
                findViewById(R.id.btnOpenAssistant);

        MaterialButton btnOpenPlanner =
                findViewById(R.id.btnOpenPlanner);

        MaterialCardView cardNotes =
                findViewById(R.id.cardNotes);

        MaterialCardView cardPlanner =
                findViewById(R.id.cardPlanner);

        MaterialCardView cardQuiz =
                findViewById(R.id.cardQuiz);

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
                showMessage("Profile screen will be added later.");
                return true;
            }

            return false;
        });
    }

    private void openNotes() {
        android.content.Intent intent =
                new android.content.Intent(
                        MainActivity.this,
                        NotesActivity.class
                );

        startActivity(intent);
    }

    private void openPlanner() {
        android.content.Intent intent =
                new android.content.Intent(
                        MainActivity.this,
                        PlannerActivity.class
                );

        startActivity(intent);
    }

    private void openQuiz() {
        android.content.Intent intent =
                new android.content.Intent(
                        MainActivity.this,
                        QuizActivity.class
                );

        startActivity(intent);
    }

    private void showMessage(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }
    private void openAssistant() {
        android.content.Intent intent =
                new android.content.Intent(
                        MainActivity.this,
                        AssistantActivity.class
                );

        startActivity(intent);
    }
}