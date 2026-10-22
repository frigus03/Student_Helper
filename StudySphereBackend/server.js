console.log("Starting StudySphere backend with Adaptive AI...");

require("dotenv").config();

const express = require("express");
const app = express();

const PORT = Number(process.env.PORT) || 3000;
const MODEL = process.env.GEMINI_MODEL || "gemini-2.5-flash";

app.use(express.json({ limit: "2mb" }));

// Helper to call Gemini REST API securely
async function callGemini(prompt, systemInstruction = "", expectJson = false) {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    throw new Error("GEMINI_API_KEY is missing from .env.");
  }

  const payload = {
    contents: [
      {
        role: "user",
        parts: [{ text: prompt }]
      }
    ],
    generationConfig: {
      temperature: expectJson ? 0.3 : 0.6,
      maxOutputTokens: 2500
    }
  };

  if (systemInstruction) {
    payload.systemInstruction = {
      parts: [{ text: systemInstruction }]
    };
  }

  if (expectJson) {
    payload.generationConfig.responseMimeType = "application/json";
  }

  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey
      },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(60000)
    }
  );

  const data = await response.json();

  if (!response.ok) {
    console.error("Gemini HTTP Error:", response.status, data.error?.message);
    throw new Error(data.error?.message || "Gemini API request failed.");
  }

  const text = (data.candidates?.[0]?.content?.parts || [])
    .map(part => part.text || "")
    .join("")
    .trim();

  if (!text) {
    throw new Error("Gemini returned an empty response.");
  }

  return text;
}

// Clean JSON response if enclosed in code blocks
function parseCleanJson(rawText) {
  let cleaned = rawText.trim();
  if (cleaned.startsWith("```json")) {
    cleaned = cleaned.replace(/^```json/, "").replace(/```$/, "").trim();
  } else if (cleaned.startsWith("```")) {
    cleaned = cleaned.replace(/^```/, "").replace(/```$/, "").trim();
  }
  return JSON.parse(cleaned);
}

// Health Check
app.get("/", (req, res) => {
  res.json({
    app: "StudySphere AI Backend",
    status: "running",
    version: "2.0.0",
    features: ["chat", "adaptive_quiz", "study_planner", "material_ingest"]
  });
});

// 1. Interactive Tutor Chat
app.post("/api/chat", async (req, res) => {
  try {
    const message = req.body?.message?.trim();
    const context = req.body?.context?.trim() || "";

    if (!message) {
      return res.status(400).json({ error: "Please enter a message." });
    }

    const systemInstruction =
      "You are an empathetic, encouraging university AI Study Companion named StudySphere. " +
      "Explain concepts step-by-step, use concise bullet points, and offer practical exam tips. " +
      (context ? `\n\nStudy Material Context:\n${context}` : "");

    const answer = await callGemini(message, systemInstruction, false);
    res.json({ answer });
  } catch (error) {
    console.error("/api/chat error:", error.message);
    res.status(502).json({ error: error.message || "Chat request failed." });
  }
});

