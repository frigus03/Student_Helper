# StudySphere AI (Student Helper) 🎓
### *An Intelligent, Secure, AI-Powered Academic Assistant for Android*

---

## 📌 Executive Summary
**StudySphere AI** (internally configured as `Student Helper`) is a modern Android application engineered to assist students with holistic academic productivity. It unifies **Adaptive AI Tutoring**, **Intelligent Study Planning**, **Document Summarization**, **In-App Native PDF Viewing**, **Automated Timetable Parsing**, **Proctored Adaptive Quizzing**, and **System Calendar Synchronization** into a single cohesive, privacy-centric mobile platform.

The system utilizes a hybrid cloud architecture combining **Firebase (Auth & Firestore)** for real-time synchronization, user identity, and offline-persistent data storage, alongside a dedicated **Node.js/Express micro-backend** that orchestrates **Google Gemini Generative AI (gemini-2.5-flash)** capabilities.

---

## 🏛️ System Architecture & Methodologies

### 1. Architectural Pattern: Client-Server + Clean Separation of Concerns
* **Presentation Layer (Android Client - Java & Material Design 3):** Handles UI rendering, user interaction, lifecycle events, permissions, and native OS integrations (such as the Google Calendar Provider and Android PDF Renderer).
* **Security & Persistence Layer (Firebase Auth & Cloud Firestore):** Manages user session state, real-time snapshot listeners for instant updates, and encrypted document persistence with strict user-level Firestore Security Rules.
* **AI Orchestration Middleware (Node.js & Express):** Serves as an intermediary bridge between Android and the Google Gemini API. This prevents API key exposure on mobile devices, manages PDF file parsing (`pdf-parse`), applies prompt engineering, and parses JSON output schemas.
* **Foundation LLM (Google Gemini):** Powers contextual Q&A, markdown formatting, LaTeX math conversion, structured quiz generation, smart schedule creation, and document summarization.

```
┌─────────────────────────────────────────────────────────────┐
│                   Android Client App (Java)                 │
│  - Activities & Adapters        - Material 3 Design         │
│  - Offline Persistence Cache    - Android Native PdfRenderer│
│  - AES-256-GCM / SHA-256 Crypto - FLAG_SECURE Anti-Cheat    │
└──────────────┬───────────────────────────────┬──────────────┘
               │                               │
       Real-Time Sync / Auth           REST API (JSON / Multipart)
               ▼                               ▼
┌──────────────────────────────┐ ┌─────────────────────────────┐
│       Google Firebase        │ │  StudySphere Node.js Server │
│ - Firebase Authentication    │ │ - Express Router (Port 3000)│
│ - Cloud Firestore (Rules)    │ │ - Multer File Upload Engine │
│ - Persistent Offline Cache   │ │ - pdf-parse Ingestion Engine│
└──────────────────────────────┘ └──────────────┬──────────────┘
                                                │
                                       Google Generative AI
                                                ▼
                                 ┌─────────────────────────────┐
                                 │ Google Gemini (gemini-2.5)  │
                                 └─────────────────────────────┘
```

---

### 2. Methodologies & Engineering Techniques

#### A. Security & Zero-Knowledge Architecture
* **AES-256-GCM Encryption:** Sensitive student metadata (e.g. email) stored in Firestore is encrypted using 256-bit Advanced Encryption Standard in Galois/Counter Mode (GCM), providing authenticated encryption with tamper detection.
* **Salted SHA-256 Hashing:** Used to create deterministic zero-knowledge lookup keys (`emailHash`) with a cryptographic salt, preventing rainbow table attacks.
* **Firestore Security Rules:** Zero-trust security enforcement (`request.auth.uid == userId`) ensures students can only read and write to their own private document paths.
* **API Key Protection:** The Google Gemini API key resides solely on the backend server (`.env`), eliminating reverse-engineering risks from APK decompilation.

