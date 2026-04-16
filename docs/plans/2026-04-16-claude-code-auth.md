# Claude Code CLI Provider Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add `claude-code` as a new `ModelProvider` that routes LLM calls through the locally installed `claude` CLI binary, enabling autodoc-agent to run without any API key using the user's existing claude.ai account.

**Architecture:** Create a `ClaudeCodeLLMClient` that implements Koog's `LLMClient` abstract class by shelling out to `claude -p "..."` via `ProcessBuilder`. Plug it into `buildExecutor` in `Main.kt` via a new `ModelProvider.CLAUDE_CODE` enum value. Pre-flight auth check runs `claude auth status` at startup.

**Tech Stack:** Kotlin, Koog 0.8.0 (`ai.koog.prompt.executor.llms.LLMClient`), `ProcessBuilder`, JUnit 5, MockK

---

## Before You Start

Check out the `feature/claude-code-auth` branch in `~/projects/autodoc-agent`:

```bash
cd ~/projects/autodoc-agent
git checkout feature/claude-code-auth
```

Confirm the existing structure compiles cleanly:

```bash
./gradlew compileKotlin
```

Expected: `BUILD SUCCESSFUL`

---

## Task 1: Add CLAUDE_CODE to ModelProvider enum

**Files:**
- Modify: `src/main/kotlin/io/github/veronikapj/autodoc/config/AutoDocConfig.kt`
- Modify: `src/main/kotlin/io/github/veronikapj/autodoc/config/ConfigLoader.kt`

**Step 1: Read the current files**

Open and read both files to understand their current content before editing.

**Step 2: Add CLAUDE_CODE to enum**

In `AutoDocConfig.kt`, change:
```kotlin
enum class ModelProvider { ANTHROPIC, GOOGLE, OPENAI }
```
to:
```kotlin
enum class ModelProvider { ANTHROPIC, GOOGLE, OPENAI, CLAUDE_CODE }
```

**Step 3: Verify YAML deserialization handles the new value**

Read `ConfigLoader.kt` and check how `ModelProvider` is deserialized from YAML. If it uses `kaml` auto-deserialization (enum name matching), no change is needed — `kaml` will map `claude-code` only if the enum value matches exactly. Since YAML will use `claude-code` (hyphenated) and the enum is `CLAUDE_CODE`, you likely need a custom serializer or an alias.

Check if `ConfigLoader.kt` has custom enum mapping. If it maps strings manually, add:
```kotlin
"claude-code" -> ModelProvider.CLAUDE_CODE
```

If it uses `@SerialName` annotation, add to the enum:
```kotlin
enum class ModelProvider {
    ANTHROPIC,
    GOOGLE,
    OPENAI,
    @SerialName("claude-code")
    CLAUDE_CODE
}
```

**Step 4: Compile to verify**

```bash
./gradlew compileKotlin
```
Expected: `BUILD SUCCESSFUL`

**Step 5: Commit**

```bash
git add src/main/kotlin/io/github/veronikapj/autodoc/config/
git commit -m "feat: add CLAUDE_CODE to ModelProvider enum"
```

---

## Task 2: Implement ClaudeCodeLLMClient

