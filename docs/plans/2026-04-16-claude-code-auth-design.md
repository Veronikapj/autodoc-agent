# Design: Claude Code CLI Provider (No API Key Required)

**Date:** 2026-04-16  
**Status:** Approved  
**Target:** `Veronikapj/autodoc-agent` upstream PR

---

## Problem

Currently autodoc-agent requires an API key (`ANTHROPIC_API_KEY`, `GOOGLE_API_KEY`, or `OPENAI_API_KEY`) to run. Developers who already have Claude Code CLI installed and authenticated with their claude.ai account cannot use autodoc-agent locally without separately obtaining and paying for an API key.

## Goal

Add `claude-code` as a new `ModelProvider` that delegates LLM calls to the locally installed `claude` CLI binary, using the user's existing claude.ai account authentication.

---

## Architecture

### Files Changed

| File | Change |
|------|--------|
| `config/AutoDocConfig.kt` | Add `CLAUDE_CODE` to `ModelProvider` enum |
| `Main.kt` | Add `CLAUDE_CODE` case in `buildExecutor`, including pre-flight auth check |
| `llm/ClaudeCodeLLMClient.kt` | **New** — Koog `LLMClient` implementation via subprocess |

### Config Usage

```yaml
# .autodoc/config.yml
model:
  provider: claude-code
  name: claude-opus-4-6  # optional, passed as --model flag
```

No secrets or environment variables required beyond an installed + authenticated `claude` CLI.

---

## ClaudeCodeLLMClient Design

### Subprocess Call

```bash
claude -p "<flattened prompt>" --no-markdown [--model <name>]
```

Invoked via Kotlin `ProcessBuilder` with a configurable timeout (default: 120s).

### Multi-turn Conversation Handling

The `claude -p` flag is single-shot. Conversation history is flattened into a single structured prompt before invocation:

```
[System]: <system prompt>

[Human]: <turn 1>
[Assistant]: <turn 1 response>
[Human]: <latest request>
```

This is appropriate for autodoc's usage pattern, where each specialist agent call is effectively single-turn.

### Error Handling

| Condition | Behavior |
|-----------|----------|
| `claude` not in PATH | `IllegalStateException("claude CLI not found. Install from https://claude.ai/code")` |
| Not authenticated | Pre-flight `claude auth status` check → clear error message before any LLM call |
| Non-zero exit code | Exception with stderr content included |
| Timeout (>120s) | `TimeoutException` with elapsed time |

### Pre-flight Check (in `buildExecutor`)

When `provider: claude-code` is selected, `buildExecutor` runs `claude auth status` once at startup — equivalent to where API key env var validation happens for other providers.

---

## Documentation Updates

### README.md

Add to the "Usage" section after existing provider instructions:

```markdown
### Claude Code (account-based, no API key required)

If you have Claude Code CLI installed and authenticated, you can run autodoc-agent
without any API key:

```yaml
model:
  provider: claude-code
  name: claude-opus-4-6  # optional
```

**Prerequisites:** `claude` CLI installed + `claude auth login` completed
```

Update the Secrets table to clarify `ANTHROPIC_API_KEY` is only required for the `anthropic` provider.

---

## Constraints & Non-Goals

- **Local-only**: GitHub Actions cannot use this provider (no browser-based OAuth in CI). The existing API key providers remain the recommended approach for CI.
- **No token extraction**: This design does not extract or reuse OAuth tokens from `~/.claude/`. All auth is delegated entirely to the CLI binary.
- **Backwards compatible**: All existing providers are unaffected.

---

## Upstream PR Plan

| Field | Value |
|-------|-------|
| Branch | `feature/claude-code-auth` |
| PR title | `feat: add Claude Code CLI provider (no API key required)` |
| Base | `Veronikapj/autodoc-agent:main` |
| Head | `piljubae/autodoc-agent:feature/claude-code-auth` |
