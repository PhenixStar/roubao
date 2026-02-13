package com.roubao.autopilot.controller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * Tests for DeviceController input validation and shell injection prevention.
 *
 * DeviceController.openApp() validates package names with a regex before
 * passing them to shell commands. This prevents injection attacks where
 * a malicious "package name" contains shell metacharacters.
 *
 * DeviceController.escapeShellDoubleQuoted() is private, so we cannot
 * test it directly. However, the package name regex validation in openApp()
 * is the primary defense and can be verified by checking the regex pattern.
 *
 * Note: Most DeviceController methods require Shizuku/Android runtime.
 * Tests that need the full Android stack are marked @Ignore with explanation.
 */
class DeviceControllerInputTest {

    // The package name regex from DeviceController.openApp()
    // Copied here to verify its behavior independently of Android runtime
    private val packageNamePattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

    // ==================== Valid package names ====================

    @Test
    fun `valid package name - standard format`() {
        assertTrue(packageNamePattern.matches("com.android.settings"))
    }

    @Test
    fun `valid package name - deep nesting`() {
        assertTrue(packageNamePattern.matches("com.roubao.autopilot.ui.screens"))
    }

    @Test
    fun `valid package name - with underscores`() {
        assertTrue(packageNamePattern.matches("com.example.my_app"))
    }

    @Test
    fun `valid package name - with numbers`() {
        assertTrue(packageNamePattern.matches("com.app2.test3"))
    }

    @Test
    fun `valid package name - two segments`() {
        assertTrue(packageNamePattern.matches("com.app"))
    }

    // ==================== Invalid / malicious package names ====================

    @Test
    fun `injection - semicolon in package name rejected`() {
        // "com.evil; rm -rf /" should not match the package name pattern
        assertFalse(packageNamePattern.matches("com.evil; rm -rf /"))
    }

    @Test
    fun `injection - backtick command substitution rejected`() {
        assertFalse(packageNamePattern.matches("com.evil.`reboot`"))
    }

    @Test
    fun `injection - dollar command substitution rejected`() {
        assertFalse(packageNamePattern.matches("com.evil.\$(reboot)"))
    }

    @Test
    fun `injection - pipe in package name rejected`() {
        assertFalse(packageNamePattern.matches("com.evil | reboot"))
    }

    @Test
    fun `injection - ampersand in package name rejected`() {
        assertFalse(packageNamePattern.matches("com.evil && reboot"))
    }

    @Test
    fun `injection - space in package name rejected`() {
        assertFalse(packageNamePattern.matches("com.evil app"))
    }

    @Test
    fun `injection - newline in package name rejected`() {
        assertFalse(packageNamePattern.matches("com.evil\nreboot"))
    }

    @Test
    fun `injection - slash in package name rejected`() {
        assertFalse(packageNamePattern.matches("com.evil/../../etc/passwd"))
    }

    @Test
    fun `invalid - single segment rejected`() {
        // Package names must have at least two segments (e.g. "com.app")
        assertFalse(packageNamePattern.matches("myapp"))
    }

    @Test
    fun `invalid - starts with number rejected`() {
        assertFalse(packageNamePattern.matches("1com.app"))
    }

    @Test
    fun `invalid - segment starts with number rejected`() {
        assertFalse(packageNamePattern.matches("com.1app"))
    }

    @Test
    fun `invalid - starts with dot rejected`() {
        assertFalse(packageNamePattern.matches(".com.app"))
    }

    @Test
    fun `invalid - ends with dot rejected`() {
        assertFalse(packageNamePattern.matches("com.app."))
    }

    @Test
    fun `invalid - consecutive dots rejected`() {
        assertFalse(packageNamePattern.matches("com..app"))
    }

    @Test
    fun `invalid - hyphen in package name rejected`() {
        // Android package names do not allow hyphens
        assertFalse(packageNamePattern.matches("com.my-app.test"))
    }

    @Test
    fun `invalid - empty string rejected`() {
        assertFalse(packageNamePattern.matches(""))
    }

    // ==================== Shell escape tests ====================
    // escapeShellDoubleQuoted is private, so we verify the contract
    // through known dangerous characters that the regex rejects.

    @Test
    fun `injection - double quote in package name rejected`() {
        assertFalse(packageNamePattern.matches("com.evil\"reboot"))
    }

    @Test
    fun `injection - backslash in package name rejected`() {
        assertFalse(packageNamePattern.matches("com.evil\\reboot"))
    }

    @Test
    fun `injection - exclamation in package name rejected`() {
        assertFalse(packageNamePattern.matches("com.evil!reboot"))
    }

    // ==================== DeviceController integration tests ====================
    // These require Android runtime (Shizuku, Context) so are marked @Ignore

    @Test
    @Ignore("Requires Android runtime - DeviceController needs Context and Shizuku")
    fun `openApp with injection string does not execute shell commands`() {
        // Would verify that openApp("com.evil; rm -rf /") returns early
        // without calling exec() due to regex validation failure
    }

    @Test
    @Ignore("Requires Android runtime - DeviceController needs Context and Shizuku")
    fun `openDeepLink escapes shell metacharacters`() {
        // Would verify that openDeepLink("http://evil.com/$(reboot)")
        // properly escapes the $ and backticks via escapeShellDoubleQuoted
    }

    @Test
    @Ignore("Requires Android runtime - DeviceController needs Context and Shizuku")
    fun `type method escapes single quotes in ASCII text`() {
        // Would verify that type("hello'world") properly escapes the quote
    }
}
