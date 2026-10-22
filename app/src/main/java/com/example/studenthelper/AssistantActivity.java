
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

public class AssistantActivity extends AppCompatActivity {

    private TextInputEditText etMessage;
    private LinearLayout messagesContainer;
    private ScrollView scrollMessages;
    private MaterialButton btnSend;

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

        etMessage.setOnEditorActionListener((textView, actionId, event) -> {
            sendMessage();
            return true;
        });
    }

    private void sendMessage() {
        if (etMessage.getText() == null) {
            return;
        }

        String message = etMessage.getText().toString().trim();

        if (message.isEmpty()) {
            Toast.makeText(
                    this,
                    "Please enter a question first.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        addMessage(message, true);
        etMessage.setText("");

        // Hide the keyboard after sending.
        InputMethodManager imm =
                (InputMethodManager) getSystemService(
                        Context.INPUT_METHOD_SERVICE
                );

        if (imm != null) {
            imm.hideSoftInputFromWindow(
                    etMessage.getWindowToken(),
                    0
            );
        }

        // Temporary response until the AI backend is connected.
        addMessage(
                "Your message was received! The AI service will be connected in the next step.",
                false
        );
    }

    private void addMessage(String message, boolean isUser) {
        TextView messageView = new TextView(this);

        messageView.setText(message);
        messageView.setTextSize(14);
        messageView.setTextColor(
                isUser ? Color.WHITE : Color.BLACK
        );
        messageView.setPadding(16, 12, 16, 12);
        messageView.setBackgroundColor(
                isUser
                        ? Color.rgb(103, 80, 164)
                        : Color.rgb(245, 245, 250)
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );

        params.gravity = isUser ? Gravity.END : Gravity.START;
        params.setMargins(0, 6, 0, 6);

        messageView.setLayoutParams(params);
        messagesContainer.addView(messageView);

        scrollMessages.post(() ->
                scrollMessages.fullScroll(View.FOCUS_DOWN)
        );
    }
}
