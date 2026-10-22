console.log("Starting StudySphere backend with Adaptive AI & Local Ingestion...");

require("dotenv").config();

const express = require("express");
const path = require("path");
const fs = require("fs");
const multer = require("multer");
const pdfParse = require("pdf-parse");

const app = express();

const PORT = Number(process.env.PORT) || 3000;
const MODEL = process.env.GEMINI_MODEL || "gemini-2.5-flash";

const UPLOAD_DIR = path.join(__dirname, "uploads");
if (!fs.existsSync(UPLOAD_DIR)) {
  fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

// Serve uploaded static files
app.use("/uploads", express.static(UPLOAD_DIR));

// Configure Multer for local uploads
const diskStorage = multer.diskStorage({
  destination: function (req, file, cb) {
    cb(null, UPLOAD_DIR);
  },
  filename: function (req, file, cb) {
    const safeName = Date.now() + "_" + file.originalname.replace(/[^a-zA-Z0-9._-]/g, "_");
    cb(null, safeName);
  }
});
const upload = multer({
  storage: diskStorage,
  limits: { fileSize: 30 * 1024 * 1024 } // 30 MB max
});

app.use(express.json({ limit: "5mb" }));

// Helper to call Gemini REST API securely (supports single prompt or multi-turn contents array)
async function callGemini(contentsOrPrompt, systemInstruction = "", expectJson = false, isRetry = false) {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    throw new Error("GEMINI_API_KEY is missing from .env.");
  }

  let contents = [];
  if (Array.isArray(contentsOrPrompt)) {
    contents = contentsOrPrompt;
  } else {
    contents = [
      {
        role: "user",
        parts: [{ text: contentsOrPrompt }]
      }
    ];
  }

  const payload = {
    contents: contents,
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

  const candidate = data.candidates?.[0];
  let text = (candidate?.content?.parts || [])
    .map(part => part.text || "")
    .join("")
    .trim();

  // If filtered due to recitation/verbatim matching, retry once requesting original paraphrasing
  if (!text && candidate?.finishReason === "RECITATION" && !isRetry) {
    console.warn("Recitation filter triggered. Retrying with original phrasing directive...");
    const retryInstruction = (systemInstruction || "") + "\nNote: Explain strictly in your own original student-friendly words and analogies without reciting verbatim textbook passages.";
    return callGemini(contentsOrPrompt, retryInstruction, expectJson, true);
  }

  if (!text) {
    console.error("Gemini returned empty text. Full response was:", JSON.stringify(data));
    throw new Error(candidate?.finishMessage || "Gemini returned an empty response.");
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
    version: "2.1.0",
    features: ["chat", "adaptive_quiz", "study_planner", "local_file_upload"]
  });
});

// 1. 100% Free Local Material Upload & Text Ingestion (No Firebase Storage billing required!)
app.post("/api/upload", upload.single("file"), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: "No file provided" });
    }

    console.log(`Received file upload: ${req.file.originalname} (${req.file.size} bytes)`);

    const filePath = req.file.path;
    let extractedText = "";
    const ext = path.extname(req.file.originalname).toLowerCase();

    // Ingest text based on file format
    if (ext === ".pdf") {
      try {
        const fileBuffer = fs.readFileSync(filePath);
        const parsed = await pdfParse(fileBuffer);
        extractedText = (parsed.text || "").trim();
        console.log(`Extracted ${extractedText.length} characters of text from PDF: ${req.file.originalname}`);
      } catch (parseErr) {
        console.warn("Could not extract PDF text:", parseErr.message);
      }
    } else if (ext === ".txt" || ext === ".md") {
      try {
        extractedText = fs.readFileSync(filePath, "utf-8").trim();
      } catch (textErr) {
        console.warn("Could not read text file:", textErr.message);
      }
    }

    let aiSummary = "";
    if (extractedText && extractedText.length > 50) {
      try {
        console.log(`Generating AI concise summary for ${req.file.originalname}...`);
        const summaryPrompt = `Create a high-yield, concise revision summary of the following study material in 4 to 6 clear bullet points with bold key terms:\n\n"""${extractedText.substring(0, 15000)}"""`;
        aiSummary = await callGemini(summaryPrompt, "You are an expert university academic summarizer creating concise study notes for exam revision.", false);
      } catch (sumErr) {
        console.warn("Could not generate summary:", sumErr.message);
      }
    }

    // Accessible via Android emulator through 10.0.2.2
    const fileUrl = `http://10.0.2.2:${PORT}/uploads/${req.file.filename}`;

    res.json({
      success: true,
      fileName: req.file.originalname,
      storedFileName: req.file.filename,
      fileUrl: fileUrl,
      extractedText: extractedText,
      aiSummary: aiSummary
    });
  } catch (error) {
    console.error("/api/upload error:", error.message);
    res.status(500).json({ error: error.message || "File upload processing failed." });
  }
});

