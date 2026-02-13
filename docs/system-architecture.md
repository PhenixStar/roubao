# Roubao System Architecture

---

## Architecture Overview

Roubao implements a **7-layer VLM-driven Android automation architecture**, running natively on Android without PC dependency.

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 1: UI (Jetpack Compose)                                  │
│  HomeScreen | SettingsScreen | HistoryScreen | CapabilitiesScreen
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Layer 2: Skills (Task Delegation)                              │
│  SkillManager | SkillRegistry | Skill Base Class                │
│  [Delegation: deeplink/beam] [Automation: VLM-guided steps]     │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Layer 3: Tools (Task Execution)                                │
│  ShellTool | HttpTool | OpenAppTool | DeepLinkTool              │
│  ClipboardTool | SearchAppsTool | ToolManager                   │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Layer 4: Agent (Orchestration)                                 │
│  MobileAgent | Manager | Executor | Reflector | ConversationMemory
│  InfoPool | Notetaker                                           │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Layer 5: VLM Clients (Model Integration)                       │
│  VLMClient (OpenAI-compatible) | GUIOwlClient (DashScope)       │
│  MAIUIClient (Local)                                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Layer 6: Controller (Device Interface)                         │
│  DeviceController (Shizuku UserService) | AppScanner            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Layer 7: System Services                                       │
│  Shizuku | Android Framework | Shell | Package Manager          │
└─────────────────────────────────────────────────────────────────┘
```

---

## Execution Flow Diagram

```
User Input
  │
  ├─→ "Order food"
  │
  ↓
┌──────────────────────────────────┐
│  SkillManager.matchSkill()       │
│  (Intent matching)               │
└──────────────────────────────────┘
  │
  ├─→ Match found: "order_food" skill
  │
  ↓ (Fast Path - Delegation)
  │
  ├─→ Check related_apps in skills.json
  │   [小美 (Meituan AI) - priority 100]
  │
  ├─→ DeepLinkTool.openDeepLink("beam://...")
  │   [Direct delegation to specialized AI app]
  │
  └─→ ✓ Task delegated (fastest, most reliable)

No match OR delegation fails
  │
  ↓ (Fallback - GUI Automation)
  │
  ├─→ DeviceController.takeScreenshot()
  │
  ├─→ Manager: Analyze via VLM
  │   Input: [Screenshot + instruction + context]
  │   Output: "Click search box, type 'rice bowl', press enter"
  │
  ├─→ Executor: Parse actions
  │   ├─ Click(x=100, y=200)
  │   ├─ Type("rice bowl")
  │   └─ Press("enter")
  │
  ├─→ ToolManager: Execute actions
  │   ├─→ ShellTool.tap(100, 200)
  │   ├─→ ShellTool.inputText("rice bowl")
  │   └─→ ShellTool.pressKey("enter")
  │
  ├─→ Reflector: Validate execution
  │   ├─ Take screenshot
  │   ├─ Ask VLM: "Did it work?"
  │   └─ If no: retry or escalate
  │
  └─→ ConversationMemory: Store step + result

Repeat until:
  - Task complete
  - Max steps exceeded (25 default)
  - User cancels
```

---

## VLM Client Architecture

### Three Modes of Operation

#### 1. OpenAI Compatible Mode (Default)
```
Request Payload:
├─ messages: [{role, content: [{type: image}, {type: text}]}]
├─ model: "gpt-4-vision" | "qwen-vl" | "claude-3.5-sonnet"
└─ max_tokens, temperature, etc.

Response Flow:
Screenshot + history → VLMClient.analyzeScreenshot()
                   ↓
            API call (OkHttp)
                   ↓
            Action string (structured)
                   ↓
            Manager parses → normalized actions

Clients:
- OpenAI API (GPT-4V)
- Azure OpenAI
- Local proxy (for Qwen-VL, Claude)
```

#### 2. GUI-Owl Mode (DashScope)
```
Request:
├─ screenshots: [base64]
├─ function_calls: [task descriptions]
├─ session_id: (persistent across steps)
└─ use_ocr: true

Response:
- Direct action commands (faster)
- Session-based context (no need to resend full history)
- Chinese-optimized prompts

Use Case:
- Real-time interactions
- Rapid decision-making
- Chinese UI automation
```

#### 3. MAI-UI Mode (Local)
```
Request:
├─ screenshot: numpy array
├─ instructions: text
└─ coordinate_system: 0-999 normalized

Response:
- Coordinate predictions (0-999 range)
- Action type (tap, swipe, scroll)
- Normalized to device screen

