package com.example.studenthelper;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlannerActivity extends AppCompatActivity {

    private static final String PLANNER_API_URL = "http://10.0.2.2:3000/api/planner/generate";
    private static final String PARSE_TIMETABLE_API_URL = "http://10.0.2.2:3000/api/planner/parse-timetable";

    private ActivityResultLauncher<String> timetablePickerLauncher;
    private TextInputEditText currentDialogEtTimetable;
    private View currentDialogLayoutFileStatus;
    private TextView currentDialogTvFileName;
    private View currentDialogPbParsing;

    private PlannerAdapter plannerAdapter;
    private RecyclerView rvTasks;
    private View layoutEmptyTasks;
    private TextView tvEmptyTasksMessage;
    private TextView tvWorkloadHours;
    private LinearLayout dateStripContainer;
    private ChipGroup chipGroupFilters;

    private ArrayList<StudyTask> allTasks = new ArrayList<>();
    private String currentFilter = "All";
    private Calendar selectedCalendarDate = null;

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
        setupBottomNavigation();
        setupDateStrip();
        setupFilters();

        timetablePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        onTimetableFileSelected(uri);
                    }
                }
        );

        FloatingActionButton fabAddTask = findViewById(R.id.fabAddTask);
        if (fabAddTask != null) {
            fabAddTask.setOnClickListener(v -> showAiPlannerWizard());
        }

        MaterialButton btnAiPlan = findViewById(R.id.btnAiPlan);
        if (btnAiPlan != null) {
            btnAiPlan.setOnClickListener(v -> showAiPlannerWizard());
        }

        loadTasks();
    }

    private void initializeViews() {
        rvTasks = findViewById(R.id.rvTasks);
        layoutEmptyTasks = findViewById(R.id.layoutEmptyTasks);
        tvEmptyTasksMessage = findViewById(R.id.tvEmptyTasksMessage);
        tvWorkloadHours = findViewById(R.id.tvWorkloadHours);
        dateStripContainer = findViewById(R.id.dateStripContainer);
        chipGroupFilters = findViewById(R.id.chipGroupFilters);
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
            public void onAiBreakdown(StudyTask task) {
                new MaterialAlertDialogBuilder(PlannerActivity.this)
                        .setTitle("AI Breakdown")
                        .setMessage("Study breakdown for " + task.getTitle() + ":\n\n" + (task.getNotes() != null ? task.getNotes() : "Review primary textbook chapters, notes, and solve key problems."))
                        .setPositiveButton("Close", null)
                        .show();
            }

            @Override
            public void onCalendarSync(StudyTask task) {
                if (task != null) {
                    CalendarHelper.addStudyTaskToCalendar(PlannerActivity.this, task);
                }
            }

            @Override
            public void onDeleteTask(StudyTask task) {
                confirmDelete(task);
            }
        });
        rvTasks.setAdapter(plannerAdapter);
    }

    private void setupFilters() {
        String[] filters = {"All", "Pending", "Completed", "Overdue"};
        for (String filter : filters) {
            Chip chip = new Chip(this);
            chip.setText(filter);
            chip.setCheckable(true);
            chip.setClickable(true);
            if (filter.equals("All")) {
                chip.setChecked(true);
            }
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    currentFilter = filter;
                    applyFilters();
                }
            });
            chipGroupFilters.addView(chip);
        }
    }

    private void setupDateStrip() {
        dateStripContainer.removeAllViews();

        SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd", Locale.getDefault());
        Calendar now = Calendar.getInstance();
        int currentDayOfYear = now.get(Calendar.DAY_OF_YEAR);
        int currentYear = now.get(Calendar.YEAR);

        int padH = dp(14);
        int padV = dp(10);

        // 1. "All" days pill
        LinearLayout allDayView = new LinearLayout(this);
        allDayView.setOrientation(LinearLayout.VERTICAL);
        allDayView.setGravity(Gravity.CENTER);
        allDayView.setPadding(padH, padV, padH, padV);
        boolean isAllSelected = (selectedCalendarDate == null);
        if (isAllSelected) {
            allDayView.setBackgroundResource(R.drawable.bg_badge_primary_light);
        }

        TextView tvAllLabel = new TextView(this);
        tvAllLabel.setText("VIEW");
        tvAllLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvAllLabel.setTextColor(getColor(isAllSelected ? R.color.primary : R.color.text_secondary));
        tvAllLabel.setTypeface(null, Typeface.BOLD);

        TextView tvAllText = new TextView(this);
        tvAllText.setText("All");
        tvAllText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tvAllText.setTextColor(getColor(isAllSelected ? R.color.primary : R.color.text_primary));
        tvAllText.setTypeface(null, Typeface.BOLD);
        tvAllText.setPadding(0, dp(4), 0, 0);

        allDayView.addView(tvAllLabel);
        allDayView.addView(tvAllText);

        LinearLayout.LayoutParams allParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        allParams.setMarginEnd(dp(8));
        allDayView.setLayoutParams(allParams);
        allDayView.setOnClickListener(v -> {
            selectedCalendarDate = null;
            setupDateStrip();
            applyFilters();
            updateWorkloadBanner();
        });
        dateStripContainer.addView(allDayView);

        // 2. Add next 21 days starting from today
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 21; i++) {
            final Calendar thisDay = (Calendar) cal.clone();
            LinearLayout dayView = new LinearLayout(this);
            dayView.setOrientation(LinearLayout.VERTICAL);
            dayView.setGravity(Gravity.CENTER);
            dayView.setPadding(padH, padV, padH, padV);

            boolean isToday = (thisDay.get(Calendar.DAY_OF_YEAR) == currentDayOfYear && thisDay.get(Calendar.YEAR) == currentYear);
            boolean isSelected = (selectedCalendarDate != null 
                    && selectedCalendarDate.get(Calendar.DAY_OF_YEAR) == thisDay.get(Calendar.DAY_OF_YEAR)
                    && selectedCalendarDate.get(Calendar.YEAR) == thisDay.get(Calendar.YEAR));

            if (isSelected) {
                dayView.setBackgroundResource(R.drawable.bg_badge_primary_light);
            }

            TextView tvDayName = new TextView(this);
            tvDayName.setText(dayFormat.format(thisDay.getTime()).toUpperCase(Locale.ROOT));
            tvDayName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tvDayName.setTextColor(getColor(isSelected ? R.color.primary : (isToday ? R.color.primary : R.color.text_secondary)));
            tvDayName.setTypeface(null, Typeface.BOLD);

            TextView tvDate = new TextView(this);
            tvDate.setText(dateFormat.format(thisDay.getTime()));
            tvDate.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            tvDate.setTextColor(getColor(isSelected ? R.color.primary : (isToday ? R.color.primary : R.color.text_primary)));
            tvDate.setTypeface(null, Typeface.BOLD);
            tvDate.setPadding(0, dp(4), 0, 0);

            dayView.addView(tvDayName);
            dayView.addView(tvDate);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(dp(8));
            dayView.setLayoutParams(params);

            dayView.setOnClickListener(v -> {
                if (selectedCalendarDate != null 
                        && selectedCalendarDate.get(Calendar.DAY_OF_YEAR) == thisDay.get(Calendar.DAY_OF_YEAR)
                        && selectedCalendarDate.get(Calendar.YEAR) == thisDay.get(Calendar.YEAR)) {
                    selectedCalendarDate = null; // Toggle off to all
                } else {
                    selectedCalendarDate = thisDay;
                }
                setupDateStrip();
                applyFilters();
                updateWorkloadBanner();
            });

            dateStripContainer.addView(dayView);
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private void loadTasks() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        tasksListener = db.collection("users").document(userId).collection("study_tasks")
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
                        updateWorkloadBanner();
                        applyFilters();
                    }
                });
    }

    private void updateWorkloadBanner() {
        int totalMinutes = 0;
        SimpleDateFormat dayFmt = new SimpleDateFormat("MMM dd", Locale.getDefault());
        String targetDateName = "today";

        if (selectedCalendarDate != null) {
            targetDateName = dayFmt.format(selectedCalendarDate.getTime());
            for (StudyTask task : allTasks) {
                if (!task.isCompleted()) {
                    Calendar taskCal = Calendar.getInstance();
                    taskCal.setTimeInMillis(task.getDueDate());
                    if (taskCal.get(Calendar.YEAR) == selectedCalendarDate.get(Calendar.YEAR)
                            && taskCal.get(Calendar.DAY_OF_YEAR) == selectedCalendarDate.get(Calendar.DAY_OF_YEAR)) {
                        totalMinutes += task.getDurationMinutes();
                    }
                }
            }
        } else {
            Calendar endOfToday = Calendar.getInstance();
            endOfToday.set(Calendar.HOUR_OF_DAY, 23);
            endOfToday.set(Calendar.MINUTE, 59);
            for (StudyTask task : allTasks) {
                if (!task.isCompleted() && task.getDueDate() <= endOfToday.getTimeInMillis()) {
                    totalMinutes += task.getDurationMinutes();
                }
            }
        }
        
        float hours = totalMinutes / 60f;
        if (totalMinutes == 0) {
            tvWorkloadHours.setText("All clear for " + targetDateName + "!");
        } else {
            tvWorkloadHours.setText(String.format(Locale.US, "%.1f hrs scheduled for %s", hours, targetDateName));
        }
    }

    private void applyFilters() {
        ArrayList<PlannerListItem> displayList = new ArrayList<>();

        ArrayList<StudyTask> needsAttention = new ArrayList<>();
        ArrayList<StudyTask> dueToday = new ArrayList<>();
        ArrayList<StudyTask> upcoming = new ArrayList<>();
        ArrayList<StudyTask> completedTasks = new ArrayList<>();

        long now = System.currentTimeMillis();
        Calendar endOfToday = Calendar.getInstance();
        endOfToday.set(Calendar.HOUR_OF_DAY, 23);
        endOfToday.set(Calendar.MINUTE, 59);
        long endOfTodayMillis = endOfToday.getTimeInMillis();

        for (StudyTask task : allTasks) {
            boolean matchesFilter = false;
            boolean isOverdue = !task.isCompleted() && task.getDueDate() < now;
            
            if (currentFilter.equals("All")) matchesFilter = true;
            else if (currentFilter.equals("Pending") && !task.isCompleted()) matchesFilter = true;
            else if (currentFilter.equals("Completed") && task.isCompleted()) matchesFilter = true;
            else if (currentFilter.equals("Overdue") && isOverdue) matchesFilter = true;

            // Date strip filter: if user tapped a date on the strip, only show tasks on that date
            if (matchesFilter && selectedCalendarDate != null) {
                Calendar taskCal = Calendar.getInstance();
                taskCal.setTimeInMillis(task.getDueDate());
                boolean sameDay = (taskCal.get(Calendar.YEAR) == selectedCalendarDate.get(Calendar.YEAR)
                        && taskCal.get(Calendar.DAY_OF_YEAR) == selectedCalendarDate.get(Calendar.DAY_OF_YEAR));
                if (!sameDay) {
                    matchesFilter = false;
                }
            }

            if (matchesFilter) {
                if (task.isCompleted()) {
                    completedTasks.add(task);
                } else if (isOverdue || "HIGH".equals(task.getPriority())) {
                    needsAttention.add(task);
                } else if (task.getDueDate() <= endOfTodayMillis) {
                    dueToday.add(task);
                } else {
                    upcoming.add(task);
                }
            }
        }

        if (!needsAttention.isEmpty()) {
            displayList.add(new HeaderItem(getString(R.string.header_needs_attention), needsAttention.size()));
            for (StudyTask t : needsAttention) displayList.add(new TaskItem(t));
        }
        
        if (!dueToday.isEmpty()) {
            displayList.add(new HeaderItem(getString(R.string.header_due_today), dueToday.size()));
            for (StudyTask t : dueToday) displayList.add(new TaskItem(t));
        }
        
        if (!upcoming.isEmpty()) {
            displayList.add(new HeaderItem(getString(R.string.header_upcoming), upcoming.size()));
            for (StudyTask t : upcoming) displayList.add(new TaskItem(t));
        }
        
        if (!completedTasks.isEmpty()) {
            displayList.add(new HeaderItem(getString(R.string.header_completed), completedTasks.size()));
            for (StudyTask t : completedTasks) displayList.add(new TaskItem(t));
        }

        plannerAdapter.setItems(displayList);

        boolean empty = displayList.isEmpty();
        rvTasks.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutEmptyTasks.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void showAddTaskDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        int padding = dp(20);
        form.setPadding(padding, dp(8), padding, 0);

        EditText etTitle = createInput("Task title");
        EditText etSubject = createInput("Subject (e.g. CS 301)");
        EditText etCategory = createInput("Category (e.g. Reading, Assignment)");
        EditText etDuration = createInput("Duration (minutes)");
        etDuration.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        etTitle.setSingleLine(true);
        etSubject.setSingleLine(true);
        etCategory.setSingleLine(true);

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
        form.addView(etCategory, createFieldParams());
        form.addView(etDuration, createFieldParams());
        form.addView(btnPickDate, createFieldParams());

        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Create a study task")
                        .setView(form)
                        .setNegativeButton("Cancel", (d, which) -> d.dismiss())
                        .setNeutralButton(R.string.btn_ai_plan, (d, which) -> showAiPlannerWizard())
                        .setPositiveButton("Add task", null)
                        .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String title = etTitle.getText().toString().trim();
                String subject = etSubject.getText().toString().trim();
                String category = etCategory.getText().toString().trim();
                String durationStr = etDuration.getText().toString().trim();

                if (title.isEmpty()) {
                    etTitle.setError("Enter a task title");
                    etTitle.requestFocus();
                    return;
                }

                if (mAuth.getCurrentUser() == null) return;
                String userId = mAuth.getCurrentUser().getUid();

                long dueDate = selectedDate.getTimeInMillis();

                StudyTask task = new StudyTask();
                task.setTitle(title);
                task.setSubject(subject);
                task.setCategory(category);
                task.setDurationMinutes(durationStr.isEmpty() ? 45 : Integer.parseInt(durationStr));
                task.setPriority("MEDIUM");
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

    /**
     * AI Study Planner Wizard:
     * 1. Takes exam timetable (subjects & dates)
     * 2. Checks uploaded notes & portion coverage from Firestore
     * 3. Asks user's study stamina (focus session length e.g. 25, 45, 60, 90 mins) & daily availability
     * 4. Dynamically incorporates quiz diagnostic results and weak concepts
     * 5. Prompts Gemini to generate a tailored calendar schedule with what to do and when to do!
     */
    private void showAiPlannerWizard() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ai_planner_wizard, null);

        TextInputEditText etExamTimetable = dialogView.findViewById(R.id.etExamTimetable);
        ChipGroup cgQuickSubjects = dialogView.findViewById(R.id.cgQuickSubjects);
        TextView tvDetectedNotesSummary = dialogView.findViewById(R.id.tvDetectedNotesSummary);
        MaterialCheckBox cbAllPortionUploaded = dialogView.findViewById(R.id.cbAllPortionUploaded);

        ChipGroup cgStamina = dialogView.findViewById(R.id.cgStamina);
        ChipGroup cgDailyHours = dialogView.findViewById(R.id.cgDailyHours);

        TextView tvWeakConceptsStatus = dialogView.findViewById(R.id.tvWeakConceptsStatus);
        ChipGroup cgWeakConceptBadges = dialogView.findViewById(R.id.cgWeakConceptBadges);

        View layoutPlannerLoading = dialogView.findViewById(R.id.layoutPlannerLoading);
        View layoutActionButtons = dialogView.findViewById(R.id.layoutActionButtons);
        MaterialButton btnGenerateAiSchedule = dialogView.findViewById(R.id.btnGenerateAiSchedule);
        MaterialButton btnManualTaskOption = dialogView.findViewById(R.id.btnManualTaskOption);
        MaterialButton btnClearWeakConcepts = dialogView.findViewById(R.id.btnClearWeakConcepts);
        MaterialCheckBox cbReplacePlan = dialogView.findViewById(R.id.cbReplacePlan);

        currentDialogEtTimetable = etExamTimetable;
        currentDialogLayoutFileStatus = dialogView.findViewById(R.id.layoutTimetableFileStatus);
        currentDialogTvFileName = dialogView.findViewById(R.id.tvTimetableFileName);
        currentDialogPbParsing = dialogView.findViewById(R.id.pbTimetableParsing);

        MaterialButton btnUploadTimetableFile = dialogView.findViewById(R.id.btnUploadTimetableFile);
        if (btnUploadTimetableFile != null) {
            btnUploadTimetableFile.setOnClickListener(v -> timetablePickerLauncher.launch("*/*"));
        }

        View btnRemoveTimetableFile = dialogView.findViewById(R.id.btnRemoveTimetableFile);
        if (btnRemoveTimetableFile != null) {
            btnRemoveTimetableFile.setOnClickListener(v -> {
                if (currentDialogLayoutFileStatus != null) {
                    currentDialogLayoutFileStatus.setVisibility(View.GONE);
                }
            });
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        View btnCloseWizard = dialogView.findViewById(R.id.btnCloseWizard);
        if (btnCloseWizard != null) {
            btnCloseWizard.setOnClickListener(v -> dialog.dismiss());
        }

        final List<JSONObject> uploadedNotesList = new ArrayList<>();
        final Set<String> detectedSubjects = new HashSet<>();
        final List<String> detectedWeakConcepts = new ArrayList<>();
        final Map<String, Double> subjectMasteryMap = new HashMap<>();

        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();

            // 1. Fetch uploaded notes for syllabus portion check & quick subjects chips
            db.collection("users").document(userId).collection("notes")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (isFinishing() || isDestroyed()) return;

                        if (!queryDocumentSnapshots.isEmpty()) {
                            StringBuilder summarySb = new StringBuilder("Found in storage: ");
                            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                                Note note = doc.toObject(Note.class);
                                if (note != null) {
                                    String subj = note.getSubject() != null ? note.getSubject() : "General";
                                    detectedSubjects.add(subj);

                                    try {
                                        JSONObject noteObj = new JSONObject();
                                        noteObj.put("title", note.getTitle());
                                        noteObj.put("subject", subj);
                                        noteObj.put("id", doc.getId());
                                        uploadedNotesList.add(noteObj);
                                    } catch (Exception ignored) {}
                                }
                            }

                            int count = 0;
                            for (String subj : detectedSubjects) {
                                if (count > 0) summarySb.append(", ");
                                summarySb.append(subj);
                                count++;

                                // Add quick subject chip with Stitch pill styling
                                Chip chip = new Chip(PlannerActivity.this);
                                chip.setText("+ " + subj);
                                chip.setClickable(true);
                                chip.setTextColor(getColor(R.color.primary));
                                chip.setChipBackgroundColor(ColorStateList.valueOf(getColor(R.color.primary_light)));
                                chip.setChipStrokeColor(ColorStateList.valueOf(getColor(R.color.primary)));
                                chip.setChipStrokeWidth(dp(1));
                                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
                                chip.setOnClickListener(v -> {
                                    String current = etExamTimetable.getText() != null ? etExamTimetable.getText().toString() : "";
                                    if (!current.isEmpty() && !current.endsWith("\n")) {
                                        current += "\n";
                                    }
                                    current += subj + ": in 10 days";
                                    etExamTimetable.setText(current);
                                    etExamTimetable.setSelection(current.length());
                                });
                                cgQuickSubjects.addView(chip);
                            }
                            tvDetectedNotesSummary.setText(summarySb.toString());
                        } else {
                            tvDetectedNotesSummary.setText("No notes uploaded yet. AI will schedule self-study & note-taking sessions.");
                            cbAllPortionUploaded.setChecked(false);
                        }
                    });

            // 2. Fetch Quiz performance & weak concepts
            // First check SharedPreferences (instant fallback)
            SharedPreferences prefs = getSharedPreferences("StudySphereQuizPrefs", Context.MODE_PRIVATE);
            Map<String, ?> allEntries = prefs.getAll();
            for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                if (entry.getKey().startsWith("weak_")) {
                    try {
                        JSONArray arr = new JSONArray(entry.getValue().toString());
                        for (int i = 0; i < arr.length(); i++) {
                            String concept = arr.getString(i);
                            if (!detectedWeakConcepts.contains(concept)) {
                                detectedWeakConcepts.add(concept);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            // Also check Firestore subject_mastery
            db.collection("users").document(userId).collection("subject_mastery")
                    .get()
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        if (isFinishing() || isDestroyed()) return;

                        if (!queryDocumentSnapshots.isEmpty()) {
                            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                                String subj = doc.getString("subject");
                                Double score = doc.getDouble("masteryScore");
                                if (subj != null && score != null) {
                                    subjectMasteryMap.put(subj, score);
                                }
                                Object rawWeak = doc.get("weakConcepts");
                                if (rawWeak instanceof List<?>) {
                                    for (Object w : (List<?>) rawWeak) {
                                        if (w instanceof String && !detectedWeakConcepts.contains((String) w)) {
                                            detectedWeakConcepts.add((String) w);
                                        }
                                    }
                                }
                            }
                        }

                        renderWeakConceptsPreview(cgWeakConceptBadges, tvWeakConceptsStatus, btnClearWeakConcepts, detectedWeakConcepts);
                    })
                    .addOnFailureListener(e -> renderWeakConceptsPreview(cgWeakConceptBadges, tvWeakConceptsStatus, btnClearWeakConcepts, detectedWeakConcepts));
        }

        if (btnClearWeakConcepts != null) {
            btnClearWeakConcepts.setOnClickListener(v -> {
                detectedWeakConcepts.clear();
                SharedPreferences prefs = getSharedPreferences("StudySphereQuizPrefs", Context.MODE_PRIVATE);
                prefs.edit().clear().apply();
                if (mAuth.getCurrentUser() != null) {
                    String userId = mAuth.getCurrentUser().getUid();
                    db.collection("users").document(userId).collection("subject_mastery")
                            .get()
                            .addOnSuccessListener(querySnaps -> {
                                WriteBatch b = db.batch();
                                for (DocumentSnapshot doc : querySnaps) {
                                    b.update(doc.getReference(), "weakConcepts", Collections.emptyList());
                                }
                                b.commit();
                            });
                }
                renderWeakConceptsPreview(cgWeakConceptBadges, tvWeakConceptsStatus, btnClearWeakConcepts, detectedWeakConcepts);
                Toast.makeText(PlannerActivity.this, "Weak concepts cleared", Toast.LENGTH_SHORT).show();
            });
        }

        btnManualTaskOption.setOnClickListener(v -> {
            dialog.dismiss();
            showAddTaskDialog();
        });

        btnGenerateAiSchedule.setOnClickListener(v -> {
            String timetable = etExamTimetable.getText() != null ? etExamTimetable.getText().toString().trim() : "";
            if (timetable.isEmpty()) {
                etExamTimetable.setError("Please enter your exam dates or timetable");
                etExamTimetable.requestFocus();
                return;
            }

            // Calculate stamina
            int stamina = 45;
            int checkedStaminaId = cgStamina.getCheckedChipId();
            if (checkedStaminaId == R.id.chipStamina25) stamina = 25;
            else if (checkedStaminaId == R.id.chipStamina45) stamina = 45;
            else if (checkedStaminaId == R.id.chipStamina60) stamina = 60;
            else if (checkedStaminaId == R.id.chipStamina90) stamina = 90;

            // Calculate daily hours
            int dailyHours = 2;
            int checkedHoursId = cgDailyHours.getCheckedChipId();
            if (checkedHoursId == R.id.chipHour1) dailyHours = 1;
            else if (checkedHoursId == R.id.chipHour2) dailyHours = 2;
            else if (checkedHoursId == R.id.chipHour3) dailyHours = 3;
            else if (checkedHoursId == R.id.chipHour4) dailyHours = 4;

            boolean allPortionUploaded = cbAllPortionUploaded.isChecked();
            final boolean replaceExisting = (cbReplacePlan == null || cbReplacePlan.isChecked());

            layoutPlannerLoading.setVisibility(View.VISIBLE);
            layoutActionButtons.setVisibility(View.GONE);

            final int finalStamina = stamina;
            final int finalHours = dailyHours;

            executor.execute(() -> {
                try {
                    JSONObject body = new JSONObject();
                    body.put("clientDate", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()));
                    body.put("examTimetable", timetable);
                    body.put("sessionDurationMinutes", finalStamina);
                    body.put("availableHoursPerDay", finalHours);
                    body.put("allPortionUploaded", allPortionUploaded);

                    JSONArray notesArr = new JSONArray();
                    for (JSONObject n : uploadedNotesList) notesArr.put(n);
                    body.put("uploadedNotes", notesArr);

                    JSONArray weakArr = new JSONArray();
                    for (String w : detectedWeakConcepts) weakArr.put(w);
                    body.put("weakConcepts", weakArr);

                    JSONObject masteryObj = new JSONObject();
                    for (Map.Entry<String, Double> entry : subjectMasteryMap.entrySet()) {
                        masteryObj.put(entry.getKey(), entry.getValue());
                    }
                    body.put("subjectMastery", masteryObj);

                    URL url = new URL(PLANNER_API_URL);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(55000);
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
                        String summary = res.optString("summary", "AI Study Plan successfully created!");
                        String userId = mAuth.getCurrentUser().getUid();

                        if (tasksArr != null && tasksArr.length() > 0) {
                            savePlannerTasksToFirestore(userId, tasksArr, finalStamina, replaceExisting, summary, dialog, layoutPlannerLoading, layoutActionButtons);
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.e("PlannerActivity", "Failed to generate AI plan", e);
                }

                createFallbackPlannerSchedule(timetable, finalStamina, finalHours, allPortionUploaded, replaceExisting, detectedSubjects, detectedWeakConcepts, dialog, layoutPlannerLoading, layoutActionButtons);
            });
        });

        dialog.show();
    }

    private void savePlannerTasksToFirestore(String userId, JSONArray tasksArr, int stamina, boolean replaceExisting, String summary, androidx.appcompat.app.AlertDialog dialog, View loadingView, View actionsView) {
        if (replaceExisting) {
            db.collection("users").document(userId).collection("study_tasks")
                    .get()
                    .addOnSuccessListener(snap -> {
                        WriteBatch deleteBatch = db.batch();
                        for (DocumentSnapshot doc : snap) {
                            Boolean completed = doc.getBoolean("completed");
                            if (completed == null || !completed) {
                                deleteBatch.delete(doc.getReference());
                            }
                        }
                        deleteBatch.commit().addOnCompleteListener(t -> writeGeneratedTasks(userId, tasksArr, stamina, summary, dialog, loadingView, actionsView));
                    })
                    .addOnFailureListener(e -> writeGeneratedTasks(userId, tasksArr, stamina, summary, dialog, loadingView, actionsView));
        } else {
            writeGeneratedTasks(userId, tasksArr, stamina, summary, dialog, loadingView, actionsView);
        }
    }

    private void writeGeneratedTasks(String userId, JSONArray tasksArr, int stamina, String summary, androidx.appcompat.app.AlertDialog dialog, View loadingView, View actionsView) {
        try {
            WriteBatch batch = db.batch();
            for (int i = 0; i < tasksArr.length(); i++) {
                JSONObject tObj = tasksArr.getJSONObject(i);
                String tTitle = tObj.optString("title", "Study Session");
                tTitle = tTitle.replaceAll("[^\\x00-\\x7F]", "").trim();
                String tSub = tObj.optString("subject", "General");
                String dateStr = tObj.optString("date", "");
                String timeStr = tObj.optString("time", "16:00");
                int dur = tObj.optInt("durationMinutes", stamina);
                String prio = tObj.optString("priority", "HIGH");
                String cat = tObj.optString("category", "Weak Concept Mastery");
                String notes = tObj.optString("notes", "");

                long dueTimeMillis = parseDateTimeToMillis(dateStr, timeStr, i + 1);

                StudyTask newTask = new StudyTask();
                newTask.setTitle(tTitle);
                newTask.setSubject(tSub);
                newTask.setCategory(cat);
                newTask.setPriority(prio);
                newTask.setDurationMinutes(dur);
                newTask.setDueDate(dueTimeMillis);
                newTask.setTime(timeStr);
                newTask.setNotes(notes);
                newTask.setCompleted(false);
                newTask.setCreatedAt(System.currentTimeMillis());

                batch.set(db.collection("users").document(userId)
                        .collection("study_tasks").document(), newTask);
            }
            batch.commit().addOnSuccessListener(aVoid -> {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(PlannerActivity.this, summary, Toast.LENGTH_LONG).show();
                });
            }).addOnFailureListener(e -> {
                runOnUiThread(() -> {
                    if (loadingView != null) loadingView.setVisibility(View.GONE);
                    if (actionsView != null) actionsView.setVisibility(View.VISIBLE);
                    Toast.makeText(PlannerActivity.this, "Failed to save tasks: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            });
        } catch (Exception ex) {
            runOnUiThread(() -> {
                if (loadingView != null) loadingView.setVisibility(View.GONE);
                if (actionsView != null) actionsView.setVisibility(View.VISIBLE);
                Toast.makeText(PlannerActivity.this, "Error processing tasks: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    private void renderWeakConceptsPreview(ChipGroup cg, TextView status, View btnClear, List<String> weakList) {
        cg.removeAllViews();
        if (weakList.isEmpty()) {
            status.setText("No weak concepts found. Gemini will schedule balanced diagnostic quizzes.");
            if (btnClear != null) btnClear.setVisibility(View.GONE);
        } else {
            status.setText("Found " + weakList.size() + " weak concept(s) from your quiz history:");
            if (btnClear != null) btnClear.setVisibility(View.VISIBLE);
            for (String w : new ArrayList<>(weakList)) {
                Chip chip = new Chip(this);
                chip.setText(w);
                chip.setCloseIconVisible(true);
                chip.setChipBackgroundColorResource(R.color.warning_light);
                chip.setTextColor(getColor(R.color.text_primary));
                chip.setChipStrokeColor(ColorStateList.valueOf(getColor(R.color.warning)));
                chip.setChipStrokeWidth(dp(1));
                chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                chip.setOnCloseIconClickListener(v -> {
                    weakList.remove(w);
                    renderWeakConceptsPreview(cg, status, btnClear, weakList);
                });
                cg.addView(chip);
            }
        }
    }

    private void createFallbackPlannerSchedule(String timetable, int stamina, int dailyHours, boolean allPortionUploaded, boolean replaceExisting, Set<String> detectedSubjects, List<String> weakConcepts, androidx.appcompat.app.AlertDialog dialog, View loadingView, View actionsView) {
        if (mAuth.getCurrentUser() == null) {
            runOnUiThread(() -> {
                if (loadingView != null) loadingView.setVisibility(View.GONE);
                if (actionsView != null) actionsView.setVisibility(View.VISIBLE);
                Toast.makeText(PlannerActivity.this, "Please sign in to save your plan.", Toast.LENGTH_SHORT).show();
            });
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        List<String> subjects = new ArrayList<>();
        String[] lines = timetable.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String subj = trimmed;
            if (trimmed.contains(":")) {
                subj = trimmed.split(":")[0].trim();
            } else if (trimmed.contains("-")) {
                subj = trimmed.split("-")[0].trim();
            }
            if (!subj.isEmpty() && !subjects.contains(subj)) {
                subjects.add(subj);
            }
        }
        if (subjects.isEmpty()) {
            for (String s : detectedSubjects) {
                if (!subjects.contains(s)) subjects.add(s);
            }
        }
        if (subjects.isEmpty()) {
            subjects.add("General Studies");
        }

        WriteBatch batch = db.batch();
        int taskCount = 0;
        Calendar baseCal = Calendar.getInstance();

        for (int i = 0; i < subjects.size(); i++) {
            String subj = subjects.get(i);

            // Session 1: Core Revision & Concept Mastery
            baseCal.add(Calendar.DAY_OF_YEAR, i == 0 ? 0 : 1);
            baseCal.set(Calendar.HOUR_OF_DAY, 16);
            baseCal.set(Calendar.MINUTE, 0);

            StudyTask task1 = new StudyTask();
            task1.setTitle(subj + ": Core Revision & Key Takeaways");
            task1.setSubject(subj);
            task1.setCategory("Concept Mastery");
            task1.setPriority("HIGH");
            task1.setDurationMinutes(stamina);
            task1.setDueDate(baseCal.getTimeInMillis());
            task1.setTime("16:00");
            task1.setNotes("Review primary notes, definitions, formulas, and summarize high-yield exam sections.");
            task1.setCompleted(false);
            task1.setCreatedAt(System.currentTimeMillis());

            batch.set(db.collection("users").document(userId).collection("study_tasks").document(), task1);
            taskCount++;

            // Session 2: Weak concept / Practice questions
            String weakTopic = (weakConcepts != null && !weakConcepts.isEmpty()) ? weakConcepts.get(i % weakConcepts.size()) : subj + " Problem Solving";
            Calendar cal2 = (Calendar) baseCal.clone();
            cal2.set(Calendar.HOUR_OF_DAY, 18);
            cal2.set(Calendar.MINUTE, 30);

            StudyTask task2 = new StudyTask();
            task2.setTitle(subj + ": Practice Drill (" + weakTopic + ")");
            task2.setSubject(subj);
            task2.setCategory("Targeted Drill");
            task2.setPriority("HIGH");
            task2.setDurationMinutes(stamina);
            task2.setDueDate(cal2.getTimeInMillis());
            task2.setTime("18:30");
            task2.setNotes("Attempt active recall questions, solve previous year questions, and resolve weak concept gaps.");
            task2.setCompleted(false);
            task2.setCreatedAt(System.currentTimeMillis());

            batch.set(db.collection("users").document(userId).collection("study_tasks").document(), task2);
            taskCount++;
        }

        final int finalTaskCount = taskCount;
        Runnable commitInsert = () -> batch.commit().addOnSuccessListener(aVoid -> {
            runOnUiThread(() -> {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
                Toast.makeText(PlannerActivity.this, "AI Study Plan successfully created with " + finalTaskCount + " sessions!", Toast.LENGTH_LONG).show();
            });
        }).addOnFailureListener(e -> {
            runOnUiThread(() -> {
                if (loadingView != null) loadingView.setVisibility(View.GONE);
                if (actionsView != null) actionsView.setVisibility(View.VISIBLE);
                Toast.makeText(PlannerActivity.this, "Failed to save tasks: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        });

        if (replaceExisting) {
            db.collection("users").document(userId).collection("study_tasks")
                    .get()
                    .addOnSuccessListener(snap -> {
                        WriteBatch deleteBatch = db.batch();
                        for (DocumentSnapshot doc : snap) {
                            Boolean completed = doc.getBoolean("completed");
                            if (completed == null || !completed) {
                                deleteBatch.delete(doc.getReference());
                            }
                        }
                        deleteBatch.commit().addOnCompleteListener(t -> commitInsert.run());
                    })
                    .addOnFailureListener(e -> commitInsert.run());
        } else {
            commitInsert.run();
        }
    }

    private long parseDateTimeToMillis(String dateStr, String timeStr, int dayOffset) {
        if (dateStr != null && !dateStr.trim().isEmpty()) {
            try {
                String combine = dateStr.trim();
                SimpleDateFormat sdf;
                if (timeStr != null && !timeStr.trim().isEmpty()) {
                    combine += " " + timeStr.trim();
                    sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                } else {
                    combine += " 16:00";
                    sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                }
                Date parsed = sdf.parse(combine);
                if (parsed != null) return parsed.getTime();
            } catch (Exception ignored) {}
        }
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, dayOffset);
        cal.set(Calendar.HOUR_OF_DAY, 16);
        cal.set(Calendar.MINUTE, 0);
        return cal.getTimeInMillis();
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

    private void setupBottomNavigation() {
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_planner);
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.nav_notes) {
                startActivity(new Intent(this, NotesActivity.class));
                finish();
                return true;
            } else if (itemId == R.id.nav_planner) {
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void onTimetableFileSelected(Uri fileUri) {
        String fileName = getFileName(fileUri);

        if (currentDialogLayoutFileStatus != null) {
            currentDialogLayoutFileStatus.setVisibility(View.VISIBLE);
        }
        if (currentDialogTvFileName != null) {
            currentDialogTvFileName.setText("Reading " + fileName + " with AI...");
        }
        if (currentDialogPbParsing != null) {
            currentDialogPbParsing.setVisibility(View.VISIBLE);
        }

        executor.execute(() -> {
            try {
                String boundary = "===" + System.currentTimeMillis() + "===";
                URL url = new URL(PARSE_TIMETABLE_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(60000);

                OutputStream os = conn.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);

                writer.append("--" + boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"").append("\r\n");
                writer.append("Content-Type: application/octet-stream").append("\r\n");
                writer.append("\r\n").flush();

                InputStream is = getContentResolver().openInputStream(fileUri);
                byte[] buffer = new byte[4096];
                int bytesRead;
                if (is != null) {
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                    os.flush();
                    is.close();
                }

                writer.append("\r\n").flush();
                writer.append("--" + boundary + "--").append("\r\n").flush();
                writer.close();

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();

                    JSONObject resJson = new JSONObject(sb.toString());
                    String timetableText = resJson.optString("timetableText", "");

                    runOnUiThread(() -> {
                        if (currentDialogPbParsing != null) {
                            currentDialogPbParsing.setVisibility(View.GONE);
                        }
                        if (currentDialogTvFileName != null) {
                            currentDialogTvFileName.setText("Attached: " + fileName + " (Extracted)");
                        }
                        if (currentDialogEtTimetable != null && !timetableText.isEmpty()) {
                            currentDialogEtTimetable.setText(timetableText);
                        }
                        Toast.makeText(PlannerActivity.this, "Exam timetable parsed with Gemini!", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
            } catch (Exception e) {
                Log.e("PlannerActivity", "Failed to parse timetable file", e);
            }

            runOnUiThread(() -> {
                if (currentDialogPbParsing != null) {
                    currentDialogPbParsing.setVisibility(View.GONE);
                }
                if (currentDialogTvFileName != null) {
                    currentDialogTvFileName.setText("Could not parse " + fileName);
                }
                Toast.makeText(PlannerActivity.this, "Could not extract timetable from file. You can still type it manually.", Toast.LENGTH_LONG).show();
            });
        });
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1 && result != null) {
                result = result.substring(cut + 1);
            }
        }
        return result != null ? result : "exam_timetable.pdf";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tasksListener != null) tasksListener.remove();
    }
}