// 2. Interactive Tutor Chat with Academic Guardrails & Multi-turn Conversational Memory
app.post("/api/chat", async (req, res) => {
  try {
    const message = req.body?.message?.trim();
    const context = req.body?.context?.trim() || "";
    const subject = req.body?.subject?.trim() || "Course Material";
    const history = Array.isArray(req.body?.history) ? req.body.history : [];

    if (!message) {
      return res.status(400).json({ error: "Please enter a message." });
    }

    let systemInstruction = "";
    if (context) {
      systemInstruction =
        `You are a strict academic tutor specializing in the course subject "${subject}" based on the following uploaded study material:\n` +
        `"""\n${context.substring(0, 15000)}\n"""\n\n` +
        `STRICT GUARDRAIL RULES:\n` +
        `1. Answer the student's questions strictly using and related to this subject and study material.\n` +
        `2. Remember previous questions and answers in this conversation. If the student asks follow-up questions (such as "explain step 2 in more detail", "give an example of that", or "why is that?"), seamlessly answer with reference to the earlier explanation and the study material without asking them to repeat themselves.\n` +
        `3. If the student asks anything off-topic, unrelated, sidetracked, or non-academic (e.g. general chit-chat, pop culture, sports, unrelated hobbies, personal advice, or unrelated subjects), YOU MUST POLITELY REFUSE AND RE-FOCUS:\n` +
        `   "I'm your dedicated AI study companion for ${subject}. Let's stay focused on your coursework! What question do you have about this material?"\n` +
        `4. Keep your answers clear, academic, encouraging, and structured with step-by-step explanations or bullet points.\n` +
        `5. Always explain in your own original words, intuitive analogies, and step-by-step reasoning.`;
    } else {
      systemInstruction =
        `You are an AI study companion named StudySphere. You strictly assist students with academic learning, courses, and exam prep. Remember previous questions in this conversation for seamless follow-ups. Politely decline any non-academic or frivolous queries. Always explain in your own original words, intuitive analogies, and structured explanations.`;
    }

    // Build multi-turn contents from previous history
    const contents = [];
    if (history.length > 0) {
      for (const item of history) {
        const text = (item.text || "").trim();
        if (!text) continue;
        const role = (item.role === "ai" || item.role === "model") ? "model" : "user";

        // Gemini contents must begin with user role
        if (contents.length === 0 && role !== "user") {
          continue;
        }

        // Gemini contents cannot have consecutive identical roles
        if (contents.length > 0 && contents[contents.length - 1].role === role) {
          contents[contents.length - 1].parts[0].text += "\n" + text;
        } else {
          contents.push({
            role: role,
            parts: [{ text: text }]
          });
        }
      }
    }

    // Append current user message
    if (contents.length > 0 && contents[contents.length - 1].role === "user") {
      contents[contents.length - 1].parts[0].text += "\n" + message;
    } else {
      contents.push({
        role: "user",
        parts: [{ text: message }]
      });
    }

    const answer = await callGemini(contents, systemInstruction, false);
    res.json({ answer });
  } catch (error) {
    console.error("/api/chat error:", error.message);
    res.status(502).json({ error: error.message || "Chat request failed." });
  }
});

