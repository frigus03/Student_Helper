package com.example.studenthelper;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlannerActivity extends AppCompatActivity {

    private static final String PLANNER_API_URL = "http://10.0.2.2:3000/api/planner/generate";

    private PlannerAdapter plannerAdapter;

    private RecyclerView rvTasks;
    private View layoutEmptyTasks;

    private TextView tvPlannerCompleted;
    private TextView tvPlannerPending;
    private TextView tvPlannerPercent;
    private TextView tvTaskListHeading;
    private TextView tvEmptyTasksMessage;

    private EditText etSearchTasks;
    private MaterialButtonToggleGroup taskFilterGroup;
    private LinearProgressIndicator progressPlanner;

    private final ArrayList<StudyTask> allTasks = new ArrayList<>();
    private String currentFilter = "all";

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration tasksListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_planner);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        initializeViews();
        setupRecyclerView();
        setupSearch();
        setupFilters();
        setupAddButton();

        loadTasks();
    }

    private void initializeViews() {
        rvTasks = findViewById(R.id.rvTasks);
        layoutEmptyTasks = findViewById(R.id.layoutEmptyTasks);

        tvPlannerCompleted = findViewById(R.id.tvPlannerCompleted);
        tvPlannerPending = findViewById(R.id.tvPlannerPending);
        tvPlannerPercent = findViewById(R.id.tvPlannerPercent);
        tvTaskListHeading = findViewById(R.id.tvTaskListHeading);
        tvEmptyTasksMessage = findViewById(R.id.tvEmptyTasksMessage);

        etSearchTasks = findViewById(R.id.etSearchTasks);
        taskFilterGroup = findViewById(R.id.taskFilterGroup);
        progressPlanner = findViewById(R.id.progressPlanner);
    }

    private void setupRecyclerView() {
        rvTasks.setLayoutManager(new LinearLayoutManager(this));

        plannerAdapter = new PlannerAdapter(new PlannerAdapter.TaskListener() {
            @Override
            public void onCompletionChanged(StudyTask task, boolean completed) {
                if (mAuth.getCurrentUser() == null) return;
                String userId = mAuth.getCurrentUser().getUid();

                db.collection("users").document(userId).collection("study_tasks").document(task.getId())
                        .update("completed", completed)
                        .addOnFailureListener(e -> {
                            Toast.makeText(PlannerActivity.this, "Could not update task", Toast.LENGTH_SHORT).show();
                        });
            }

            @Override
            public void onDeleteTask(StudyTask task) {
                confirmDelete(task);
            }
        });

        rvTasks.setAdapter(plannerAdapter);
    }

    private void setupSearch() {
        etSearchTasks.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilters() {
        taskFilterGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;

            if (checkedId == R.id.filterAll) {
                currentFilter = "all";
            } else if (checkedId == R.id.filterPending) {
                currentFilter = "pending";
            } else if (checkedId == R.id.filterCompleted) {
                currentFilter = "completed";
            }

            applyFilters();
        });
    }

    private void setupAddButton() {
        MaterialButton btnAddTask = findViewById(R.id.btnAddTask);
        if (btnAddTask != null) {
            btnAddTask.setOnClickListener(v -> showAddTaskDialog());
        }

        MaterialButton btnAiPlan = findViewById(R.id.btnAiPlan);
        if (btnAiPlan != null) {
            btnAiPlan.setOnClickListener(v -> showAiPlanDialog());
        }
    }

    private void loadTasks() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        tasksListener = db.collection("users").document(userId).collection("study_tasks")
                .orderBy("completed", Query.Direction.ASCENDING)
                .orderBy("dueDate", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e("PlannerActivity", "Listen failed.", error);
                        return;
                    }

                    if (value != null) {
                        allTasks.clear();
                        for (com.google.firebase.firestore.DocumentSnapshot doc : value.getDocuments()) {
                            StudyTask task = doc.toObject(StudyTask.class);
                            if (task != null) {
                                task.setId(doc.getId());
                                allTasks.add(task);
                            }
                        }

                        updateProgressCard();
                        applyFilters();
                    }
                });
    }

    private void updateProgressCard() {
        int total = allTasks.size();
        int completed = 0;

        for (StudyTask task : allTasks) {
            if (task.isCompleted()) completed++;
        }

        int pending = total - completed;
        int percentage = total == 0 ? 0 : Math.round((completed * 100f) / total);

        tvPlannerCompleted.setText(String.valueOf(completed));
        tvPlannerPending.setText(String.valueOf(pending));
        tvPlannerPercent.setText(percentage + "%");

        progressPlanner.setProgress(percentage);
    }

    private void applyFilters() {
        ArrayList<StudyTask> filteredTasks = new ArrayList<>();
        String query = etSearchTasks.getText().toString().trim().toLowerCase(Locale.ROOT);

        for (StudyTask task : allTasks) {
            boolean matchesSearch =
                    task.getTitle().toLowerCase(Locale.ROOT).contains(query)
                            || task.getSubject().toLowerCase(Locale.ROOT).contains(query);

            boolean matchesFilter =
                    currentFilter.equals("all")
                            || (currentFilter.equals("pending") && !task.isCompleted())
                            || (currentFilter.equals("completed") && task.isCompleted());

            if (matchesSearch && matchesFilter) {
                filteredTasks.add(task);
            }
        }

        plannerAdapter.setTasks(filteredTasks);
        tvTaskListHeading.setText("YOUR TASKS (" + filteredTasks.size() + ")");

        boolean empty = filteredTasks.isEmpty();
        rvTasks.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutEmptyTasks.setVisibility(empty ? View.VISIBLE : View.GONE);

        if (allTasks.isEmpty()) {
            tvEmptyTasksMessage.setText("Add your first study task to get organized.");
        } else if (!query.isEmpty() || !currentFilter.equals("all")) {
            tvEmptyTasksMessage.setText("No tasks match your current search or filter.");
        } else {
            tvEmptyTasksMessage.setText("You're all caught up! Add another task whenever you're ready.");
        }
    }

    private void showAddTaskDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        int padding = dp(20);
        form.setPadding(padding, dp(8), padding, 0);

        EditText etTitle = createInput("Task title");
        EditText etSubject = createInput("Subject");

        etTitle.setSingleLine(true);
        etSubject.setSingleLine(true);

        Calendar selectedDate = Calendar.getInstance();
        selectedDate.set(Calendar.HOUR_OF_DAY, 0);
        selectedDate.set(Calendar.MINUTE, 0);
        selectedDate.set(Calendar.SECOND, 0);
        selectedDate.set(Calendar.MILLISECOND, 0);

        MaterialButton btnPickDate =
                new MaterialButton(
                        this,
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle
                );
        btnPickDate.setText(getDateLabel(selectedDate));

        btnPickDate.setOnClickListener(v -> {
            new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        selectedDate.set(Calendar.YEAR, year);
                        selectedDate.set(Calendar.MONTH, month);
                        selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        btnPickDate.setText(getDateLabel(selectedDate));
                    },
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH)
            ).show();
        });

        form.addView(etTitle, createFieldParams());
        form.addView(etSubject, createFieldParams());
        form.addView(btnPickDate, createFieldParams());

        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Create a study task")
                        .setView(form)
                        .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                        .setPositiveButton("Add task", null)
                        .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String title = etTitle.getText().toString().trim();
                String subject = etSubject.getText().toString().trim();

                if (title.isEmpty()) {
                    etTitle.setError("Enter a task title");
                    etTitle.requestFocus();
                    return;
                }

                if (subject.isEmpty()) {
                    etSubject.setError("Enter a subject");
                    etSubject.requestFocus();
                    return;
                }

                if (mAuth.getCurrentUser() == null) return;
                String userId = mAuth.getCurrentUser().getUid();

                long dueDate = selectedDate.getTimeInMillis();

                StudyTask task = new StudyTask();
                task.setTitle(title);
                task.setSubject(subject);
                task.setDueDate(dueDate);
                task.setCompleted(false);
                task.setCreatedAt(System.currentTimeMillis());

                db.collection("users").document(userId).collection("study_tasks")
                        .add(task).addOnCompleteListener(t -> {
                            if (t.isSuccessful()) {
                                dialog.dismiss();
                                Toast.makeText(this, "Study task added!", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Could not save task. Please try again.", Toast.LENGTH_LONG).show();
                            }
                        });
            });
        });

        dialog.show();
    }

    private void showAiPlanDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        int padding = dp(20);
        form.setPadding(padding, dp(8), padding, 0);

        EditText etExamTarget = createInput("Target Exam / Deadline (e.g. 2026-11-15)");
        EditText etSubjects = createInput("Subjects (e.g. CS 301, Biology, Math)");
        EditText etWeakAreas = createInput("Weak subjects to focus on (e.g. CS 301)");
        EditText etDailyHours = createInput("Available study hours / day (e.g. 2)");

        etExamTarget.setSingleLine(true);
        etSubjects.setSingleLine(true);
        etWeakAreas.setSingleLine(true);
        etDailyHours.setSingleLine(true);
        etDailyHours.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        form.addView(etExamTarget, createFieldParams());
        form.addView(etSubjects, createFieldParams());
        form.addView(etWeakAreas, createFieldParams());
        form.addView(etDailyHours, createFieldParams());

        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle("✨ AI Study Plan Generator")
                        .setMessage("Gemini will design an adaptive schedule prioritizing your weak subjects.")
                        .setView(form)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Generate Plan", null)
                        .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String examTarget = etExamTarget.getText().toString().trim();
                String subjects = etSubjects.getText().toString().trim();
                String weak = etWeakAreas.getText().toString().trim();
                String hoursStr = etDailyHours.getText().toString().trim();

                if (subjects.isEmpty()) {
                    etSubjects.setError("Please enter at least one subject");
                    etSubjects.requestFocus();
                    return;
                }

                dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                Toast.makeText(this, "AI is creating your adaptive study plan...", Toast.LENGTH_SHORT).show();

                executor.execute(() -> {
                    try {
                        JSONObject body = new JSONObject();
                        body.put("targetExamDate", examTarget.isEmpty() ? "in 14 days" : examTarget);
                        body.put("subjects", subjects);
                        body.put("weakSubjects", weak.isEmpty() ? subjects : weak);
                        body.put("availableHoursPerDay", hoursStr.isEmpty() ? 2 : Integer.parseInt(hoursStr));

                        URL url = new URL(PLANNER_API_URL);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(45000);
                        conn.setDoOutput(true);

                        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(bytes);
                        }

                        int code = conn.getResponseCode();
                        InputStream is = code == 200 ? conn.getInputStream() : conn.getErrorStream();
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        byte[] data = new byte[1024];
                        int nRead;
                        while ((nRead = is.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, nRead);
                        }
                        String responseStr = buffer.toString(StandardCharsets.UTF_8.name());

                        if (code == 200 && mAuth.getCurrentUser() != null) {
                            JSONObject res = new JSONObject(responseStr);
                            JSONArray tasksArr = res.optJSONArray("tasks");
                            String userId = mAuth.getCurrentUser().getUid();

                            if (tasksArr != null && tasksArr.length() > 0) {
                                for (int i = 0; i < tasksArr.length(); i++) {
                                    JSONObject tObj = tasksArr.getJSONObject(i);
                                    String tTitle = tObj.optString("title", "Study Session");
                                    String tSub = tObj.optString("subject", subjects);
                                    String dateStr = tObj.optString("date", "");

                                    long dueTimeMillis = parseDateToMillis(dateStr, i + 1);

                                    StudyTask newTask = new StudyTask();
                                    newTask.setTitle(tTitle);
                                    newTask.setSubject(tSub);
                                    newTask.setDueDate(dueTimeMillis);
                                    newTask.setCompleted(false);
                                    newTask.setCreatedAt(System.currentTimeMillis());

                                    db.collection("users").document(userId)
                                            .collection("study_tasks").add(newTask);
                                }

                                runOnUiThread(() -> {
                                    dialog.dismiss();
                                    Toast.makeText(PlannerActivity.this, "AI Study Plan successfully created!", Toast.LENGTH_SHORT).show();
                                });
                                return;
                            }
                        }
                    } catch (Exception e) {
                        Log.e("PlannerActivity", "Failed to generate AI plan", e);
                    }

                    runOnUiThread(() -> {
                        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        Toast.makeText(PlannerActivity.this, "Could not contact StudySphere AI backend. Make sure the backend server is running on port 3000.", Toast.LENGTH_LONG).show();
                    });
                });
            });
        });

        dialog.show();
    }

    private long parseDateToMillis(String dateStr, int dayOffset) {
        if (dateStr != null && !dateStr.isEmpty()) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date parsed = sdf.parse(dateStr);
                if (parsed != null) return parsed.getTime();
            } catch (Exception ignored) {
            }
        }
        return System.currentTimeMillis() + ((long) dayOffset * 24 * 60 * 60 * 1000);
    }

    private void confirmDelete(StudyTask task) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete task?")
                .setMessage("Are you sure you want to delete \"" + task.getTitle() + "\"?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (mAuth.getCurrentUser() == null) return;
                    String userId = mAuth.getCurrentUser().getUid();

                    db.collection("users").document(userId).collection("study_tasks").document(task.getId())
                            .delete().addOnCompleteListener(t -> {
                                if (t.isSuccessful()) {
                                    Toast.makeText(this, "Task deleted", Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(this, "Could not delete task", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .show();
    }

    private EditText createInput(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setTextSize(15);
        editText.setPadding(dp(12), dp(12), dp(12), dp(12));
        return editText;
    }

    private LinearLayout.LayoutParams createFieldParams() {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        params.bottomMargin = dp(12);
        return params;
    }

    private String getDateLabel(Calendar calendar) {
        SimpleDateFormat formatter =
                new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault());
        return "Due date: " + formatter.format(calendar.getTime());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tasksListener != null) {
            tasksListener.remove();
        }
    }
}
