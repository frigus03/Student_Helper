package com.example.studenthelper;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class PlannerActivity extends AppCompatActivity {

    private DatabaseHelper databaseHelper;
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

    private ArrayList<StudyTask> allTasks = new ArrayList<>();

    private String currentFilter = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_planner);

        databaseHelper = new DatabaseHelper(this);

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

        plannerAdapter = new PlannerAdapter(
                new PlannerAdapter.TaskListener() {

                    @Override
                    public void onCompletionChanged(
                            StudyTask task,
                            boolean completed
                    ) {
                        boolean updated =
                                databaseHelper.updateTaskStatus(
                                        task.getId(),
                                        completed
                                );

                        if (updated) {
                            loadTasks();
                        } else {
                            Toast.makeText(
                                    PlannerActivity.this,
                                    "Could not update task",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }

                    @Override
                    public void onDeleteTask(StudyTask task) {
                        confirmDelete(task);
                    }
                }
        );

        rvTasks.setAdapter(plannerAdapter);
    }

    private void setupSearch() {
        etSearchTasks.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
                // Not needed.
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not needed.
            }
        });
    }

    private void setupFilters() {
        taskFilterGroup.addOnButtonCheckedListener(
                (group, checkedId, isChecked) -> {

                    if (!isChecked) {
                        return;
                    }

                    if (checkedId == R.id.filterAll) {
                        currentFilter = "all";
                    } else if (checkedId == R.id.filterPending) {
                        currentFilter = "pending";
                    } else if (checkedId == R.id.filterCompleted) {
                        currentFilter = "completed";
                    }

                    applyFilters();
                }
        );
    }

    private void setupAddButton() {
        MaterialButton btnAddTask = findViewById(R.id.btnAddTask);
        btnAddTask.setOnClickListener(v -> showAddTaskDialog());
    }

    private void loadTasks() {
        allTasks = databaseHelper.getAllTasks();

        updateProgress();
        applyFilters();
    }

    private void updateProgress() {
        int total = allTasks.size();
        int completed = 0;

        for (StudyTask task : allTasks) {
            if (task.isCompleted()) {
                completed++;
            }
        }

        int pending = total - completed;

        int percentage = total == 0
                ? 0
                : Math.round(completed * 100f / total);

        tvPlannerCompleted.setText(String.valueOf(completed));
        tvPlannerPending.setText(String.valueOf(pending));
        tvPlannerPercent.setText(percentage + "%");

        progressPlanner.setProgress(percentage);
    }

    private void applyFilters() {
        ArrayList<StudyTask> filteredTasks = new ArrayList<>();

        String query = etSearchTasks.getText()
                .toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        for (StudyTask task : allTasks) {

            boolean matchesSearch =
                    task.getTitle().toLowerCase(Locale.ROOT).contains(query)
                            || task.getSubject().toLowerCase(Locale.ROOT).contains(query);

            boolean matchesFilter =
                    currentFilter.equals("all")
                            || (currentFilter.equals("pending")
                            && !task.isCompleted())
                            || (currentFilter.equals("completed")
                            && task.isCompleted());

            if (matchesSearch && matchesFilter) {
                filteredTasks.add(task);
            }
        }

        plannerAdapter.setTasks(filteredTasks);

        tvTaskListHeading.setText(
                "YOUR TASKS (" + filteredTasks.size() + ")"
        );

        boolean empty = filteredTasks.isEmpty();

        rvTasks.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutEmptyTasks.setVisibility(empty ? View.VISIBLE : View.GONE);

        if (allTasks.isEmpty()) {
            tvEmptyTasksMessage.setText(
                    "Add your first study task to get organized."
            );
        } else if (!query.isEmpty() || !currentFilter.equals("all")) {
            tvEmptyTasksMessage.setText(
                    "No tasks match your current search or filter."
            );
        } else {
            tvEmptyTasksMessage.setText(
                    "You're all caught up! Add another task whenever you're ready."
            );
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
        selectedDate.set(
                Calendar.HOUR_OF_DAY, 0
        );
        selectedDate.set(
                Calendar.MINUTE, 0
        );
        selectedDate.set(
                Calendar.SECOND, 0
        );
        selectedDate.set(
                Calendar.MILLISECOND, 0
        );

        MaterialButton btnChooseDate = new MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle
        );

        btnChooseDate.setText(getDateLabel(selectedDate));
        btnChooseDate.setIconResource(android.R.drawable.ic_menu_my_calendar);

        btnChooseDate.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    PlannerActivity.this,
                    (view, year, month, dayOfMonth) -> {
                        selectedDate.set(
                                year,
                                month,
                                dayOfMonth,
                                0,
                                0,
                                0
                        );
                        selectedDate.set(
                                Calendar.MILLISECOND, 0
                        );

                        btnChooseDate.setText(
                                getDateLabel(selectedDate)
                        );
                    },
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH)
            );

            Calendar minimumDate = Calendar.getInstance();

            minimumDate.set(
                    Calendar.HOUR_OF_DAY, 0
            );
            minimumDate.set(
                    Calendar.MINUTE, 0
            );
            minimumDate.set(
                    Calendar.SECOND, 0
            );
            minimumDate.set(
                    Calendar.MILLISECOND, 0
            );

            datePickerDialog.getDatePicker().setMinDate(
                    minimumDate.getTimeInMillis()
            );

            datePickerDialog.show();
        });

        form.addView(etTitle, createFieldParams());
        form.addView(etSubject, createFieldParams());
        form.addView(btnChooseDate, createFieldParams());

        androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Create study task")
                        .setView(form)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Save task", null)
                        .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(
                    androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {

                String title = etTitle.getText()
                        .toString().trim();

                String subject = etSubject.getText()
                        .toString().trim();

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

                long dueDate = selectedDate.getTimeInMillis();

                long result = databaseHelper.insertTask(
                        title,
                        subject,
                        dueDate
                );

                if (result != -1) {
                    dialog.dismiss();

                    Toast.makeText(
                            this,
                            "Study task added!",
                            Toast.LENGTH_SHORT
                    ).show();

                    loadTasks();
                } else {
                    Toast.makeText(
                            this,
                            "Could not save task. Please try again.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        });

        dialog.show();
    }

    private void confirmDelete(StudyTask task) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete task?")
                .setMessage(
                        "Are you sure you want to delete \""
                                + task.getTitle()
                                + "\"?"
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {

                    boolean deleted =
                            databaseHelper.deleteTask(task.getId());

                    if (deleted) {
                        Toast.makeText(
                                this,
                                "Task deleted",
                                Toast.LENGTH_SHORT
                        ).show();

                        loadTasks();
                    } else {
                        Toast.makeText(
                                this,
                                "Could not delete task",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
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
        java.text.SimpleDateFormat formatter =
                new java.text.SimpleDateFormat(
                        "EEE, dd MMM yyyy",
                        Locale.getDefault()
                );

        return "Due date: " + formatter.format(
                calendar.getTime()
        );
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (databaseHelper != null && rvTasks != null) {
            loadTasks();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (databaseHelper != null) {
            databaseHelper.close();
        }
    }
}