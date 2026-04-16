package io.github.veronikapj.autodoc.llm

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import org.slf4j.LoggerFactory

class ClaudeCodeLLMClient(
    private val claudePath: String = DEFAULT_CLAUDE_PATH,
) : LLMClient() {

    override fun llmProvider(): LLMProvider = ClaudeCodeLLMProvider

    override fun close() {
        // No persistent resources to release
    }

    /**
     * Executes a prompt by shelling out to the claude CLI binary.
     */
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> {
        val flatPrompt = flattenPrompt(prompt)
        val args = buildArgs(flatPrompt, model.id)
        log.debug("Executing claude CLI: {}", args.joinToString(" "))

        val output = runProcess(args)
        return listOf(Message.Assistant(output, ResponseMetaInfo.create(Clock.System)))
    }

    /**
     * Streaming is not supported — callers should use [execute] instead.
     */
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): Flow<ai.koog.prompt.streaming.StreamFrame> = flow {
        throw UnsupportedOperationException(
            "ClaudeCodeLLMClient does not support streaming execution"
        )
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
        throw UnsupportedOperationException("ClaudeCodeLLMClient does not support moderation")

    /**
     * Checks that the claude binary is reachable and authenticated.
     * Throws [IllegalStateException] if the binary is not found or the exit code is non-zero.
     */
    suspend fun checkAuth() {
        val args = listOf(claudePath, "auth", "status")
        log.info("Checking claude auth: {}", args.joinToString(" "))
        runProcess(args)
    }

    // -------------------------------------------------------------------------
    // Internal helpers exposed for testing
    // -------------------------------------------------------------------------

    internal fun flattenPromptForTest(systemPrompt: String, userMessage: String): String =
        "[System]: $systemPrompt\n[Human]: $userMessage"

    internal fun buildArgsForTest(flatPrompt: String, modelId: String): List<String> =
        buildArgs(flatPrompt, modelId)

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun flattenPrompt(prompt: Prompt): String {
        val sb = StringBuilder()
        for (message in prompt.messages) {
            when (message) {
                is Message.System -> sb.appendLine("[System]: ${message.content}")
                is Message.User -> sb.appendLine("[Human]: ${message.content}")
                is Message.Assistant -> sb.appendLine("[Assistant]: ${message.content}")
                else -> sb.appendLine(message.content)
            }
        }
        return sb.toString().trimEnd()
    }

    private fun buildArgs(flatPrompt: String, modelId: String): List<String> {
        val args = mutableListOf(claudePath, "-p", flatPrompt, "--no-markdown")
        if (modelId.isNotBlank()) {
            args += listOf("--model", modelId)
        }
        return args
    }

    private suspend fun runProcess(args: List<String>): String = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
        } catch (e: java.io.IOException) {
            throw IllegalStateException(
                "Failed to start claude binary at '${args.first()}': ${e.message}", e
            )
        }

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw IllegalStateException(
                "claude CLI exited with code $exitCode. Output: ${output.take(500)}"
            )
        }

        output.trim()
    }

    // -------------------------------------------------------------------------
    // LLMProvider singleton for claude-code
    // -------------------------------------------------------------------------

    object ClaudeCodeLLMProvider : LLMProvider(id = "claude-code", display = "Claude Code")

    companion object {
        private const val DEFAULT_CLAUDE_PATH = "/opt/homebrew/bin/claude"
        private val log = LoggerFactory.getLogger(ClaudeCodeLLMClient::class.java)
    }
}
