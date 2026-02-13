# Roubao Quick Reference Guide

**Last Updated:** February 13, 2025
**Project:** Roubao (肉包) - AI Android Automation Assistant

---

## What is Roubao?

Open-source AI phone automation assistant for Android. Uses Vision-Language Models (VLMs) to understand screenshots and automate complex smartphone tasks. No PC required, runs natively on Android via Shizuku.

**Status:** v1.4.2 (stable, hardened)
**Language:** Kotlin
**License:** MIT

---

## Key Facts

| Aspect | Details |
|--------|---------|
| **Min Android** | API 26 (Android 8.0) |
| **Target Android** | API 34 (Android 14) |
| **Code Size** | 36 files, ~12,450 LOC |
| **APK Size** | ~20-25 MB |
| **Crash Rate** | < 0.5% |
| **Memory Peak** | ~125 MB |
| **VLM Models** | GPT-4V, Qwen-VL, Claude (cloud) + local models (offline) |

---

## Core Architecture

```
User Input → Skill Match → Delegation (fast)
              ↓
              No match → Take Screenshot
              ↓
              Manager (VLM analysis)
              ↓
              Executor (tool calls)
              ↓
              Reflector (validation)
              ↓
              Success ✓ or Retry
```

---

## Module Breakdown

| Module | LOC | Purpose |
|--------|-----|---------|
| **Agent** | 2,474 | Orchestrate VLM + tools |
| **Tools** | 1,800 | Shell, HTTP, OpenApp, DeepLink, Clipboard |
| **Skills** | 1,143 | Task delegation (20+ predefined) |
| **UI** | 3,727 | 5 Compose screens |
| **VLM** | 1,061 | 3 client adapters (OpenAI, GUI-Owl, MAI-UI) |
| **Controller** | 1,090 | Device control via Shizuku |
| **Data** | 700 | Settings & history persistence |

---

## Key Files

### Agent Layer
- `MobileAgent.kt` (1,211 LOC) - Main orchestration
- `Manager.kt` - Screenshot analysis
- `Executor.kt` - Action execution
- `Reflector.kt` - Validation & retry
- `ConversationMemory.kt` - History

### Controller Layer
- `DeviceController.kt` (655 LOC) - Shizuku integration, screenshots
- `AppScanner.kt` (435 LOC) - App discovery

### Tools Layer
- `ShellTool.kt` - Shell command execution
- `HttpTool.kt` - HTTP requests
- `OpenAppTool.kt` - Launch apps
- `DeepLinkTool.kt` - Deep linking
- `ClipboardTool.kt` - Clipboard ops

### Skills Layer
- `SkillManager.kt` (503 LOC) - Skill matching
- `SkillRegistry.kt` (341 LOC) - Skill registry
- `skills.json` (asset) - Skill definitions

### UI Layer
- `HomeScreen.kt` (557 LOC) - Chat interface
- `SettingsScreen.kt` (1,872 LOC) - Configuration
- `HistoryScreen.kt` (595 LOC) - Past executions
- `CapabilitiesScreen.kt` (452 LOC) - Skills showcase
- `OnboardingScreen.kt` (247 LOC) - Setup flow

### VLM Layer
- `VLMClient.kt` (368 LOC) - OpenAI-compatible
- `GUIOwlClient.kt` (302 LOC) - DashScope (Alibaba)
- `MAIUIClient.kt` (391 LOC) - Local models

---

## Three VLM Execution Modes

### 1. OpenAI Compatible (Default)
- Models: GPT-4V, Claude, Qwen-VL (via proxy)
- Pattern: Manager → Executor → Reflector
- Latency: ~30 seconds
- Cloud-based, requires API key

### 2. GUI-Owl (DashScope)
- Vendor: Alibaba DashScope
- Optimized for: Chinese UI automation
- Latency: ~25 seconds
- Session-based (persistent context)

### 3. MAI-UI (Local)
- Type: Local inference
- Models: LLaVA, Phi-Vision (small)
- Latency: ~15 seconds
- Offline, no API key needed
- Privacy-first, lower accuracy

---

## Execution Loop (Pseudo-code)

```kotlin
suspend fun runInstruction(instruction: String) {
    for (step in 1..maxSteps) {
        // 1. Take screenshot
        val screenshot = deviceController.takeScreenshot()
        
        // 2. Try skill delegation first (fast path)
        val skill = skillManager.matchSkill(instruction)
        if (skill.confidence > 0.8) {
            executeSkill(skill)  // ← Fast delegation
            return
        }
        
        // 3. GUI automation fallback (VLM-guided)
        val action = manager.analyzeScreenshot(screenshot, instruction)
        executor.execute(action)
        
        // 4. Validate execution
        val success = reflector.validate(screenshot)
        if (success) return
        if (retryCount >= maxRetries) escalate()
    }
}
```