// 2. Adaptive Quiz Generation
// Dynamically adjusts question difficulty based on student's learner curve & mastery
app.post("/api/quiz/generate", async (req, res) => {
  try {
    const { subject, material, masteryLevel, count } = req.body;
    const numQuestions = Math.min(Math.max(Number(count) || 5, 3), 10);
    const mastery = typeof masteryLevel === "number" ? masteryLevel : 0.5;

    // Difficulty steering based on mastery curve:
    // If student mastery < 0.5 (weak): Start easy, focus on core definitions and foundational mechanics
    // If student mastery >= 0.7 (strong): Analytical, synthesis, edge cases
    let curveGuidance = "";
    if (mastery < 0.45) {
      curveGuidance =
        "The student is struggling with this subject (Accuracy: ~" +
        Math.round(mastery * 100) +
        "%). " +
        "Adapt to their learning curve: Start with fundamental, confidence-building questions (Bloom's level 1-2). " +
        "Keep the first 2 questions straightforward definitions/concepts, and make explanations very thorough, friendly, and step-by-step.";
    } else if (mastery >= 0.75) {
      curveGuidance =
        "The student has high mastery in this subject (Accuracy: ~" +
        Math.round(mastery * 100) +
        "%). " +
        "Provide advanced application, edge cases, and problem-solving questions to challenge them.";
    } else {
      curveGuidance =
        "The student has moderate proficiency. Provide a balanced mix of fundamental and intermediate conceptual questions.";
    }

    const prompt = `Generate an adaptive practice quiz for the subject "${subject || "General Study"}".
${curveGuidance}

Source Study Material Context:
"""
${(material || "Core concepts of " + (subject || "General Science and Mathematics")).substring(0, 12000)}
"""

You MUST return a JSON object with this exact schema:
{
  "category": "${subject || "Study Drill"}",
  "questions": [
    {
      "question": "Clear, direct question string",
      "options": ["Option A", "Option B", "Option C", "Option D"],
      "correctIndex": 0,
      "explanation": "Clear explanation of why this option is correct and others are not",
      "difficulty": "easy" | "medium" | "hard"
    }
  ]
}
Generate exactly ${numQuestions} questions. Do NOT wrap in markdown, return pure JSON.`;

    const systemInstruction =
      "You are a pedagogical expert who designs adaptive quizzes matching the student's exact knowledge level.";

    const rawJson = await callGemini(prompt, systemInstruction, true);
    const quizData = parseCleanJson(rawJson);

    if (!quizData.questions || !Array.isArray(quizData.questions)) {
      throw new Error("Invalid quiz format received from AI model.");
    }

    res.json({
      success: true,
      category: quizData.category || subject || "Practice Quiz",
      questions: quizData.questions
    });
  } catch (error) {
    console.error("/api/quiz/generate error:", error.message);
    res.status(502).json({
      success: false,
      error: error.message || "Failed to generate quiz."
    });
  }
});

// 3. AI Study Planner Generation
// Takes user's target exam date, available hours, and weak subjects to create a day-by-day plan
app.post("/api/planner/generate", async (req, res) => {
  try {
    const { targetExamDate, availableHoursPerDay, subjects, weakSubjects, materialOverview } = req.body;

    const targetDate = targetExamDate || "in 14 days";
    const hours = Number(availableHoursPerDay) || 2;
    const allSubjects = Array.isArray(subjects) ? subjects.join(", ") : (subjects || "General Subjects");
    const weak = Array.isArray(weakSubjects) ? weakSubjects.join(", ") : (weakSubjects || "None specified");

    const prompt = `Create a targeted, realistic study schedule leading up to the target date/exam: "${targetDate}".
The student has ${hours} hours available per day.
Enrolled Subjects: ${allSubjects}.
Identified WEAK subjects needing extra focus: ${weak}.

Study Schedule Rules:
1. Dedicate at least 60% of early study sessions to the weak subjects.
2. Break study sessions into focused 45-60 minute chunks with clear milestones.
3. Include active recall and practice quiz checkpoints.
4. Distribute dates starting from today until the exam date.

Return a JSON object with this exact structure:
{
  "summary": "Short 1-2 sentence motivating summary of this study plan",
  "tasks": [
    {
      "title": "Short actionable task title",
      "subject": "Subject Name",
      "date": "YYYY-MM-DD",
      "time": "HH:MM (e.g. 17:00)",
      "durationMinutes": 60,
      "priority": "HIGH" | "MEDIUM" | "LOW",
      "notes": "Specific subtopics and active recall goal"
    }
  ]
}
Generate between 5 to 10 prioritized tasks. Do NOT wrap in markdown, return pure JSON.`;

    const systemInstruction =
      "You are a master academic time-management planner who builds personalized study schedules.";

    const rawJson = await callGemini(prompt, systemInstruction, true);
    const planData = parseCleanJson(rawJson);

    res.json({
      success: true,
      summary: planData.summary || "Customized AI Study Plan",
      tasks: planData.tasks || []
    });
  } catch (error) {
    console.error("/api/planner/generate error:", error.message);
    res.status(502).json({
      success: false,
      error: error.message || "Failed to generate study plan."
    });
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`StudySphere backend running on port ${PORT}`);
});
