# Roubao Code Standards & Guidelines

---

## Kotlin Coding Standards

### Naming Conventions

#### Classes & Interfaces
- Use **PascalCase** for class/interface names
- Suffix with purpose: `Manager`, `Client`, `Tool`, `Screen`
- Examples:
  ```kotlin
  class MobileAgent { }           // ✓ Clear purpose
  class VLMClient { }             // ✓ Descriptive
  interface Skill { }             // ✓ Behavior-focused

  class Agent { }                 // ✗ Too generic
  class MyUtil { }                // ✗ Unclear purpose
  ```

#### Functions & Variables
- Use **camelCase**
- Verb-first for functions; adjective-first for booleans
- Examples:
  ```kotlin
  fun analyzeScreenshot() { }           // ✓ Clear action
  var isExecuting = false               // ✓ Boolean prefix
  fun executeAction(action: String) { } // ✓ Parameter clear

  fun analyse() { }                     // ✗ Non-standard spelling
  fun data() { }                        // ✗ Too vague
  ```

#### Constants
- Use **UPPER_SNAKE_CASE** in companion objects
- Examples:
  ```kotlin
  companion object {
      const val SCREENSHOT_PATH = "/data/local/tmp/autopilot_screen.png"
      const val MAX_STEPS = 25
      const val TIMEOUT_MS = 60000L
  }
  ```

#### File Names
- Match primary class name: `MobileAgent.kt`, `VLMClient.kt`
- If multiple classes: use descriptive kebab-case: `vlm-client-factory.kt`
- Tests: append `.test.kt` or `.spec.kt`

---

## Code Organization

### File Structure
```kotlin
package com.roubao.autopilot.agent

import android.content.Context
import kotlinx.coroutines.withContext
// [grouped by source: android, androidx, kotlin, external, local]

/**
 * Main class documentation (what it does, why it exists)
 *
 * Architecture role: Part of MobileAgent orchestration
 * Dependencies: Manager, Executor, Reflector
 * Responsibilities: Coordinate VLM analysis → action execution
 */
class MobileAgent(
    private val vlmClient: VLMClient?,
    private val controller: DeviceController,
    context: Context
) {
    // [1. Companion object & constants]
    companion object {
        const val MAX_STEPS = 25
        const val TIMEOUT_MS = 60000L
    }

    // [2. State & configuration]
    @Volatile
    private var isRunning = false
    private val _state = MutableStateFlow(AgentState())

    // [3. Lazy-initialized dependencies]
    private val skillManager: SkillManager? by lazy { }

    // [4. Lifecycle methods]
    fun initialize() { }

    // [5. Main public methods]
    suspend fun runInstruction(instruction: String) { }

    // [6. Helper methods]
    private fun parseAction(action: String) { }

    // [7. Nested classes & data classes]
    data class AgentState(val step: Int = 0)
}
```

### Grouping Imports
```kotlin
// 1. Android Framework
import android.content.Context
import android.graphics.Bitmap

// 2. AndroidX Libraries
import androidx.compose.material3.Button
import androidx.lifecycle.ViewModel

// 3. Kotlin stdlib & coroutines
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

// 4. External libraries
import com.squareup.okhttp3.OkHttpClient

// 5. Local packages
import com.roubao.autopilot.controller.DeviceController
import com.roubao.autopilot.vlm.VLMClient
```

---

## Documentation Standards

### KDoc Comments (Class-Level)
```kotlin
/**
 * Vision-Language Model client adapter for OpenAI-compatible APIs.
 *
 * Supports:
 * - GPT-4V (OpenAI)
 * - Qwen-VL (Alibaba DashScope via proxy)
 * - Claude 3.5 Sonnet (Anthropic via proxy)
 *
 * Handles:
 * - Screenshot encoding (base64 JPEG)
 * - Message history
 * - Rate limiting (HTTP 429)
 * - Timeout management (default 60s)
 *
 * Thread-safe: Yes (OkHttpClient is thread-safe)
 * Example:
 * ```
 * val client = VLMClient(apiKey, model = "gpt-4-vision")
 * val response = client.analyzeScreenshot(bitmap, "Click search button")
 * ```
 */
class VLMClient(private val apiKey: String) { }
```

