# Roubao Codebase Summary

**Project:** Roubao (肉包) - AI Android Automation Assistant
**Language:** Kotlin (36 files, ~12,450 LOC)
**Target:** Android 8.0+ (API 26-34)
**Build System:** Gradle 8.2, Kotlin 1.9.20
**Version:** 1.4.2 (hardened)

---

## Directory Structure

```
app/src/main/
├── java/com/roubao/autopilot/
│   ├── agent/           (7 files, ~2,500 LOC) - MobileAgent orchestration
│   ├── controller/      (2 files, ~1,100 LOC) - Device & app control
│   ├── data/            (2 files, ~700 LOC)   - Settings & history persistence
│   ├── skills/          (3 files, ~1,140 LOC) - Skill delegation system
│   ├── tools/           (8 files, ~1,800 LOC) - Task execution tools
│   ├── ui/              (5 screens, ~4,000 LOC) - Compose UI
│   ├── vlm/             (3 files, ~1,060 LOC) - VLM client adapters
│   ├── utils/           (1 file, ~245 LOC)   - Error handling
│   ├── service/         (1 file, ShellService) - Shizuku integration
│   ├── App.kt           - Application singleton
│   └── MainActivity.kt  - Activity entry point
├── aidl/
│   └── IShellService.aidl - Shizuku IPC interface
├── res/                 - Resources (strings, colors, themes, drawables)
├── assets/
│   └── skills.json      - Skill definitions & app delegations
└── AndroidManifest.xml
```

---

## Core Modules

### 1. Agent Layer (~2,500 LOC)
**Location:** `agent/`
**Responsibility:** Orchestrate VLM-driven automation workflows

| File | LOC | Purpose |
|------|-----|---------|
| `MobileAgent.kt` | 1,211 | Main automation loop; coordinates Manager→Executor→Reflector; 3 VLM modes |
| `Manager.kt` | 156 | Analyzes screenshots → generates action steps |
| `Executor.kt` | 185 | Converts action strings to tool calls |
| `ActionReflector.kt` | 128 | Validates action execution & handles failures |
| `Notetaker.kt` | 154 | Maintains conversation context for history |
| `ConversationMemory.kt` | 128 | Stores/retrieves execution steps & VLM responses |
| `InfoPool.kt` | 226 | Caches app info & device state across steps |

**Data Flow:**
```
Screenshot → Manager (analyze via VLM)
         ↓
       Actions → Executor (delegate to tools/skills)
         ↓
      Results → Reflector (validate & retry)
         ↓
      History → ConversationMemory (for context)
```

### 2. Controller Layer (~1,100 LOC)
**Location:** `controller/`
**Responsibility:** Device control via Shizuku; app discovery

| File | LOC | Purpose |
|------|-----|---------|
| `DeviceController.kt` | 655 | Shizuku UserService binding; shell commands; screenshots |
| `AppScanner.kt` | 435 | Scans installed apps; caches metadata |

**Key Methods:**
- `DeviceController.takeScreenshot()` → PNG at `/data/local/tmp/`
- `DeviceController.executeShellCmd()` → runs with Shizuku privileges
- `AppScanner.getInstalledApps()` → returns app list with icons/names

### 3. Skills Layer (~1,140 LOC)
**Location:** `skills/`
**Responsibility:** Task delegation & fallback execution

| File | LOC | Purpose |
|------|-----|---------|
| `Skill.kt` | 299 | Base class; execution modes (delegation/automation) |
| `SkillManager.kt` | 503 | Loads skills.json; matches user intent to skills |
| `SkillRegistry.kt` | 341 | Maintains skill registry; delegates to apps |

**Execution Modes:**
- **Delegation:** Pass task to specialized app (deeplink, beam protocol)
- **GUI Automation:** AI-driven navigation with step guidance
- **Tool-based:** Shell commands or HTTP requests

### 4. Tools Layer (~1,800 LOC)
**Location:** `tools/`
**Responsibility:** Low-level task execution