#### B. Anti-Cheating & Proctored Quiz Technology
* **Hardware Screen Protection (`FLAG_SECURE`):** During quiz execution, `WindowManager.LayoutParams.FLAG_SECURE` is applied to block screenshots, screen recording, and display mirroring.
* **Clipboard Trapping & Guardrail Injection:** If text selection or copying occurs, the app intercepts the clipboard and injects an academic integrity prompt directive designed to force external AI models (ChatGPT, Claude, Gemini) into refusing to provide answers.

#### C. Asynchronous Execution & Non-Blocking UI
* **ExecutorService Multithreading:** All intensive operations (network HTTP requests, file uploads, crypto algorithms, and PDF page rasterization) run on background executor threads, keeping the Main UI thread free of frame drops (60/120 FPS target) and preventing Android ANR (Application Not Responding) dialogs.
* **Real-Time Snapshot Listeners:** Firestore `addSnapshotListener` maintains live synchronization across devices without manual pull-to-refresh.

#### D. Native Document Rendering
* Uses the native `android.graphics.pdf.PdfRenderer` API and local caching, removing the need for heavy 3rd-party PDF reader libraries.

---

## 🔄 App Process Flow & Lifecycle

```
[App Launch] ──▶ SplashActivity
                       │
             Is User Logged In?
             ├──▶ NO  ──▶ LoginActivity ──▶ RegisterActivity / ForgotPasswordActivity
             └──▶ YES ──▶ MainActivity (Home Dashboard)
                               │
       ┌───────────────────────┼───────────────────────┐
       ▼                       ▼                       ▼
  [Notes Module]       [AI Study Buddy]       [Planner Module]
  - Upload notes/PDF   - Note-aware chat      - AI Wizard Generation
  - AI Summarization   - Multi-turn sessions  - Timetable auto-parse
  - In-app PDF view    - Markdown/Math view   - Date strip & filters
  - Start Quiz button  - Session history      - Sync to Google Calendar
                               │
                               ▼
                        [Quiz Module]
                        - AI MCQ generation
                        - Proctored FLAG_SECURE
                        - Weak concept breakdown
                        - YouTube tutorial recommendations
```

1. **Launch & Routing:** `SplashActivity` initializes the Splash Screen API, checks `FirebaseAuth.getCurrentUser()`, and routes to `MainActivity` (if authenticated) or `LoginActivity`.
2. **Dashboard Overview:** `MainActivity` displays real-time statistics (pending study tasks, total planned study hours, dynamic time-of-day greetings), and quick navigation to all core modules.
3. **Notes & Document Ingestion:** The student creates notes manually or attaches PDFs/TXT documents. The backend parses documents, runs AI summarization, and saves structured summaries back to Firestore.
4. **Interactive AI Study Buddy:** Students engage in multi-turn conversations with context awareness (notes, subject details). The assistant responds with formatted Markdown and LaTeX math formulas.
5. **Study Planner & Timetable Automation:** Students can add tasks manually or trigger the AI Planner Wizard (specifying subject, target days, and daily hours) or upload a timetable to auto-generate organized study tasks, which can be synced directly to Google Calendar.
6. **Adaptive Quiz & Learning Loop:** Quizzes are generated directly from student notes. If a student gets answers wrong, the system identifies weak concepts and automatically generates targeted YouTube study links.

---

## 📂 Complete File Catalog & Contents

### 📱 Android Application Code (`app/src/main/java/com/example/studenthelper/`)

