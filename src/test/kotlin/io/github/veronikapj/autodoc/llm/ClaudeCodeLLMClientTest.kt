package io.github.veronikapj.autodoc.llm

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

class ClaudeCodeLLMClientTest {

    @Test
    fun `flattenPromptForTest formats system and user messages correctly`() {
        val client = ClaudeCodeLLMClient()
        val result = client.flattenPromptForTest(
            systemPrompt = "You are a helpful assistant.",
            userMessage = "Hello"
        )
        assertTrue(result.contains("[System]: You are a helpful assistant."))
        assertTrue(result.contains("[Human]: Hello"))
    }

    @Test
    fun `throws when claude binary not found`() {
        val client = ClaudeCodeLLMClient(claudePath = "/nonexistent/claude")
        assertThrows<IllegalStateException> {
            runBlocking { client.checkAuth() }
        }
    }

    @Test
    fun `buildArgs includes model flag when model id is not blank`() {
        val client = ClaudeCodeLLMClient()
        val args = client.buildArgsForTest("hello", "claude-opus-4-6")
        assertTrue(args.contains("--model"))
        assertTrue(args.contains("claude-opus-4-6"))
    }

    @Test
    fun `buildArgs omits model flag when model id is blank`() {
        val client = ClaudeCodeLLMClient()
        val args = client.buildArgsForTest("hello", "")
        assertTrue(!args.contains("--model"))
    }
}