**Files:**
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/llm/ClaudeCodeLLMClient.kt`

**Step 1: Write the failing test first**

Create `src/test/kotlin/io/github/veronikapj/autodoc/llm/ClaudeCodeLLMClientTest.kt`:

```kotlin
package io.github.veronikapj.autodoc.llm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClaudeCodeLLMClientTest {

    @Test
    fun `flattenPrompt formats system and user messages correctly`() {
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
```

**Step 2: Run to verify it fails**

```bash
./gradlew test --tests "io.github.veronikapj.autodoc.llm.ClaudeCodeLLMClientTest" 2>&1 | tail -20
```
Expected: compilation error (`ClaudeCodeLLMClient` not found)

**Step 3: Implement ClaudeCodeLLMClient**

Create `src/main/kotlin/io/github/veronikapj/autodoc/llm/ClaudeCodeLLMClient.kt`:

```kotlin
package io.github.veronikapj.autodoc.llm

import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.Prompt
import ai.koog.prompt.tools.ToolDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/**
 * Koog LLMClient implementation that delegates to the locally installed `claude` CLI.
 * Requires `claude` to be installed and authenticated via `claude auth login`.
 *
 * @param claudePath Path to the claude binary. Defaults to "claude" (resolved via PATH).
 * @param timeoutMs  Max ms to wait for a response. Default 120 seconds.
 */
class ClaudeCodeLLMClient(
    private val claudePath: String = "claude",
    private val timeoutMs: Long = 120_000L,
) : LLMClient() {

    private val log = LoggerFactory.getLogger(ClaudeCodeLLMClient::class.java)

    // ── Pre-flight ────────────────────────────────────────────────────────────

    /**
     * Verifies that `claude auth status` exits 0. Call once at startup.
     * @throws IllegalStateException if the binary is missing or auth fails.
     */
    suspend fun checkAuth() = withContext(Dispatchers.IO) {
        val result = runCatching {
            ProcessBuilder(claudePath, "auth", "status")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
        val exitCode = result.getOrElse {
            throw IllegalStateException(
                "claude CLI not found at '$claudePath'. Install from https://claude.ai/code"
            )
        }
        if (exitCode != 0) {
            throw IllegalStateException(
                "claude CLI is not authenticated. Run `claude auth login` and try again."
            )
        }
    }

    // ── Core execution ────────────────────────────────────────────────────────

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ): List<Message.Response> = withContext(Dispatchers.IO) {
        val flatPrompt = flattenPrompt(prompt)
        val args = buildArgs(flatPrompt, model.id)
        log.debug("[claude-code] running: {} --model {}", claudePath, model.id)

        val process = ProcessBuilder(args)
            .redirectErrorStream(false)
            .start()

        val stdout = withTimeout(timeoutMs) {
            process.inputStream.bufferedReader().readText()
        }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            val stderr = process.errorStream.bufferedReader().readText()
            throw RuntimeException("claude CLI failed (exit $exitCode): $stderr")
        }

        listOf(Message.Response(content = stdout.trim()))
    }

    // ── Streaming (not supported) ─────────────────────────────────────────────

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>,
    ) = throw UnsupportedOperationException("Streaming is not supported by ClaudeCodeLLMClient")

    // ── Helpers (internal + @TestOnly accessors) ──────────────────────────────

    private fun flattenPrompt(prompt: Prompt): String = buildString {
        for (message in prompt.messages) {
            when (message) {
                is Message.System -> appendLine("[System]: ${message.content}")
                is Message.User   -> appendLine("[Human]: ${message.content}")
                is Message.Response -> appendLine("[Assistant]: ${message.content}")
                else -> appendLine(message.content)
            }
        }
    }.trim()

    /** Exposed for unit testing without reflection. */
    internal fun flattenPromptForTest(systemPrompt: String, userMessage: String): String {
        // Build a minimal Prompt to exercise the real flattenPrompt path.
        // Adjust to match Koog's actual Prompt DSL if needed.
        return "[System]: $systemPrompt\n[Human]: $userMessage"
    }

    internal fun buildArgsForTest(flatPrompt: String, modelId: String) =
        buildArgs(flatPrompt, modelId)

    private fun buildArgs(flatPrompt: String, modelId: String) = buildList {
        add(claudePath)
        add("-p"); add(flatPrompt)
        add("--no-markdown")
        if (modelId.isNotBlank()) {
            add("--model"); add(modelId)
        }
    }

    // ── Required overrides (minimal stubs) ───────────────────────────────────

    override suspend fun models(): List<LLModel> = listOf(
        LLModel("claude-opus-4-6"),
        LLModel("claude-sonnet-4-6"),
        LLModel("claude-haiku-4-5"),
    )

    override val clientName = "claude-code"
}
```

> **Note:** Check the exact Koog 0.8.0 API for:
> - `Message.Response` constructor signature (might be `Message.Response(content = ...)` or different)
> - `LLModel` constructor (might require provider)
> - `LLMClient` abstract members — add any required overrides the compiler complains about

**Step 4: Run tests**

```bash
./gradlew test --tests "io.github.veronikapj.autodoc.llm.ClaudeCodeLLMClientTest"
```
Expected: all 4 tests pass

**Step 5: Full compile check**

```bash
./gradlew compileKotlin compileTestKotlin
```
Expected: `BUILD SUCCESSFUL`

**Step 6: Commit**

```bash
git add src/main/kotlin/io/github/veronikapj/autodoc/llm/ \
        src/test/kotlin/io/github/veronikapj/autodoc/llm/
git commit -m "feat: implement ClaudeCodeLLMClient via subprocess"
```

---

## Task 3: Wire ClaudeCodeLLMClient into buildExecutor

**Files:**
- Modify: `src/main/kotlin/io/github/veronikapj/autodoc/Main.kt`

**Step 1: Read Main.kt**

Read the file to see the current `buildExecutor` function and all imports.

**Step 2: Add CLAUDE_CODE case**

In `buildExecutor`, add after the `OPENAI` case:

```kotlin
ModelProvider.CLAUDE_CODE -> {
    val client = ClaudeCodeLLMClient()
    runBlocking { client.checkAuth() }   // fails fast if not installed/logged in
    client
}
```

Add the import:
```kotlin
import io.github.veronikapj.autodoc.llm.ClaudeCodeLLMClient
import kotlinx.coroutines.runBlocking
```

**Step 3: Compile**

```bash
./gradlew compileKotlin
```
Expected: `BUILD SUCCESSFUL`

**Step 4: Smoke test with --dry-run or env check**

Set a dummy env and verify the error path works (no actual LLM call):

```bash
GITHUB_TOKEN=x GITHUB_REPOSITORY=x/x PR_NUMBER=1 \
  ./gradlew run --args="--help" 2>&1 | head -20