| File Name | Primary Role & Description |
| :--- | :--- |
| **`StudentHelperApp.java`** | Custom `Application` class. Configures global Firestore persistent cache settings (`PersistentCacheSettings`) for offline data access and loads the saved theme (Dark/Light). |
| **`SplashActivity.java`** | Cold-start entry point. Uses AndroidX SplashScreen API, verifies Firebase authentication state, and routes to `MainActivity` or `LoginActivity`. |
| **`LoginActivity.java`** | User login interface. Handles email/password authentication via `FirebaseAuth`, input validation, progress indicators, and routing to Register or Forgot Password. |
| **`RegisterActivity.java`** | User sign-up flow. Creates Firebase credentials, encrypts user metadata with `CryptoUtils` (AES-256-GCM + Salted SHA-256), and stores user profile in Firestore. |
| **`ForgotPasswordActivity.java`** | Initiates password recovery emails via `FirebaseAuth.sendPasswordResetEmail()` with validation. |
| **`MainActivity.java`** | Core dashboard. Listens to real-time study task updates via Firestore snapshots, tallies completed vs pending hours, formats dynamic greetings, and controls bottom navigation. |
| **`NotesActivity.java`** | Notes manager. Supports text creation, file upload (PDF/TXT), live search filtering, AI note summarization via backend REST API, and Firestore sync. |
| **`NotesAdapter.java`** | `RecyclerView.Adapter` for notes. Handles note card rendering, date formatting, and click callbacks for viewing, editing, deleting, or launching a quiz. |
| **`Note.java`** | Firestore data model representing notes (title, subject, content, timestamps, file URLs, and AI summaries). |
| **`PdfViewerActivity.java`** | In-app document reader. Downloads remote PDFs to cache or opens local files, rendering high-resolution bitmaps page-by-page using Android's native `PdfRenderer`. |
| **`AssistantActivity.java`** | Interactive AI Study Buddy. Features slide-out drawer for chat sessions, note context attachment, multi-turn history, and API communication with `/api/chat`. |
| **`ChatSession.java`** | Model class for chat sessions (session ID, title, timestamp, associated note ID, subject). |
| **`ChatMessage.java`** | Model class for individual chat messages (`sender: "user" | "ai"`, message text, timestamp). |
| **`ChatSessionsAdapter.java`** | `RecyclerView.Adapter` displaying past chat history sessions in the navigation drawer with delete actions. |
| **`MarkdownTextFormatter.java`** | Custom parser converting Markdown (bold, headings, bullet points) and LaTeX math formulas (`\frac`, `\text`, `$$...$$`) into formatted Android `Spanned` text via HTML. |
| **`PlannerActivity.java`** | Comprehensive study task organizer. Includes interactive calendar date strip, status/subject filters, AI Study Planner Wizard, timetable file parser, and task status toggle. |
| **`PlannerAdapter.java`** | Heterogeneous `RecyclerView.Adapter` that displays both date headers (`HeaderItem`) and task cards (`TaskItem`). |
| **`PlannerListItem.java`** | Base interface for planner items to enable multi-type recycler view rows. |
| **`HeaderItem.java`** | Model for section headers in the task list (e.g., "Today", "Tomorrow", specific dates). |
| **`TaskItem.java`** | Model wrapping a `StudyTask` for display in `PlannerAdapter`. |
| **`StudyTask.java`** | Firestore data model representing study tasks (title, subject, category, due date, duration, priority, completed flag, notes). |
| **`CalendarHelper.java`** | Integration utility using Android `CalendarContract.Events.CONTENT_URI` to export scheduled tasks directly into the student's Google Calendar app. |
| **`QuizActivity.java`** | Interactive test environment. Implements `FLAG_SECURE` screen recording protection, clipboard trapping, dynamic MCQ rendering, score calculation, weak area analysis, and YouTube video recommendations. |
| **`ProfileActivity.java`** | User account management. Displays decrypted user details, toggles Dark/Light theme, triggers password resets, and performs cascading account deletion. |
| **`CryptoUtils.java`** | Cryptographic utility class providing AES-256-GCM authenticated encryption/decryption and salted SHA-256 hashing. |

---

### 🖥️ Backend Service Code (`StudySphereBackend/`)

| File Name | Purpose & Functionality |
| :--- | :--- |
| **`server.js`** | Express application handling multipart file uploads (`multer`), PDF text extraction (`pdf-parse`), and Google Gemini REST API calls for chat, summarization, quiz generation, and timetable parsing. |
| **`package.json`** | Node.js project manifest defining dependencies (`express`, `multer`, `pdf-parse`, `dotenv`). |
| **`.env`** | Environment configuration storing the server port and `GEMINI_API_KEY`. |

---

### ⚙️ Configuration & Security Rules

