# Gemini Mobile Agent (Android Autonomous Phone Controller)

An autonomous Android Mobile Agent powered by the **Gemini API** and Android **Accessibility Service** (`AccessibilityService`). It allows users to give natural language instructions to their smartphone, which the AI executes autonomously by observing the screen, reasoning on next steps, and performing taps, gestures, typing, and app navigation.

---

## Architecture Overview

```
                                      ┌─────────────────────────────────┐
                                      │          User Input             │
                                      │  (App Dashboard / Floating UI)  │
                                      └────────────────┬────────────────┘
                                                       │
                                                       ▼
                                      ┌─────────────────────────────────┐
                                      │          AgentEngine            │
                                      │     (Autonomous Loop Controller)│
                                      └───────┬─────────────────▲───────┘
                                              │                 │
                      ┌───────────────────────┴──────┐          │ (Feedback)
                      ▼                              ▼          │
       ┌──────────────────────────────┐ ┌──────────────────────────────┐
       │   AgentAccessibilityService  │ │        GeminiClient          │
       │ - UI Hierarchy Inspection    │ │ - Gemini 2.0 / 1.5 Flash     │
       │ - Gesture & Tap Dispatcher   │ │ - Sub-second latency         │
       │ - Screenshot Capture API     │ │ - Structured JSON Decisions  │
       └──────────────┬───────────────┘ └──────────────────────────────┘
                      │
                      ▼
       ┌──────────────────────────────┐
       │      Target Android App      │
       │  (YouTube, WhatsApp, etc.)   │
       └──────────────────────────────┘
```

---

## Key Features

1. **Gemini API Powered Reasoning**:
   - Uses `gemini-2.0-flash` (or `gemini-1.5-flash` / `gemini-1.5-pro`) with strict JSON schema outputs.
   - Generates thought reasoning alongside concrete UI actions.

2. **Native Android Accessibility Service**:
   - Real-time DOM/UI hierarchy parsing via `AccessibilityNodeInfo`.
   - Sequential indexing of interactive screen elements (`[1]`, `[2]`, `[3]`).
   - Coordinate gesture dispatching (`dispatchGesture`) for taps and smooth scrolls.
   - Text input into search bars and text fields via `ACTION_SET_TEXT`.

3. **Floating Overlay Control Bar (`OverlayService`)**:
   - Draggable floating pill that sits on top of all third-party apps.
   - Shows live AI thought process and current step.
   - **Emergency Red STOP Button**: Instantly halts the agent loop at any moment.

4. **Safety & Guardrails**:
   - **Password Guard**: Automatic detection of password/PIN fields (`isPassword == true`) blocks AI text entry.
   - **Loop Detection**: Prevents runaway repetition if an action fails 4 times.
   - **Step Limit**: Configurable limit (default 15 steps) ensures the agent never runs indefinitely.

5. **API Key Security**:
   - User enters their own Gemini API key inside the app.
   - Saved locally using Android `EncryptedSharedPreferences`.
   - Real-time "Save & Validate" button checks key validity with Google AI Studio.

---

## App Modules & Code Structure

- `service/AgentAccessibilityService.kt`: Accessibility service inspecting windows and dispatching gestures.
- `service/OverlayService.kt` & `FloatingViewManager.kt`: System overlay window manager and floating UI.
- `gemini/GeminiClient.kt`: Fast OkHttp client for Gemini REST API.
- `gemini/GeminiModels.kt` & `AgentPrompts.kt`: System instructions and data schemas.
- `engine/ScreenParser.kt`: UI tree flattener and element compressor.
- `engine/ActionExecutor.kt`: Action dispatcher (clicks, scrolls, app launches, back, home).
- `engine/AgentEngine.kt`: The central autonomous control loop.
- `engine/SafetyGuard.kt`: Safety checks and loop breaker.
- `data/AppPreferences.kt`: Encrypted settings storage.
- `ui/screens/`: Jetpack Compose screens for Dashboard, Execution Logs, and Settings.

---

## Setup & Running

1. **Build APK**:
   ```bash
   ./gradlew assembleDebug
   ```
2. **Install on Android Device / Emulator**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
3. **Grant Permissions**:
   - Open **Gemini Mobile Agent**.
   - Tap **Enable** under "Accessibility Automation Service" -> Turn ON in Android Settings.
   - Tap **Grant** under "Draw Over Other Apps".
   - Go to **Settings** tab -> Enter your Gemini API key -> Tap **Save & Validate API Key**.
4. **Automate!**:
   - Enter a prompt like `"Open YouTube and play lofi beats"` and tap **Start Agent**.
   - Or enable the **Floating Assistant Bubble** to trigger automation from any screen.