| File | LOC | Purpose |
|------|-----|---------|
| `ShellTool.kt` | 180 | Execute shell commands via Shizuku |
| `HttpTool.kt` | 160 | Make HTTP requests (GET/POST/JSON) |
| `ClipboardTool.kt` | 110 | Read/write clipboard |
| `OpenAppTool.kt` | 140 | Launch apps by package name |
| `SearchAppsTool.kt` | 105 | Find apps by name/keyword |
| `DeepLinkTool.kt` | 125 | Open deep links (beam://, http://, etc.) |
| `ToolManager.kt` | 185 | Registry & execution dispatcher |
| `Tool.kt` | 95 | Base interface |

**Security Mitigations (v1.4.2):**
- Shell injection prevention (argument escaping)
- SSRF prevention (URL validation)
- HTTP 429 handling (rate limiting)
- Response body limit enforcement

### 5. VLM Client Layer (~1,060 LOC)
**Location:** `vlm/`
**Responsibility:** Integrate multiple Vision-Language Models

| File | LOC | Purpose |
|------|-----|---------|
| `VLMClient.kt` | 368 | OpenAI-compatible API (GPT-4V, Claude, Qwen-VL via proxy) |
| `GUIOwlClient.kt` | 302 | DashScope API (GUI-Owl, session-based, Chinese) |
| `MAIUIClient.kt` | 391 | Local/self-hosted models (normalized coordinates) |

**Three Execution Modes:**
1. **OpenAI Mode:** Manager→Executor→Reflector pattern; GPT-4V/Qwen-VL/Claude
2. **GUI-Owl Mode:** DashScope sessions; returns action instructions directly
3. **MAI-UI Mode:** Local inference; 0-999 coordinate normalization

### 6. Data Layer (~700 LOC)
**Location:** `data/`
**Responsibility:** Persist settings & execution history

| File | LOC | Purpose |
|------|-----|---------|
| `SettingsManager.kt` | 369 | Encrypted SharedPreferences; API keys, VLM config |
| `ExecutionHistory.kt` | 331 | Room/local storage; tracks past executions |

**Encrypted Storage:** API keys stored via EncryptedSharedPreferences (AndroidX Security)

### 7. UI Layer (~4,000 LOC)
**Location:** `ui/screens/` + `OverlayService.kt`
**Responsibility:** Jetpack Compose UI; floating overlay

| Component | LOC | Purpose |
|-----------|-----|---------|
| `HomeScreen.kt` | 557 | Main chat interface; input & execution |
| `SettingsScreen.kt` | 1,872 | VLM config, API key management, preferences |
| `HistoryScreen.kt` | 595 | Execution history with filters/search |
| `CapabilitiesScreen.kt` | 452 | Skills showcase; available tools |
| `OnboardingScreen.kt` | 247 | First-run setup flow |
| `OverlayService.kt` | 507 | Floating UI service (persistent overlay) |
| `Theme.kt` | ~200 | Material3 colors & typography |

---

## Key Flows

### Execution Flow
```
User Input (HomeScreen)
    ↓
MobileAgent.runInstruction()
    ├─→ SkillManager.matchSkill() → Execute skill (fast path)
    │
    ├─→ Manager: Take screenshot + analyze via VLM
    │
    ├─→ Executor: Parse VLM response → tool calls
    │
    ├─→ ToolManager: Execute (Shell/HTTP/OpenApp/DeepLink/Clipboard)
    │
    └─→ Reflector: Validate results; retry/fail

Results → ConversationMemory + ExecutionHistory
        → UI update (HomeScreen progress)
```

### VLM Integration
```
Screenshot (Bitmap) + Previous actions
    ↓
VLMClient.call() [OpenAI-compatible]
    or
GUIOwlClient.call() [DashScope/GUI-Owl]
    or
MAIUIClient.call() [Local inference]
    ↓
Action string → Parse & normalize
    ↓
Executor.executeAction()
```

### Settings Management
```
User modifies VLM config (SettingsScreen)
    ↓
SettingsManager.saveSettings()
    ↓
EncryptedSharedPreferences
    ↓
MobileAgent reads on next execution
```

---

## Dependencies

### Core
- **Shizuku 13.1.5** - System privilege escalation via UserService
- **Jetpack Compose 2023.10.01** - Declarative UI
- **OkHttp 4.12.0** - HTTP client (VLM API calls)
- **Kotlinx Coroutines 1.7.3** - Async/concurrent execution

### Security & Persistence
- **AndroidX Security 1.1.0-alpha06** - EncryptedSharedPreferences
- **Firebase Crashlytics** - Error reporting (optional)

### Android Framew
- **AndroidX Core 1.12.0, Lifecycle 2.6.2, Activity Compose 1.8.1**
- **Android API 26-34 support**

---

## Recent Improvements (v1.4.2-hardened)

**29 security & stability fixes:**
- Shell injection mitigation via argument escaping
- SSRF prevention (URL origin validation)
- Bitmap memory recycling (prevent OOM)
- OkHttp response body lifecycle management
- HTTP 429 rate-limiting handling
- @Volatile annotations for thread safety
- Token budget enforcement (VLM requests)
- Touch coordinate clamping (screen bounds)
- Null safety improvements across all layers

---

## File Statistics

| Layer | Files | LOC | % of Total |
|-------|-------|-----|-----------|
| UI Screens | 5 | 3,727 | 30% |
| Agent | 7 | 2,474 | 20% |
| VLM Clients | 3 | 1,061 | 8.5% |
| Tools | 8 | 1,800 | 14% |
| Skills | 3 | 1,143 | 9% |
| Controller | 2 | 1,090 | 9% |
| Data | 2 | 700 | 5.6% |
| Utils & Core | 3 | 455 | 3.6% |
| **Total** | **36** | **~12,450** | **100%** |

---

## Design Patterns

1. **Singleton Pattern** - App, SkillManager, SettingsManager
2. **Strategy Pattern** - Multiple VLM client implementations
3. **Repository Pattern** - SettingsManager, ExecutionHistory
4. **Coroutines** - Async execution with structured concurrency
5. **Compose State Flow** - UI reactivity via StateFlow
6. **Builder Pattern** - Tool configuration & VLM request construction

---

## Security Model

- **Shizuku Integration** - Privileges granted via manager app (no root needed)
- **Encrypted Storage** - API keys via EncryptedSharedPreferences
- **Input Validation** - Shell commands escaped; URLs validated
- **SSRF Prevention** - HTTP requests validated for origin
- **Rate Limiting** - Respects VLM API limits (429 handling)
- **Memory Safety** - Bitmap recycling; bounded response sizes

---

## Notes for Developers

1. **Threading:** All I/O operations use `withContext(Dispatchers.IO)` in coroutines
2. **Screenshot Format:** PNG stored at `/data/local/tmp/autopilot_screen.png` (Shizuku writable)
3. **Skill Definition:** Loaded from assets/skills.json; supports delegation & GUI automation
4. **VLM Modes:** Selected in SettingsScreen; requires valid API keys
5. **Error Handling:** CrashHandler logs uncaught exceptions; Crashlytics integration optional
6. **Overlay Service:** Runs as foreground service; requires SYSTEM_ALERT_WINDOW permission

