
package com.example.studenthelper;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class QuizActivity extends AppCompatActivity {

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

        initializeViews();
        createQuestionBank();
        setupListeners();
        loadQuestion();
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

    private void createQuestionBank() {
        questions.clear();

        questions.add(new Question(
                "NLP",
                "What does TF-IDF help measure in a document?",
                new String[]{
                        "The importance of a word",
                        "The number of sentences",
                        "The language of a document",
                        "The size of a vocabulary"
                },
                0,
                "TF-IDF gives a word more weight when it is frequent in a document but less common across the overall collection."
        ));

        questions.add(new Question(
                "Machine Learning",
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
                "Computer Networks",
                "What does CSMA/CD do when a collision is detected?",
                new String[]{
                        "Immediately shuts down the network",
                        "Continues transmitting without changes",
                        "Stops transmission and uses a backoff process",
                        "Converts the frame into a packet"
                },
                2,
                "CSMA/CD detects collisions, stops the transmission and waits for a backoff interval before retrying."
        ));

        questions.add(new Question(
                "Quantum Computing",
                "Which quantum gate creates an equal superposition from the |0⟩ state?",
                new String[]{
                        "Pauli-X gate",
                        "Hadamard gate",
                        "CNOT gate",
                        "Identity gate"
                },
                1,
                "Applying a Hadamard gate to |0⟩ produces an equal superposition of |0⟩ and |1⟩."
        ));

        questions.add(new Question(
                "NLP",
                "What is the main purpose of a Hidden Markov Model?",
                new String[]{
                        "To model sequences with hidden states",
                        "To sort numerical arrays",
                        "To create database tables",
                        "To compress images only"
                },
                0,
                "An HMM models sequential observations using hidden states and transition and emission probabilities."
        ));

        questions.add(new Question(
                "Machine Learning",
                "Why is a dataset divided into training and testing sets?",
                new String[]{
                        "To increase the number of features",
                        "To remove every missing value automatically",
                        "To evaluate performance on unseen data",
                        "To guarantee 100% accuracy"
                },
                2,
                "The test set estimates how well a trained model generalizes to data it has not seen during training."
        ));

        questions.add(new Question(
                "Computer Networks",
                "In Slotted ALOHA, when may a device begin transmitting?",
                new String[]{
                        "Only at the start of a time slot",
                        "At any instant without restrictions",
                        "Only after every device is silent forever",
                        "Only when the network is disconnected"
                },
                0,
                "Slotted ALOHA divides time into slots, and transmissions begin at slot boundaries."
        ));

        questions.add(new Question(
                "AI Basics",
                "Which is an example of supervised learning?",
                new String[]{
                        "Grouping data without labels",
                        "Learning from labelled examples",
                        "Randomly changing model outputs",
                        "Storing files without processing them"
                },
                1,
                "Supervised learning uses labelled examples to learn a mapping from inputs to known target outputs."
        ));

        questions.add(new Question(
                "NLP",
                "What does dependency parsing identify?",
                new String[]{
                        "Relationships between words in a sentence",
                        "The pixel count of an image",
                        "The speed of a network",
                        "The size of a database"
                },
                0,
                "Dependency parsing identifies grammatical relationships between words, such as subject and object relations."
        ));

        questions.add(new Question(
                "Quantum Computing",
                "What is a qubit?",
                new String[]{
                        "A classical byte containing eight bits",
                        "A quantum unit of information",
                        "A type of network cable",
                        "A machine-learning dataset"
                },
                1,
                "A qubit is a quantum information unit that can exist in a superposition of basis states."
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
                Toast.makeText(
                        this,
                        "Choose an answer first.",
                        Toast.LENGTH_SHORT
                ).show();
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
            optionButtons[i].setText(
                    String.format(
                            Locale.getDefault(),
                            "%s. %s",
                            (char) ('A' + i),
                            question.options[i]
                    )
            );

            optionButtons[i].setEnabled(true);
            optionButtons[i].setBackgroundTintList(
                    ColorStateList.valueOf(defaultButtonColor)
            );
            optionButtons[i].setTextColor(defaultTextColor);
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

        // Highlight the correct answer.
        optionButtons[question.correctAnswer].setBackgroundTintList(
                ColorStateList.valueOf(correctColor)
        );
        optionButtons[question.correctAnswer].setTextColor(correctTextColor);

        // Highlight an incorrect selection.
        if (!isCorrect) {
            optionButtons[selectedOption].setBackgroundTintList(
                    ColorStateList.valueOf(wrongColor)
            );
            optionButtons[selectedOption].setTextColor(wrongTextColor);
        }

        for (MaterialButton button : optionButtons) {
            button.setEnabled(false);
        }

        tvExplanation.setText(
                (isCorrect ? "Correct! " : "Not quite. ") +
                        question.explanation
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

        int percentage = Math.round(
                (score * 100f) / questions.size()
        );

        tvResultScore.setText(
                String.format(
                        Locale.getDefault(),
                        "%d / %d",
                        score,
                        questions.size()
                )
        );

        tvResultAccuracy.setText(percentage + "% accuracy");

        if (percentage >= 80) {
            tvResultTitle.setText("Excellent work!");
            tvResultMessage.setText(
                    "You've built a strong foundation. Keep challenging yourself!"
            );
        } else if (percentage >= 50) {
            tvResultTitle.setText("Good effort!");
            tvResultMessage.setText(
                    "You're making progress. Review the concepts you missed and try again."
            );
        } else {
            tvResultTitle.setText("Keep practising!");
            tvResultMessage.setText(
                    "Every attempt helps you learn. Review the explanations and give it another try."
            );
        }

        quizProgress.setProgress(questions.size());
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