---

## Security Highlights

### Shell Injection Prevention
```kotlin
// ✓ Safe: Escapes arguments
val escaped = "'${cmd.replace("'", "'\\''")}'".trimIndent()
Runtime.getRuntime().exec(arrayOf("sh", "-c", escaped))
```

### SSRF Prevention
```kotlin
// ✓ Safe: Validates URL origin
if (uri.host == "localhost" || uri.host.matches(privateIP)) {
    reject()  // Block private network access
}
```

### API Key Encryption
```kotlin
// ✓ Safe: Uses EncryptedSharedPreferences
EncryptedSharedPreferences.create(context, ...)
    .putString("api_key", key)  // Auto-encrypted
```

---

## Common Tasks

### Add a New VLM Client
1. Create `MyVLMClient.kt` in `vlm/` folder
2. Implement VLMClient interface
3. Add to SettingsScreen dropdown
4. Update MobileAgent initialization

### Add a New Skill
1. Edit `app/src/main/assets/skills.json`
2. Add skill definition (id, name, keywords, params)
3. Link related_apps (delegation or GUI automation)
4. Test with SkillManager.matchSkill()

### Add a New Tool
1. Create `MyTool.kt` in `tools/` folder
2. Extend Tool base class
3. Implement execute() method
4. Register in ToolManager

### Debug VLM Response
1. Enable logging in VLMClient.kt
2. Check ConversationMemory for action string
3. Verify Executor can parse the action
4. Use CrashHandler to capture errors

---

## Performance Tips

### Optimize Screenshot
- Compress to JPEG (quality 75%)
- Target size < 2 MB
- Bitmap must be recycled after use

### Reduce VLM Latency
- Use skill delegation (< 1 second)
- Batch tool calls
- Reuse VLM context

### Reduce Memory
- Recycle bitmaps immediately
- Limit screenshot history (< 5)
- Use InfoPool for caching

---

## Testing Checklist

- [ ] Screenshot taken successfully
- [ ] VLM API called with correct format
- [ ] Actions parsed from response
- [ ] Tools execute without errors
- [ ] Reflector validates results
- [ ] ConversationMemory stores history
- [ ] No crashes in exception cases
- [ ] Memory stable after 10+ steps

---

## Troubleshooting

### "Shizuku not available"
- Install Shizuku Manager app
- Grant permissions to Roubao
- Restart Roubao

### "VLM timeout"
- Check API key validity
- Check network connection
- Try different VLM model
- Reduce screenshot size

### "Action not recognized"
- Check VLM response format
- Update action parsing logic
- Try different VLM model
- Add debug logging

### "OOM (Out of Memory)"
- Check bitmap recycling
- Reduce screenshot frequency
- Clear conversation history
- Reduce max steps

---

## Documentation Files

| File | Purpose | Lines |
|------|---------|-------|
| `codebase-summary.md` | Code reference | 284 |
| `system-architecture.md` | System design | 509 |
| `code-standards.md` | Coding guidelines | 624 |
| `project-overview-pdr.md` | Requirements | 370 |
| `project-roadmap.md` | Timeline & features | 507 |
| `README-DOCUMENTATION.md` | Navigation guide | 269 |

**Total:** 2,563 lines of documentation

---

## Useful Commands

```bash
# Build
./gradlew assembleRelease

# Test
./gradlew testDebug

# Lint
./gradlew ktlint

# Generate codebase summary
repomix --output ./repomix-output.xml

# View logs
adb logcat | grep "[肉包]"
```

---

## Links

- **GitHub:** https://github.com/Turbo1123/roubao
- **Shizuku:** https://shizuku.rikka.app/
- **Kotlin:** https://kotlinlang.org/
- **Compose:** https://developer.android.com/jetpack/compose

---

## Version Info

| Version | Release | Status |
|---------|---------|--------|
| v1.4.2 | Feb 2025 | ✓ Current (hardened) |
| v1.5 | Jun 2025 | Planned (local models + workflows) |
| v2.0 | Dec 2025 | Planned (distribution + marketplace) |

---

## Need Help?

1. Check [README-DOCUMENTATION.md](./README-DOCUMENTATION.md)
2. Search specific docs (Ctrl+F)
3. Open GitHub issue
4. Check discussions on GitHub

