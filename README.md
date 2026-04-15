# autodoc-agent

> An AI multi-agent GitHub Action that detects code changes when a PR is merged and automatically updates relevant documentation.

**[한국어](README.ko.md)**

---

## Why We Built This

PR gets merged — README is 3 months out of date, architecture doc still reflects the pre-refactor structure, API doc has half the endpoints missing. This pattern repeats across every team.

autodoc-agent automates that gap with AI agents. When a PR is merged, it analyzes the code changes, selects only the affected documents, drafts updates, and opens a new PR for human review.

---

## Architecture

```
PR Merge Event (GitHub Actions)
        ↓
  OrchestratorAgent
  - Analyze PR diff
  - Determine affected documents
        ↓ Parallel A2A calls
  ┌─────────────────────────┐
  │  ReadmeAgent            │  README.md
  │  ArchDocAgent           │  architecture.md
  │  ApiDocAgent            │  api.md
  │  TestDocAgent           │  testing.md
  │  ChangelogAgent         │  CHANGELOG.md
  │  SetupDocAgent          │  setup.md
  │  SpecDocAgent           │  spec/*.md
  └─────────────────────────┘
        ↓
  Auto-create documentation PR
  → Human reviews and merges
```

**Tech Stack**: Kotlin · [Koog 0.8.0](https://github.com/JetBrains/koog) · A2A Protocol · Ktor · GitHub Actions

---

## Usage

### 1. Add config file to your repo

```yaml
# .autodoc/config.yml
platform: android  # android | ios | backend | frontend | generic

model:
  provider: anthropic  # anthropic | google | openai

documents:
  README.md: overwrite
  architecture.md: overwrite
  CHANGELOG.md: append    # append keeps existing content and adds change history
  spec/*.md: append

spec:
  source: markdown         # markdown | confluence
  path: docs/spec/
```

### 2. Add GitHub Actions workflow

```yaml
# .github/workflows/autodoc.yml
name: AutoDoc Agent

on:
  pull_request:
    types: [closed]

jobs:
  autodoc:
    if: github.event.pull_request.merged == true
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: Veronikapj/autodoc-agent@v1.0.0
        with:
          anthropic-api-key: ${{ secrets.ANTHROPIC_API_KEY }}
```

### 3. Configure secrets

| Secret | Required | Description |
|--------|----------|-------------|
| `ANTHROPIC_API_KEY` | Required (Anthropic) | Claude API key |
| `GOOGLE_API_KEY` | Required (Google) | Gemini API key |
| `OPENAI_API_KEY` | Required (OpenAI) | OpenAI API key |
| `CONFLUENCE_TOKEN` | Optional | Only needed for Confluence spec sync |

---

## Document Modes

| Mode | Behavior |
|------|----------|
| `overwrite` | Rewrites the entire document to reflect the current state (README, architecture, etc.) |
| `append` | Keeps existing content and appends a new change history section (CHANGELOG, specs, etc.) |

---

## Supported Platforms

Each platform has built-in document templates and file pattern mappings.

| Platform | Detected Patterns | Generated Documents |
|----------|-------------------|---------------------|
| `android` | `*Activity.kt`, `build.gradle.kts`, `*Test.kt` | README, architecture, modules, testing |
| `ios` | `*.swift`, `*.xcodeproj` | README, architecture, testing |
| `backend` | `*Controller.kt`, `*Api.kt`, `*Repository.kt` | README, architecture, api, database |
| `frontend` | `*.tsx`, `*.vue`, `*.stories.*` | README, components, storybook |
| `generic` | All platforms | README, CHANGELOG, setup, spec |

---

## Custom Templates

You can define your own templates to match team conventions. Files placed in the repo take priority over built-in templates.

```
.autodoc/
└── templates/
    └── android/
        └── README.md.tmpl   ← overrides the built-in template
```

Template priority: **Repo custom** → **Platform built-in** → **Generic built-in**

---

## Confluence Integration

```yaml
# .autodoc/config.yml
spec:
  source: confluence
  base_url: https://yourcompany.atlassian.net
  space_key: ANDROID
  page_ids:
    - 123456
    - 789012
```

Fetches Confluence page content, compares it against the PR diff, and updates `spec/*.md` in append mode.

---

## Local Run

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export GITHUB_TOKEN=ghp_...
export GITHUB_REPOSITORY=owner/repo
export PR_NUMBER=42
export TARGET_REPO_PATH=/path/to/target-repo

./gradlew run
```

---

## Project Structure

```
src/main/kotlin/io/github/veronikapj/autodoc/
├── Main.kt                          # Entry point, full pipeline
├── config/
│   ├── AutoDocConfig.kt             # Config data model
│   └── ConfigLoader.kt             # .autodoc/config.yml parser
├── platform/
│   ├── PlatformConfig.kt           # File pattern → agent mapping
│   └── TemplateResolver.kt         # 3-level template priority loader
├── agent/
│   ├── OrchestratorAgent.kt        # PR diff analysis, parallel A2A calls
│   └── specialist/
│       ├── ReadmeAgent.kt
│       ├── ArchDocAgent.kt
│       ├── ApiDocAgent.kt
│       ├── TestDocAgent.kt
│       ├── ChangelogAgent.kt
│       ├── SetupDocAgent.kt
│       └── SpecDocAgent.kt
├── a2a/
│   ├── DocAgentExecutor.kt         # A2A server executor
│   ├── A2AServerManager.kt         # Dynamic port allocation, health check
│   └── A2AClientManager.kt         # Parallel connection management
└── tools/
    ├── GitHubTool.kt               # PR fetch, docs PR creation
    └── ConfluenceTool.kt           # Confluence page fetcher
```

---

## License

MIT