Use Case:
- Privacy-first (no cloud API)
- Offline operation
- Low latency
```

---

## Data Flow: Settings & Persistence

```
┌─────────────────────────────────┐
│  SettingsScreen (UI)            │
│  - VLM selection               │
│  - API key input               │
│  - Execution settings          │
└─────────────────────────────────┘
           ↓ (user saves)
┌─────────────────────────────────┐
│  SettingsManager                │
│  (singleton, thread-safe)       │
└─────────────────────────────────┘
           ↓ (encrypt & persist)
┌─────────────────────────────────┐
│  EncryptedSharedPreferences     │
│  (AndroidX Security crypto)     │
└─────────────────────────────────┘
           ↓ (device storage)
┌─────────────────────────────────┐
│  Android Keystore               │
│  (hardware-backed if available) │
└─────────────────────────────────┘

On execution:
SettingsManager.getApiKey() → decrypt from Keystore → use in VLMClient
```

---

## Skill Resolution Pipeline

```
User: "点外卖"

SkillManager.matchSkill()
    ↓
Load skills.json: [order_food, find_food, ...]
    ↓
For each skill:
  - Check keywords match: ["点外卖", "叫外卖", ...]
  - Calculate similarity score (VLM-based or keyword matching)
    ↓
Best match: "order_food" (confidence: 95%)
    ↓
Extract params: food="not specified"
    ↓
Check execution mode:
  a) related_apps with delegation priority
     ├─ 小美 (beam://...) - priority 100 ✓
     │  (DeepLinkTool.openDeepLink)
     │
     └─ 饿了么 (GUI automation) - priority 60
        (MobileAgent with VLM + tools)

  b) If no delegation: use GUI automation step guide
     steps: ["打开饿了么", "搜索食物", "选择商家", "下单"]
        ↓
     MobileAgent executes guided steps
```

---

## Screenshot Processing Pipeline

```
MobileAgent.runInstruction()
    ↓
DeviceController.takeScreenshot()
    ├─ Shell: "screencap -p /data/local/tmp/autopilot_screen.png"
    │  (executed via Shizuku UserService)
    │
    ├─ Read PNG from /data/local/tmp
    │  (accessible because Shizuku runs as shell user)
    │
    └─ Decode Bitmap
        (recycle after use to prevent OOM)
    ↓
Manager.analyzeScreenshot()
    ├─ Compress bitmap (if >2MB)
    ├─ Encode to base64
    ├─ Add to VLM message
    │  {role: "user", content: [{type: "image_url", url: "data:image/png;base64,..."}]}
    │
    └─ Include instruction + history
    ↓
VLMClient.call()
    └─ Response: action string
    ↓
Executor.parseAction()
    ├─ Tokenize instruction
    ├─ Extract tool calls & coordinates
    └─ Normalize to device bounds
    ↓
ToolManager.execute()
    └─ tap(x, y) | type(text) | pressKey(key) | swipe(...) | scroll(...)
    ↓
Reflector.validate()
    ├─ Take screenshot (again)
    ├─ Ask VLM: "Did it succeed?"
    ├─ If failure: retry or escalate
    └─ Store in ConversationMemory
```

---

## Component Interactions

### MobileAgent ↔ Manager
```
MobileAgent:
  screenshot (Bitmap)
     ↓
  Manager.analyzeScreenshot(screenshot, previousActions, instruction)
     ↓
  Returns: "Click button at (100, 50), then swipe down"
```

### Manager ↔ VLMClient
```
Manager:
  payload = {
    "messages": [{
      "role": "user",
      "content": [
        {"type": "image_url", "url": "data:image/png;base64,..."},
        {"type": "text", "text": "Click on the search button"}
      ]
    }, ...]
  }
     ↓
  VLMClient.call(payload)
     ↓
  Returns response with action description
```

### Executor ↔ ToolManager
```
Executor receives: "Click button at (100, 50)"
     ↓
  Parse coordinates → (100, 50)
  Clamp to screen bounds → ensure valid
     ↓
  ToolManager.execute(Tool.TAP, params: {x: 100, y: 50})
     ↓
  ShellTool.tap(100, 50)
     ↓
  Executes: input keyevent --longpress 26 2>/dev/null
           (or input tap x y depending on device)
```

### SkillManager ↔ DeepLinkTool
```
SkillManager.matchSkill("点外卖")
     ↓
  Found: "order_food" skill
     ↓
  Check related_apps[0]: {
    "name": "小美",
    "type": "delegation",
    "deep_link": "beam://www.meituan.com/home"
  }
     ↓
  DeepLinkTool.openDeepLink("beam://www.meituan.com/home")
     ↓
  Intent.ACTION_VIEW + Uri.parse(link)
     ↓
  System routes to Meituan AI app