// 2b. Generate Concise Revision Summary from Note Content or Material
app.post("/api/summarize", async (req, res) => {
  try {
    let text = req.body?.text?.trim() || "";
    const subject = req.body?.subject?.trim() || "Course Material";
    const title = req.body?.title?.trim() || "Study Note";
    const fileUrl = req.body?.fileUrl?.trim() || "";

    // If text is empty but file is on server, extract text
    if ((!text || text.length < 15) && fileUrl && fileUrl.includes("/uploads/")) {
      try {
        const storedName = path.basename(fileUrl);
        const diskPath = path.join(UPLOAD_DIR, storedName);
        if (fs.existsSync(diskPath)) {
          const ext = path.extname(diskPath).toLowerCase();
          if (ext === ".pdf") {
            const buf = fs.readFileSync(diskPath);
            const parsed = await pdfParse(buf);
            text = (parsed.text || "").trim();
            console.log(`Extracted ${text.length} chars from stored file for summary: ${storedName}`);
          }
        }
      } catch (e) {
        console.warn("Could not extract from stored file:", e.message);
      }
    }

    if (!text || text.length < 15) {
      text = `Key principles, definitions, formulas, calculations, and core exam revision concepts of ${title} in the subject "${subject}".`;
    }

    const summaryPrompt = `Create a high-yield, concise revision summary of the following study material on "${subject}" (Topic: ${title}) in 4 to 6 clear bullet points with bold key terms:\n\n"""${text.substring(0, 15000)}"""`;
    const aiSummary = await callGemini(
      summaryPrompt,
      "You are an expert university academic summarizer creating concise, high-yield study notes for exam revision.",
      false
    );

    res.json({ success: true, aiSummary, extractedText: text });
  } catch (error) {
    console.error("/api/summarize error:", error.message);
    res.status(500).json({ error: error.message || "Summarization failed." });
  }
});