| File Name | Description |
| :--- | :--- |
| **`app/src/main/AndroidManifest.xml`** | Defines permissions (`INTERNET`), registers all 10 app activities, declares FileProvider for secure URI sharing, and sets the app theme. |
| **`app/build.gradle.kts`** | Configures Android build parameters (`compileSdk 36`, `minSdk 29`), view binding, and dependencies (Firebase BoM 33.7.0, Material 1.12.0). |
| **`app/firestore.rules`** | Server-side security rules enforcing strict user isolation on `/users/{userId}` and all subcollections. |

---

## 🚀 Setup & Execution Guide

### 1. Backend Server Setup
```bash
cd StudySphereBackend
npm install
# Copy .env.example to .env and configure your keys:
# PORT=3000
# GEMINI_API_KEY=your_gemini_api_key_here
# GEMINI_MODEL=gemini-2.5-flash
# APP_SHARED_SECRET=StudySphere_Secure_App_Key_2026
npm start
```

### 2. Android Studio Setup
1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/) and enable **Authentication** (Email/Password) and **Cloud Firestore**.
2. Download your `google-services.json` from Firebase and place it into the `app/` folder (`app/google-services.json`).
3. Open the project in **Android Studio Ladybug / Koala or newer**.
4. In `app/src/main/java/com/example/studenthelper/AppConfig.java`, verify the backend URL (`http://10.0.2.2:3000` for Android emulator, or your deployed cloud backend URL).
5. Sync Project with Gradle Files and run on an Android Emulator (API 29+) or physical device.

---

## 🎯 Viva Voce Ready: Expected Questions & Answers

### Q1: What is the overall architecture of StudySphere AI?
> **Answer:** StudySphere AI uses a Client-Server architecture with a clean separation of concerns. The front end is a native Android application written in Java following Material Design 3 guidelines. Real-time database and authentication needs are handled directly via Firebase (Auth & Firestore). Intensive AI and document parsing tasks are delegated to a lightweight Node.js/Express backend that securely interfaces with Google's Gemini LLM.

### Q2: Why did you build a Node.js middleware instead of calling Gemini directly from the Android app?
> **Answer:** Calling the Gemini API directly from the mobile app would expose the private API key inside the compiled APK, creating a significant security vulnerability. The Node.js backend protects the API key, handles file uploads and raw PDF parsing server-side, and enforces prompt templates and JSON output formatting before returning responses to the mobile app.

### Q3: How do you handle security and user privacy?
> **Answer:** We employ a multi-layered security strategy:
> 1. **Client-Side Encryption (`CryptoUtils`):** User emails are encrypted using AES-256-GCM before being stored, and zero-knowledge indexing is achieved using salted SHA-256 hashes.
> 2. **Firestore Security Rules:** Server-side rules verify that `request.auth.uid == userId`, ensuring students cannot access other users' data.
> 3. **Proctoring Protection:** In `QuizActivity`, `FLAG_SECURE` blocks screen recording and screenshots, while custom clipboard handling prevents copying questions into external AI tools.

### Q4: How is offline support handled in the app?
> **Answer:** In `StudentHelperApp`, Firestore offline persistence is configured via `PersistentCacheSettings`. This enables Firestore to cache documents locally on the device, allowing students to access previously loaded notes and study tasks even without an internet connection. Changes sync automatically when the connection is restored.

### Q5: How does the AI Study Planner Wizard work?
> **Answer:** In `PlannerActivity`, students enter subjects, duration, target completion dates, or upload their syllabus/timetable. This payload is transmitted to the backend's `/api/planner/generate` endpoint. Gemini evaluates the constraints, balances the workload, and returns a structured JSON array of study tasks. The Android app converts these into `StudyTask` objects and saves them directly into Firestore.

### Q6: How does the quiz system help students improve?
> **Answer:** When a quiz concludes in `QuizActivity`, the app analyzes questions answered incorrectly, isolates the specific concept tags returned by Gemini, and displays a "Weak Concepts Breakdown." It then builds deep-link search queries to open curated YouTube educational videos for each concept, completing an active revision loop.