```

---

## Concurrency Model

```
MobileAgent.runInstruction() [Coroutine scope]
    │
    ├─→ withContext(Dispatchers.Default)
    │   └─ SkillManager.matchSkill() (CPU-bound)
    │
    ├─→ withContext(Dispatchers.IO)
    │   ├─ DeviceController.takeScreenshot()
    │   ├─ VLMClient.call() (network)
    │   └─ ToolManager.execute()
    │
    └─ UI updates via StateFlow
       ├─ _state.emit(AgentState(step=i, status=RUNNING))
       └─ UI observes: state.collect { ... }

Thread Safety:
- @Volatile annotations on mutable fields (shellService, serviceBound)
- Synchronized blocks for shared resources
- MainHandler for UI thread callbacks
```

---

## Error Handling & Resilience

```
Step Execution:
    Try:
      Tool.execute()
         ↓
    On Exception:
      ├─ Log error
      ├─ Ask VLM: "What went wrong?"
      ├─ Retry (up to retryLimit)
      ├─ If still fails:
      │  └─ Move to next step OR escalate
      │
      └─ Store in ExecutionHistory
           (for user review & debugging)

VLM API Errors:
    HTTP 429 (Rate Limited)
      └─ Exponential backoff (1s, 2s, 4s, 8s)

    HTTP 500+ (Server Error)
      └─ Retry with backoff; fallback to local mode if available

    Timeout (>60s)
      └─ Cancel request; escalate to user

Memory Errors:
    OOM during screenshot
      └─ Recycle bitmaps; reduce compression
         Escalate if persistent
```

---

## Security Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  API Key Storage                                            │
│  User enters key → SettingsManager → EncryptedSharedPrefs  │
│                                   → Android Keystore        │
│  (Hardware-backed if device supports)                       │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Shell Command Execution (via Shizuku)                      │
│  ShellTool.executeCmd("cmd")                                │
│    ├─ Escape arguments (prevent injection)                  │
│    ├─ Validate command (whitelist safe operations)          │
│    └─ Execute via DeviceController.ShellService             │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  HTTP Requests (SSRF Prevention)                            │
│  HttpTool.request(url)                                      │
│    ├─ Validate URL origin (no private IPs unless whitelisted)
│    ├─ Enforce size limits (response body)                   │
│    └─ Respect rate limits (429 handling)                    │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Screenshot Security                                        │
│  DeviceController.takeScreenshot()                          │
│    ├─ Stores in /data/local/tmp (not accessible to other apps)
│    ├─ Deletes after processing                             │
│    └─ Sensitive data (API keys) not visible to VLM         │
└─────────────────────────────────────────────────────────────┘

Shizuku Privilege Model:
  Regular app ← requires user grant → Shizuku manager
                                   ← grants temporary access
  ShellService (runs as shell user) ← no root needed
                                   ← safer than full privilege escalation
```

---

## Performance Optimization

| Optimization | Technique |
|--------------|-----------|
| **Screenshot Speed** | Cached app info (AppScanner.getInstalledApps) |
| **VLM Latency** | Skill delegation (avoid VLM for known tasks) |
| **Memory** | Bitmap recycling after analysis |
| **API Usage** | Token budget enforcement; action aggregation |
| **Concurrency** | Coroutines with appropriate dispatchers |
| **Caching** | InfoPool (app metadata, screen state) |

---

## Testing Strategy

```
Unit Tests:
  └─ Executor.parseAction() → verify parsing logic
  └─ ToolManager.buildCommand() → shell escaping
  └─ SettingsManager.encrypt/decrypt() → data integrity

Integration Tests:
  └─ MobileAgent.runInstruction() [mock VLM]
  └─ DeviceController.executeShellCmd() [mock Shizuku]

UI Tests:
  └─ HomeScreen state changes
  └─ SettingsScreen form validation

End-to-End Tests:
  └─ Full execution flow (with real VLM API)
  └─ Skill delegation + fallback
```

---

## Deployment & Distribution

```
Build:
  gradle assembleRelease
    ├─ ProGuard enabled (reduce APK size)
    ├─ Compose compiler extension (v1.5.5)
    └─ Firebase Crashlytics integration

APK:
  ~20-30 MB (Compose + dependencies)

Signing:
  release.keystore (v1 signature scheme)

Distribution:
  GitHub Releases
  F-Droid (community)
  Google Play (future)
```