### Function Documentation
```kotlin
/**
 * Analyzes a screenshot via VLM to generate next action.
 *
 * @param screenshot Bitmap to analyze (will be compressed if >2MB)
 * @param instruction User instruction for this step
 * @param history Previous actions and VLM responses
 * @return Action description string (e.g., "Click button at (100, 50)")
 * @throws IOException If network request fails
 * @throws TimeoutException If request exceeds 60s
 */
suspend fun analyzeScreenshot(
    screenshot: Bitmap,
    instruction: String,
    history: List<String> = emptyList()
): String = withContext(Dispatchers.IO) {
    // implementation
}
```

### Inline Comments (When Necessary)
```kotlin
// Clamp coordinates to screen bounds (prevent IndexOutOfBoundsException)
val clampedX = x.coerceIn(0, screenWidth)
val clampedY = y.coerceIn(0, screenHeight)

// Escape shell arguments to prevent injection attacks
// e.g., "$(rm -rf /)" becomes "'$(rm -rf /)'"
val escaped = "'${cmd.replace("'", "'\\''")}'"
```

---

## Coroutine & Threading

### Dispatcher Usage
```kotlin
// I/O-heavy operations (network, file, database)
withContext(Dispatchers.IO) {
    vlmClient.call(...)        // Network request
    deviceController.screenshot()
    settingsManager.load()
}

// CPU-heavy operations (parsing, compression)
withContext(Dispatchers.Default) {
    skillManager.matchSkill(instruction)
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
}

// UI updates (StateFlow, LiveData)
_state.emit(AgentState(step = i))
```

### Coroutine Cancellation
```kotlin
suspend fun runInstruction(instruction: String) {
    try {
        for (step in 0 until maxSteps) {
            ensureActive()  // Throw CancellationException if cancelled

            val action = analyzer.analyze(...)
            // ... execute action
        }
    } catch (e: CancellationException) {
        logger.info("Execution cancelled by user")
        throw e  // Always re-throw CancellationException
    }
}
```

---

## Error Handling & Logging

### Exception Strategy
```kotlin
// ✓ Specific exceptions, logged at appropriate level
try {
    shellService.executeCommand(cmd)
} catch (e: IOException) {
    logger.warn("Shell command failed: ${e.message}")
    // Handle recovery
} catch (e: SecurityException) {
    logger.error("Shizuku permission denied", e)
    // Escalate to user
}

// ✗ Bare catch-all
try {
    // ...
} catch (e: Exception) {
    // Too generic
}
```

### Logging Levels
```kotlin
// DEBUG: Detailed state transitions, verbose API responses
logger.debug("[Manager] Analyzing screenshot (${bitmap.width}x${bitmap.height})")

// INFO: Major milestones, skill matches, execution progress
logger.info("[肉包] SkillManager loaded ${skills.size} skills")

// WARN: Recoverable errors, retries, degraded performance
logger.warn("VLM API rate limited, retrying in 2s")

// ERROR: Unrecoverable errors, user-facing failures
logger.error("Failed to connect to Shizuku service", exception)
```

### CrashHandler Integration
```kotlin
// Uncaught exceptions logged to Crashlytics
Thread.setDefaultUncaughtExceptionHandler(CrashHandler())

// Also log Coroutine errors
CoroutineExceptionHandler { _, exception ->
    logger.error("Coroutine error: ${exception.message}", exception)
}
```

---

## Memory & Performance