// 3. Adaptive Quiz Generation with Concept Tagging & YouTube Recommendations
app.post("/api/quiz/generate", async (req, res) => {
  try {
    const { subject, material, title, masteryLevel, weakConcepts, count } = req.body;
    const numQuestions = Math.min(Math.max(Number(count) || 5, 3), 10);
    const mastery = typeof masteryLevel === "number" ? masteryLevel : 0.5;

    let weakPointsDirective = "";
    if (Array.isArray(weakConcepts) && weakConcepts.length > 0) {
      weakPointsDirective =
        `\n🎯 ADAPTIVE LEARNING FOCUS (CRITICAL):\n` +
        `In previous quizzes, the student struggled with these specific concepts:\n` +
        weakConcepts.map(c => `- ${c}`).join("\n") + "\n" +
        `Prioritize generating questions that specifically test, explain, and reinforce these weak concepts to close their learning gaps.\n`;
    }

    let curveGuidance = "";
    if (mastery < 0.45) {
      curveGuidance =
        "The student is struggling with this material (Accuracy: ~" +
        Math.round(mastery * 100) +
        "%). " +
        "Adapt to their learning curve: Start with fundamental, confidence-building questions (Bloom's level 1-2). " +
        "Make explanations very thorough, friendly, and step-by-step.";
    } else if (mastery >= 0.75) {
      curveGuidance =
        "The student has high mastery in this topic (Accuracy: ~" +
        Math.round(mastery * 100) +
        "%). " +
        "Provide advanced application, analytical reasoning, and edge-case questions.";
    } else {
      curveGuidance =
        "The student has moderate proficiency. Provide a balanced mix of fundamental and intermediate conceptual questions.";
    }

    const prompt = `Generate an adaptive practice quiz for "${title || subject || "Course Material"}".
${curveGuidance}
${weakPointsDirective}

Source Study Material Context:
"""
${(material || "Core concepts of " + (title || subject || "Course Material")).substring(0, 15000)}
"""

You MUST return a JSON object with this exact schema:
{
  "category": "${title || subject || "Practice Quiz"}",
  "questions": [
    {
      "question": "Clear, direct question string",
      "options": ["Option A", "Option B", "Option C", "Option D"],
      "correctIndex": 0,
      "explanation": "Objective explanation of why the correct option is right. Do NOT include phrases like 'You got it!', 'Great job!', or 'Correct!'. Start directly explaining the reasoning.",
      "difficulty": "easy",
      "concept": "Specific concept name (e.g. Newton's Second Law F=ma, or Light-Dependent Reactions)",
      "youtubeSearchQuery": "YouTube search phrase to help student watch a tutorial on this concept (e.g. Newton's Second Law physics tutorial Khan Academy)",
      "videoHint": "Short encouraging tip on what video to watch for this concept (e.g. Watch a 5-minute video on calculating F = ma on an incline)"
    }
  ]
}
Generate exactly ${numQuestions} questions. Do NOT wrap in markdown, return pure JSON.`;

    const systemInstruction =
      "You are a pedagogical expert who designs adaptive quizzes matching the student's exact knowledge level and recommends targeted study videos for concepts they find challenging.";

    const rawJson = await callGemini(prompt, systemInstruction, true);
    const quizData = parseCleanJson(rawJson);

    if (!quizData.questions || !Array.isArray(quizData.questions)) {
      throw new Error("Invalid quiz format received from AI model.");
    }

    res.json({
      success: true,
      category: quizData.category || title || subject || "Practice Quiz",
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

// 4. AI Study Planner Generation (Exam Timetable + Weak Concepts + Stamina + Portion Check)
app.post("/api/planner/generate", async (req, res) => {
  try {
    const {
      examTimetable,
      targetExamDate,
      availableHoursPerDay,
      sessionDurationMinutes,
      allPortionUploaded,
      uploadedNotes,
      weakConcepts,
      subjectMastery,
      subjects,
      weakSubjects
    } = req.body;

    const todayStr = new Date().toISOString().split("T")[0];
    const hours = Number(availableHoursPerDay) || 2;
    const stamina = Number(sessionDurationMinutes) || 45;
    const timetableText = examTimetable || targetExamDate || "Exams starting in 14 days";

    // Format uploaded notes summary
    let notesSummary = "None uploaded yet";
    if (Array.isArray(uploadedNotes) && uploadedNotes.length > 0) {
      notesSummary = uploadedNotes.map(n => typeof n === "string" ? n : `${n.subject || "Subject"}: ${n.title || "Note"}`).join("; ");
    }

    // Format weak concepts from quizzes
    let weakList = "None recorded";
    if (Array.isArray(weakConcepts) && weakConcepts.length > 0) {
      weakList = weakConcepts.join(", ");
    } else if (weakSubjects) {
      weakList = Array.isArray(weakSubjects) ? weakSubjects.join(", ") : weakSubjects;
    }

    // Format mastery scores
    let masteryText = "Not yet taken";
    if (subjectMastery && typeof subjectMastery === "object") {
      masteryText = Object.entries(subjectMastery)
        .map(([subj, score]) => `${subj}: ${(Number(score) * 100).toFixed(0)}% accuracy`)
        .join(", ");
    }

    const prompt = `You are a world-class academic strategist and adaptive study planner.
Generate a high-impact, realistic calendar study plan leading up to the student's exams.

TODAY'S DATE: ${todayStr}

STUDENT INPUTS:
1. Exam Timetable:
${timetableText}

2. Study Stamina & Availability:
- Max continuous sitting duration: ${stamina} minutes per session. (Every session MUST fit the student's stamina of ${stamina} minutes, or a multiple with short breaks).
- Daily study capacity: ${hours} hours per day.

3. Notes & Syllabus Portion Status:
- All portion uploaded in notes?: ${allPortionUploaded === false ? "NO - Some portions are missing (Schedule extra self-study, textbook reading, and note synthesis sessions for unuploaded portions)." : "YES - All portions uploaded in notes."}
- Uploaded Notes in App: ${notesSummary}

4. Quiz Results & Diagnostic Weak Points:
- Identified Weak Concepts from Quizzes: ${weakList}
- Subject Mastery Scores: ${masteryText}

ADAPTIVE SCHEDULING RULES:
1. EXAM TIMETABLE PACING: Parse all subjects and exam dates. Work backwards from the earliest exam to the latest.
2. DYNAMIC TIME ALLOCATION:
   - For subjects with low quiz mastery (< 60%) or identified WEAK CONCEPTS, dynamically allocate more frequent study sessions and assign HIGH priority.
   - For strong subjects, schedule high-yield spaced review sessions.
3. TITLE, WHEN TO DO, AND WHAT TO DO:
   - Each session must have:
     * "title": Actionable and motivating (e.g. "🎯 Deep Dive: Cell Signaling Cascades", "📖 Core Revision: Thermodynamics", "⚡ Active Recall & Practice Quiz")
     * "subject": Subject name
     * "date": "YYYY-MM-DD" (strictly on or after ${todayStr} up to the exam date)
     * "time": "HH:MM" (realistic time of day e.g. "09:00", "14:00", "17:30", "19:00")
     * "durationMinutes": ${stamina}
     * "priority": "HIGH" (for weak concepts or imminent exams) or "MEDIUM" / "LOW"
     * "category": "Weak Concept Mastery" | "Note Revision" | "Practice Quiz" | "Active Recall"
     * "notes": Detailed "WHAT TO DO" instructions (step-by-step concepts to review, diagrams to draw, or formulas to practice).

OUTPUT FORMAT:
Return a JSON object with this exact structure:
{
  "summary": "Short 2-sentence motivating overview of the timetable strategy, highlighting focus on weak concepts.",
  "tasks": [
    {
      "title": "...",
      "subject": "...",
      "date": "YYYY-MM-DD",
      "time": "HH:MM",
      "durationMinutes": ${stamina},
      "priority": "HIGH",
      "category": "Weak Concept Mastery",
      "notes": "What to do: ..."
    }
  ]
}
Generate between 6 to 12 prioritized calendar tasks. Return ONLY valid JSON, do not wrap in markdown or backticks.`;

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

// 5. Parse Exam Timetable from Uploaded PDF or Image
app.post("/api/planner/parse-timetable", upload.single("file"), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ success: false, error: "No file provided" });
    }

    console.log(`Parsing exam timetable file: ${req.file.originalname} (${req.file.size} bytes)`);

    const filePath = req.file.path;
    const ext = path.extname(req.file.originalname).toLowerCase();
    let timetableText = "";

    if (ext === ".pdf") {
      try {
        const fileBuffer = fs.readFileSync(filePath);
        const parsed = await pdfParse(fileBuffer);
        const rawPdfText = (parsed.text || "").trim();

        if (rawPdfText.length > 10) {
          const prompt = `Extract all exam subjects, dates, and times from this exam timetable document.
Format the output as a clean, concise list with each exam on a new line in this format:
Subject Name: YYYY-MM-DD at HH:MM (or month/day if year is missing)

Document Text:
"""
${rawPdfText.substring(0, 15000)}
"""

Return only the list of exams, nothing else.`;
          timetableText = await callGemini(prompt, "You are an accurate academic document scanner that extracts exam schedules.", false);
        } else {
          timetableText = rawPdfText;
        }
      } catch (pdfErr) {
        console.warn("PDF timetable parsing error:", pdfErr.message);
      }
    } else if ([".png", ".jpg", ".jpeg", ".webp"].includes(ext)) {
      try {
        const fileBuffer = fs.readFileSync(filePath);
        const base64Data = fileBuffer.toString("base64");
        const mimeType = req.file.mimetype || (ext === ".png" ? "image/png" : "image/jpeg");

        const contents = [
          {
            role: "user",
            parts: [
              {
                inlineData: {
                  mimeType: mimeType,
                  data: base64Data
                }
              },
              {
                text: "Read this exam timetable image carefully. Extract all exam subjects, dates, and times. Format the output with each exam on a new line as: 'Subject Name: Date at Time' (e.g. 'Biology: Nov 10 at 09:00'). Only return the list of exams."
              }
            ]
          }
        ];

        timetableText = await callGemini(contents, "You are an expert OCR timetable scanner who extracts exam schedules from images accurately.", false);
      } catch (imgErr) {
        console.warn("Image timetable parsing error:", imgErr.message);
      }
    } else {
      timetableText = fs.readFileSync(filePath, "utf-8").trim();
    }

    if (!timetableText || !timetableText.trim()) {
      return res.status(422).json({
        success: false,
        error: "Could not read any timetable details from the uploaded file. Please make sure the image or PDF is clear."
      });
    }

    res.json({
      success: true,
      fileName: req.file.originalname,
      timetableText: timetableText.trim()
    });
  } catch (error) {
    console.error("/api/planner/parse-timetable error:", error.message);
    res.status(500).json({
      success: false,
      error: error.message || "Failed to parse timetable file."
    });
  }
});

app.listen(PORT, "0.0.0.0", () => {
  console.log(`StudySphere backend running on port ${PORT}`);
});