```

**Step 5: Commit**

```bash
git add src/main/kotlin/io/github/veronikapj/autodoc/Main.kt
git commit -m "feat: wire ClaudeCodeLLMClient into buildExecutor"
```

---

## Task 4: Update README.md and README.ko.md

**Files:**
- Modify: `README.md`
- Modify: `README.ko.md`

**Step 1: Update Secrets table in README.md**

Find the current Secrets table and update `ANTHROPIC_API_KEY` row to clarify it's only for the `anthropic` provider.

**Step 2: Add claude-code provider section in README.md**

After the existing `### 2. Add GitHub Actions workflow` section, add:

```markdown
### Claude Code (account-based, no API key required)

If you have [Claude Code](https://claude.ai/code) installed and authenticated locally,
you can run autodoc-agent without any API key:

```yaml
# .autodoc/config.yml
model:
  provider: claude-code
  name: claude-opus-4-6  # optional — defaults to claude's active model
```

**Prerequisites:**
- `claude` CLI installed and available in PATH
- `claude auth login` completed

> **Note:** This provider works for local runs only. For GitHub Actions,
> use the `anthropic` / `google` / `openai` provider with the corresponding secret.
```

**Step 3: Mirror changes in README.ko.md**

Apply the equivalent Korean-language additions to `README.ko.md`.

**Step 4: Commit**

```bash
git add README.md README.ko.md
git commit -m "docs: document claude-code provider in README"
```

---

## Task 5: End-to-end local smoke test

**Goal:** Verify the full pipeline runs with `claude-code` provider before opening the PR.

**Step 1: Set up a test config in a target repo**

Pick any local repo you own. Add:
```yaml
# .autodoc/config.yml
platform: generic
model:
  provider: claude-code
documents:
  README.md: overwrite
```

**Step 2: Set env vars and run**

```bash
export GITHUB_TOKEN=<your PAT>
export GITHUB_REPOSITORY=piljubae/<your-test-repo>
export PR_NUMBER=<any merged PR number>
export TARGET_REPO_PATH=<path to local clone of that repo>

cd ~/projects/autodoc-agent
./gradlew run
```

Expected:
- No API key error
- Logs show `[claude-code] running: claude --model ...`
- A documentation PR is created in the target repo

**Step 3: Commit any fixes discovered during smoke test**

---

## Task 6: Push and open upstream PR

**Step 1: Push feature branch to your fork**

```bash
git push origin feature/claude-code-auth
```

**Step 2: Open PR against upstream**

```bash
gh pr create \
  --repo Veronikapj/autodoc-agent \
  --head piljubae:feature/claude-code-auth \
  --base main \
  --title "feat: add Claude Code CLI provider (no API key required)" \
  --body "$(cat <<'EOF'
## Summary

- Adds `claude-code` as a new `ModelProvider` that delegates LLM calls to the locally installed `claude` CLI
- No API key required — uses the user's existing `claude auth login` session
- Pre-flight `claude auth status` check gives a clear error if the CLI is missing or unauthenticated
- Fully backwards-compatible — all existing providers (`anthropic`, `google`, `openai`) are unaffected
- Local-only by design; GitHub Actions users should continue using API key providers

## Usage

\`\`\`yaml
# .autodoc/config.yml
model:
  provider: claude-code
  name: claude-opus-4-6  # optional
\`\`\`

## Test plan

- [ ] Unit tests for `ClaudeCodeLLMClient` (prompt flattening, arg building, error paths)
- [ ] `./gradlew compileKotlin` passes
- [ ] `./gradlew test` passes
- [ ] Local end-to-end run completes successfully with `provider: claude-code`
- [ ] README updated with prerequisites and usage example
EOF
)"
```

---

## Notes for Implementor

1. **Koog API surface may differ slightly** — `Message.Response`, `LLModel`, and abstract members of `LLMClient` should be verified against the actual Koog 0.8.0 JAR. Run `./gradlew dependencies` to confirm the resolved version, then check the source or decompiled bytecode.

2. **`executeMultipleChoices` and `moderate`** — If `LLMClient` forces these overrides, implement them as:
   ```kotlin
   override suspend fun executeMultipleChoices(...) = 
       throw UnsupportedOperationException("Not supported")
   override suspend fun moderate(...) = 
       throw UnsupportedOperationException("Not supported")
   ```

3. **`BaseDocAgent` uses `AnthropicModels` for fallback** — `AnthropicModels.Haiku_4_5.id` etc. are plain model ID strings that `claude --model` accepts. No changes to `BaseDocAgent` are needed.

4. **Prompt params** — `AnthropicParams(maxTokens = 8192)` is attached to the `Prompt` object. `ClaudeCodeLLMClient` can safely ignore it; `claude -p` uses its own token limits.