### Bitmap Management
```kotlin
// ✓ Always recycle bitmaps after use
private fun analyzeAndRecycle(bitmap: Bitmap) {
    try {
        val analysis = visionModel.analyze(bitmap)
        return analysis
    } finally {
        bitmap.recycle()  // ALWAYS in finally block
    }
}

// ✓ Compress large bitmaps
fun compressScreenshot(bitmap: Bitmap): ByteArray {
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
    return outputStream.toByteArray()
}

// ✗ Leak bitmap memory
val bitmap = BitmapFactory.decodeFile(path)
val analysis = visionModel.analyze(bitmap)
// bitmap never recycled!
```

### Resource Management
```kotlin
// ✓ Use try-with-resources equivalent in Kotlin
File(filePath).inputStream().use { input ->
    // stream auto-closed after use
}

// ✓ Bound response sizes
val response = okHttpClient.newCall(request).execute()
val body = response.body?.byteStream()?.use { input ->
    // Limit read to 10MB to prevent OOM
    input.readNBytes(10 * 1024 * 1024)
}
```

---

## Security Practices

### Shell Command Escaping
```kotlin
// ✓ Escape arguments to prevent injection
fun executeCmd(cmd: String): String {
    val escaped = "'${cmd.replace("'", "'\\''")}'".trimIndent()
    val fullCmd = arrayOf("sh", "-c", escaped)
    // ...
}

// Command: input text "hello world"
// Becomes: sh -c 'input text '\''hello world'\'

// ✗ String interpolation (UNSAFE)
Runtime.getRuntime().exec("sh -c input text $userInput")
```

### URL Validation (SSRF Prevention)
```kotlin
// ✓ Validate HTTP URLs
fun isSafeUrl(url: String): Boolean {
    val uri = try { URI(url) } catch (e: Exception) { return false }
    return when {
        uri.scheme != "http" && uri.scheme != "https" -> false
        uri.host == "localhost" || uri.host == "127.0.0.1" -> false
        uri.host?.endsWith(".internal") == true -> false
        uri.host?.matches(Regex("^192\\.168\\..*")) == true -> false
        else -> true
    }
}
```

### API Key Storage
```kotlin
// ✓ Use EncryptedSharedPreferences
val encryptedSharedPreferences = EncryptedSharedPreferences.create(
    context,
    "secret_shared_prefs",
    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

// Store: encrypts both key and value
encryptedSharedPreferences.edit().putString("api_key", key).apply()

// Retrieve: auto-decrypts
val apiKey = encryptedSharedPreferences.getString("api_key", null)
```

---

## Testing Conventions

### Unit Test Structure
```kotlin
class VLMClientTest {
    private lateinit var vlmClient: VLMClient
    private val mockOkHttp: MockOkHttpClient = MockOkHttpClient()

    @Before
    fun setup() {
        vlmClient = VLMClient(
            apiKey = "test-key",
            httpClient = mockOkHttp
        )
    }

    @Test
    fun `analyzeScreenshot returns action string on success`() {
        // Given
        val testBitmap = createTestBitmap(100, 100)
        mockOkHttp.enqueueResponse(MockResponse().setBody("""{"action":"click"}"""))

        // When
        val result = runBlocking { vlmClient.analyzeScreenshot(testBitmap, "test") }

        // Then
        assertEquals("click", result)
        testBitmap.recycle()
    }

    @Test
    fun `analyzeScreenshot retries on HTTP 429`() {
        // Given
        mockOkHttp.enqueueResponse(MockResponse().setResponseCode(429))
        mockOkHttp.enqueueResponse(MockResponse().setBody("""{"action":"success"}"""))

        // When
        val result = runBlocking { vlmClient.analyzeScreenshot(testBitmap, "test") }

        // Then
        assertEquals("success", result)
        assertEquals(2, mockOkHttp.getRequestCount())
    }
}
```

### Test Naming
```kotlin
// ✓ Descriptive test names (BDD style)
fun `executeShellCmd returns output on success`() { }
fun `executeShellCmd throws IOException when Shizuku unavailable`() { }
fun `takeScreenshot compresses large bitmaps`() { }

// ✗ Generic names
fun testExecute() { }
fun test1() { }
```

