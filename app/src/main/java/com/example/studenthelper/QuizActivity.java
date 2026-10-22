package com.example.studenthelper;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class QuizActivity extends AppCompatActivity {

    public static final String EXTRA_SUBJECT = "extra_subject";
    public static final String EXTRA_MATERIAL = "extra_material";
    private static final String QUIZ_API_URL = "http://10.0.2.2:3000/api/quiz/generate";

    private TextView tvQuestionNumber;
    private TextView tvQuestionCategory;
    private TextView tvQuestion;
    private TextView tvExplanation;
    private TextView tvResultTitle;
    private TextView tvResultScore;
    private TextView tvResultMessage;
    private TextView tvResultAccuracy;

    private ProgressBar quizProgress;

    private MaterialButton[] optionButtons;
    private MaterialButton btnNextQuestion;
    private MaterialButton btnRetryQuiz;
    private MaterialButton btnExitQuiz;

    private android.view.View quizContainer;
    private android.view.View resultsContainer;

    private final List<Question> questions = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private String currentSubject = "General";
    private String currentMaterial = "";
    private float currentMastery = 0.45f;

    private int currentQuestionIndex = 0;
    private int score = 0;
    private boolean answerSelected = false;

    private final int defaultButtonColor = Color.parseColor("#F1F0FA");
    private final int defaultTextColor = Color.parseColor("#28243D");
    private final int correctColor = Color.parseColor("#D9F5E5");
    private final int correctTextColor = Color.parseColor("#176B3A");
    private final int wrongColor = Color.parseColor("#FFE1E1");
    private final int wrongTextColor = Color.parseColor("#B42318");
    private final int primaryColor = Color.parseColor("#6558D3");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        if (getIntent().hasExtra(EXTRA_SUBJECT)) {
            currentSubject = getIntent().getStringExtra(EXTRA_SUBJECT);
        }
        if (getIntent().hasExtra(EXTRA_MATERIAL)) {
            currentMaterial = getIntent().getStringExtra(EXTRA_MATERIAL);
        }

        initializeViews();
        setupListeners();
        fetchAdaptiveQuizOrFallback();
    }

    private void initializeViews() {
        tvQuestionNumber = findViewById(R.id.tvQuestionNumber);
        tvQuestionCategory = findViewById(R.id.tvQuestionCategory);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvExplanation = findViewById(R.id.tvExplanation);

        tvResultTitle = findViewById(R.id.tvResultTitle);
        tvResultScore = findViewById(R.id.tvResultScore);
        tvResultMessage = findViewById(R.id.tvResultMessage);
        tvResultAccuracy = findViewById(R.id.tvResultAccuracy);

        quizProgress = findViewById(R.id.quizProgress);

        optionButtons = new MaterialButton[]{
                findViewById(R.id.btnOption1),
                findViewById(R.id.btnOption2),
                findViewById(R.id.btnOption3),
                findViewById(R.id.btnOption4)
        };

        btnNextQuestion = findViewById(R.id.btnNextQuestion);
        btnRetryQuiz = findViewById(R.id.btnRetryQuiz);
        btnExitQuiz = findViewById(R.id.btnExitQuiz);

        quizContainer = findViewById(R.id.quizContainer);
        resultsContainer = findViewById(R.id.resultsContainer);
    }

    private void fetchAdaptiveQuizOrFallback() {
        tvQuestion.setText("Generating adaptive quiz tailored to your learning level...");
        tvQuestionCategory.setText(currentSubject);

        if (mAuth.getCurrentUser() != null) {
            String userId = mAuth.getCurrentUser().getUid();
            db.collection("users").document(userId)
                    .collection("subject_mastery").document(currentSubject)
                    .get()
                    .addOnSuccessListener(snap -> {
                        if (snap.exists() && snap.contains("masteryScore")) {
                            Double scoreObj = snap.getDouble("masteryScore");
                            if (scoreObj != null) currentMastery = scoreObj.floatValue();
                        }
                        requestAdaptiveQuizFromBackend();
                    })
                    .addOnFailureListener(e -> requestAdaptiveQuizFromBackend());
        } else {
            requestAdaptiveQuizFromBackend();
        }
    }

    private void requestAdaptiveQuizFromBackend() {
        executor.execute(() -> {
            boolean success = false;
            try {
                JSONObject requestBody = new JSONObject();
                requestBody.put("subject", currentSubject);
                requestBody.put("material", currentMaterial);
                requestBody.put("masteryLevel", currentMastery);
                requestBody.put("count", 5);

                URL url = new URL(QUIZ_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(45000);
                conn.setDoOutput(true);

                byte[] postBytes = requestBody.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postBytes);
                }

                int statusCode = conn.getResponseCode();
                InputStream is = statusCode == 200 ? conn.getInputStream() : conn.getErrorStream();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[1024];
                int nRead;
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                String responseStr = buffer.toString(StandardCharsets.UTF_8.name());

                if (statusCode == 200) {
                    JSONObject resJson = new JSONObject(responseStr);
                    if (resJson.optBoolean("success", false)) {
                        JSONArray questionsArr = resJson.getJSONArray("questions");
                        List<Question> parsed = new ArrayList<>();
                        for (int i = 0; i < questionsArr.length(); i++) {
                            JSONObject qObj = questionsArr.getJSONObject(i);
                            String qText = qObj.getString("question");
                            JSONArray optsArr = qObj.getJSONArray("options");
                            String[] options = new String[optsArr.length()];
                            for (int j = 0; j < optsArr.length(); j++) {
                                options[j] = optsArr.getString(j);
                            }
                            int correctIdx = qObj.getInt("correctIndex");
                            String explanation = qObj.optString("explanation", "Correct!");
                            parsed.add(new Question(currentSubject, qText, options, correctIdx, explanation));
                        }

                        if (!parsed.isEmpty()) {
                            runOnUiThread(() -> {
                                questions.clear();
                                questions.addAll(parsed);
                                currentQuestionIndex = 0;
                                score = 0;
                                loadQuestion();
                            });
                            success = true;
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            if (!success) {
                runOnUiThread(() -> {
                    createQuestionBank();
                    loadQuestion();
                });
            }
        });
    }

    private void createQuestionBank() {
        questions.clear();

        questions.add(new Question(
                currentSubject.isEmpty() ? "Machine Learning" : currentSubject,
                "What is overfitting in machine learning?",
                new String[]{
                        "The model is too simple",
                        "The model memorizes training patterns too closely",
                        "The dataset has no labels",
                        "The model never learns"
                },
                1,
                "An overfitted model performs well on training data but often performs poorly on unseen data."
        ));

        questions.add(new Question(
                currentSubject.isEmpty() ? "Algorithms" : currentSubject,
                "What is the average time complexity of QuickSort?",
                new String[]{
                        "O(n)",
                        "O(n log n)",
                        "O(n^2)",
                        "O(1)"
                },
                1,
                "QuickSort typically runs in O(n log n) average time complexity."
        ));

        questions.add(new Question(
                currentSubject.isEmpty() ? "Computer Science" : currentSubject,
                "Which data structure uses LIFO (Last In First Out)?",
                new String[]{
                        "Queue",
                        "Stack",
                        "Array",
                        "Binary Tree"
                },
                1,
                "A Stack follows the Last In, First Out (LIFO) principle."
        ));
    }

    private void setupListeners() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        for (int i = 0; i < optionButtons.length; i++) {
            final int selectedOption = i;

            optionButtons[i].setOnClickListener(v -> {
                if (!answerSelected) {
                    checkAnswer(selectedOption);
                }
            });
        }

        btnNextQuestion.setOnClickListener(v -> {
            if (!answerSelected) {
                Toast.makeText(this, "Choose an answer first.", Toast.LENGTH_SHORT).show();
                return;
            }

            currentQuestionIndex++;

            if (currentQuestionIndex < questions.size()) {
                loadQuestion();
            } else {
                showResults();
            }
        });

        btnRetryQuiz.setOnClickListener(v -> restartQuiz());
        btnExitQuiz.setOnClickListener(v -> finish());
    }

    private void loadQuestion() {
        if (questions.isEmpty()) return;

        answerSelected = false;
        Question question = questions.get(currentQuestionIndex);

        quizContainer.setVisibility(android.view.View.VISIBLE);
        resultsContainer.setVisibility(android.view.View.GONE);

        tvQuestionNumber.setText(
                String.format(
                        Locale.getDefault(),
                        "QUESTION %02d OF %02d",
                        currentQuestionIndex + 1,
                        questions.size()
                )
        );

        tvQuestionCategory.setText(question.category);
        tvQuestion.setText(question.question);

        quizProgress.setMax(questions.size());
        quizProgress.setProgress(currentQuestionIndex + 1);

        for (int i = 0; i < optionButtons.length; i++) {
            if (i < question.options.length) {
                optionButtons[i].setVisibility(android.view.View.VISIBLE);
                optionButtons[i].setText(
                        String.format(
                                Locale.getDefault(),
                                "%s. %s",
                                (char) ('A' + i),
                                question.options[i]
                        )
                );
                optionButtons[i].setEnabled(true);
                optionButtons[i].setBackgroundTintList(ColorStateList.valueOf(defaultButtonColor));
                optionButtons[i].setTextColor(defaultTextColor);
            } else {
                optionButtons[i].setVisibility(android.view.View.GONE);
            }
        }

        tvExplanation.setVisibility(android.view.View.GONE);
        tvExplanation.setText("");

        btnNextQuestion.setEnabled(false);
        btnNextQuestion.setText(
                currentQuestionIndex == questions.size() - 1
                        ? "View Results"
                        : "Next Question"
        );
    }

    private void checkAnswer(int selectedOption) {
        answerSelected = true;

        Question question = questions.get(currentQuestionIndex);
        boolean isCorrect = selectedOption == question.correctAnswer;

        if (isCorrect) {
            score++;
        }

        for (int i = 0; i < optionButtons.length; i++) {
            if (i >= question.options.length) continue;
            optionButtons[i].setEnabled(false);

            if (i == question.correctAnswer) {
                optionButtons[i].setBackgroundTintList(ColorStateList.valueOf(correctColor));
                optionButtons[i].setTextColor(correctTextColor);
            } else if (i == selectedOption && !isCorrect) {
                optionButtons[i].setBackgroundTintList(ColorStateList.valueOf(wrongColor));
                optionButtons[i].setTextColor(wrongTextColor);
            }
        }

        tvExplanation.setText(
                String.format(
                        Locale.getDefault(),
                        "%s %s",
                        isCorrect ? "Correct!" : "Incorrect.",
                        question.explanation
                )
        );

        tvExplanation.setTextColor(
                isCorrect ? correctTextColor : wrongTextColor
        );

        tvExplanation.setVisibility(android.view.View.VISIBLE);
        btnNextQuestion.setEnabled(true);
    }

    private void showResults() {
        quizContainer.setVisibility(android.view.View.GONE);
        resultsContainer.setVisibility(android.view.View.VISIBLE);

        int percentage = Math.round((score * 100f) / questions.size());

        tvResultScore.setText(
                String.format(Locale.getDefault(), "%d / %d", score, questions.size())
        );

        tvResultAccuracy.setText(percentage + "% accuracy");

        if (percentage >= 80) {
            tvResultTitle.setText("Excellent work!");
            tvResultMessage.setText("Strong mastery demonstrated. Keep up the high standard!");
        } else if (percentage >= 50) {
            tvResultTitle.setText("Good effort!");
            tvResultMessage.setText("You're making steady progress. Next quiz will reinforce your weaker spots.");
        } else {
            tvResultTitle.setText("Learning in progress!");
            tvResultMessage.setText("Adaptive tutor noticed this topic needs more practice. We'll start with basics next time.");
        }

        quizProgress.setProgress(questions.size());

        // Persist student's learning curve in Firestore
        saveLearnerProgress(percentage / 100.0f);
    }

    private void saveLearnerProgress(float accuracyFraction) {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        Map<String, Object> progress = new HashMap<>();
        progress.put("subject", currentSubject);
        progress.put("masteryScore", accuracyFraction);
        progress.put("lastAttempted", System.currentTimeMillis());

        db.collection("users").document(userId)
                .collection("subject_mastery").document(currentSubject)
                .set(progress);
    }

    private void restartQuiz() {
        score = 0;
        currentQuestionIndex = 0;
        answerSelected = false;
        loadQuestion();
    }

    private static class Question {
        final String category;
        final String question;
        final String[] options;
        final int correctAnswer;
        final String explanation;

        Question(
                String category,
                String question,
                String[] options,
                int correctAnswer,
                String explanation
        ) {
            this.category = category;
            this.question = question;
            this.options = Arrays.copyOf(options, options.length);
            this.correctAnswer = correctAnswer;
            this.explanation = explanation;
        }
    }
}
