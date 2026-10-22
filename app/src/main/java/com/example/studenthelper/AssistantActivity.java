package com.example.studenthelper;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AssistantActivity extends AppCompatActivity {

    private static final String CHAT_API_URL = "http://10.0.2.2:3000/api/chat";

    private TextInputEditText etMessage;
    private LinearLayout messagesContainer;
    private ScrollView scrollMessages;
    private MaterialButton btnSend;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isWaitingForResponse = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant);

        etMessage = findViewById(R.id.etMessage);
        messagesContainer = findViewById(R.id.messagesContainer);
        scrollMessages = findViewById(R.id.scrollMessages);
        btnSend = findViewById(R.id.btnSend);

        MaterialButton btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        if (isWaitingForResponse) return;
        
        if (etMessage.getText() == null) {
            return;
        }

        String message = etMessage.getText().toString().trim();

        if (message.isEmpty()) {
            Toast.makeText(this, "Please enter a question.", Toast.LENGTH_SHORT).show();
            return;
        }

        addMessage(message, true);
        etMessage.setText("");

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etMessage.getWindowToken(), 0);
        }

        btnSend.setEnabled(false);
        isWaitingForResponse = true;

        TextView replyView = addMessage("Thinking...", false);

        executor.execute(() -> {
            String result;
            try {
                result = requestAiAnswer(message);
            } catch (Exception e) {
                result = "Sorry, I couldn't get an AI answer.\n\n" + e.getMessage();
            }

            final String finalResult = result;

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }

                replyView.setText(finalResult);
                btnSend.setEnabled(true);
                isWaitingForResponse = false;
                scrollToBottom();
            });
        });
    }

    private String requestAiAnswer(String message) throws Exception {
        HttpURLConnection connection = null;

        try {
            URL url = new URL(CHAT_API_URL);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(70000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            JSONObject requestBody = new JSONObject();
            requestBody.put("message", message);

            byte[] requestBytes = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(requestBytes.length);

            try (OutputStream output = connection.getOutputStream()) {
                output.write(requestBytes);
                output.flush();
            }

            int statusCode = connection.getResponseCode();
            InputStream inputStream = statusCode >= 200 && statusCode < 300
                            ? connection.getInputStream()
                            : connection.getErrorStream();

            String responseText = "";
            if (inputStream != null) {
                try (InputStream stream = inputStream;
                     ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    byte[] data = new byte[4096];
                    int count;
                    while ((count = stream.read(data)) != -1) {
                        buffer.write(data, 0, count);
                    }
                    responseText = buffer.toString("UTF-8");
                }
            }

            JSONObject responseJson;
            try {
                responseJson = new JSONObject(responseText);
            } catch (Exception e) {
                throw new IOException("Invalid backend response. HTTP " + statusCode);
            }

            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException(responseJson.optString("error", "Request failed: HTTP " + statusCode));
            }

            String answer = responseJson.optString("answer", "").trim();
            if (answer.isEmpty()) {
                throw new IOException("The AI returned an empty answer.");
            }
            return answer;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private TextView addMessage(String message, boolean isUser) {
        TextView messageView = new TextView(this);

        messageView.setText(message);
        messageView.setTextSize(16);
        messageView.setTextColor(isUser ? Color.WHITE : getResources().getColor(R.color.text_primary));
        
        int paddingH = dp(16);
        int paddingV = dp(12);
        messageView.setPadding(paddingH, paddingV, paddingH, paddingV);
        messageView.setBackgroundResource(isUser ? R.drawable.bg_bubble_user : R.drawable.bg_bubble_ai);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        params.gravity = isUser ? Gravity.END : Gravity.START;
        params.setMargins(0, dp(4), 0, dp(4));

        messageView.setLayoutParams(params);
        messagesContainer.addView(messageView);

        scrollToBottom();
        return messageView;
    }

    private void scrollToBottom() {
        scrollMessages.post(() -> scrollMessages.fullScroll(View.FOCUS_DOWN));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