---

## Compose UI Standards

### Screen Composition
```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeScreenViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Content
    }
}

// Preview for IDE support
@Preview
@Composable
private fun HomeScreenPreview() {
    HomeScreen()
}
```

### State Management
```kotlin
// Use ViewModel + StateFlow for state
class HomeScreenViewModel : ViewModel() {
    private val _state = MutableStateFlow(HomeScreenState())
    val state: StateFlow<HomeScreenState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<HomeScreenEvent>()
    val events: SharedFlow<HomeScreenEvent> = _events.asSharedFlow()

    fun executeInstruction(instruction: String) {
        viewModelScope.launch {
            try {
                val result = mobileAgent.runInstruction(instruction)
                _state.emit(state.value.copy(result = result))
                _events.emit(HomeScreenEvent.ExecutionSuccess)
            } catch (e: Exception) {
                _events.emit(HomeScreenEvent.ExecutionError(e.message ?: "Unknown error"))
            }
        }
    }
}
```

---

## Dependency Injection

### Hilt Integration
```kotlin
// Define modules
@Module
@InstallIn(SingletonComponent::class)
object VLMModule {
    @Provides
    @Singleton
    fun provideVLMClient(
        @ApplicationContext context: Context
    ): VLMClient {
        val apiKey = SettingsManager.getInstance().getApiKey()
        return VLMClient(apiKey)
    }
}

// Inject in ViewModels
class HomeScreenViewModel @Inject constructor(
    private val mobileAgent: MobileAgent
) : ViewModel() { }
```

---

## Version Control Practices

### Commit Messages
```
✓ Good commits:
  "Add skill delegation for food ordering"
  "Fix shell command escaping vulnerability"
  "Improve VLM request timeout handling"

✗ Bad commits:
  "Update" (too vague)
  "Fix bug" (no context)
  "AI-assisted implementation" (don't mention AI)
```

### Branch Naming
```
feature/skill-delegation
fix/shell-injection-vulnerability
refactor/vlm-client-consolidation
docs/architecture-overview
```

---

## Code Review Checklist

- [ ] **Correctness**: Logic is sound; no off-by-one errors
- [ ] **Naming**: Classes, functions, variables clearly named
- [ ] **Documentation**: KDoc for public APIs; inline comments where needed
- [ ] **Error Handling**: Try-catch for expected failures; escalation for critical errors
- [ ] **Performance**: No unnecessary allocations; bitmaps recycled; coroutines used correctly
- [ ] **Security**: Input validated; secrets encrypted; shell commands escaped
- [ ] **Testing**: Unit tests cover happy path + error cases
- [ ] **Threading**: Proper dispatchers; no blocking I/O on main thread
- [ ] **Memory**: Bitmap recycling; resource cleanup; no leaks

---

## Common Patterns

### Retry Logic
```kotlin
suspend inline fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelayMs: Long = 1000L,
    crossinline block: suspend () -> T
): T {
    var delay = initialDelayMs
    repeat(maxRetries - 1) {
        try {
            return block()
        } catch (e: Exception) {
            delay(delay)
            delay *= 2
        }
    }
    return block()  // Last attempt
}

// Usage
val response = retryWithBackoff(maxRetries = 4) {
    vlmClient.analyzeScreenshot(screenshot, instruction)
}
```

### Singleton Pattern
```kotlin
class SettingsManager private constructor(context: Context) {
    companion object {
        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context? = null): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context ?: App.getInstance()).also {
                    instance = it
                }
            }
        }
    }
}
```

---

## Linting & Formatting

### Run Before Commit
```bash
# Kotlin linting (if using ktlint)
./gradlew ktlint

# Compose linting
./gradlew composeMetrics

# Tests
./gradlew testDebug

# Build check
./gradlew assembleDebug
```

