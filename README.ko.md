# autodoc-agent

> PR이 머지될 때 코드 변경을 감지하고 관련 문서를 자동으로 업데이트하는 AI 멀티에이전트 GitHub Action

**[English](README.md)**

---

## 왜 만들었나

PR은 머지됐는데 README는 3달 전 내용, 아키텍처 문서는 리팩터링 이전 구조, API 문서는 엔드포인트가 절반쯤 사라진 채로 남아 있다. 이 패턴은 모든 팀에서 반복된다.

autodoc-agent는 그 갭을 AI 에이전트로 자동화한다. PR이 머지되면 코드 변경을 분석해서 영향받는 문서만 골라 초안을 작성하고, 리뷰용 PR을 새로 만들어 준다.

---

## 아키텍처

```
PR 머지 이벤트 (GitHub Actions)
        ↓
  OrchestratorAgent
  - PR diff 분석
  - 영향받는 문서 판단
        ↓ 병렬 A2A 호출
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
  문서 업데이트 PR 자동 생성
  → 사람이 검토 후 머지
```

**기술 스택**: Kotlin · [Koog 0.8.0](https://github.com/JetBrains/koog) · A2A Protocol · Ktor · GitHub Actions

---

## 사용법

### 1. 타겟 레포에 설정 파일 추가

```yaml
# .autodoc/config.yml
platform: android  # android | ios | backend | frontend | generic

model:
  provider: anthropic  # anthropic | google | openai

documents:
  README.md: overwrite
  architecture.md: overwrite
  CHANGELOG.md: append    # append 모드는 기존 내용 유지 + 변경분 추가
  spec/*.md: append

spec:
  source: markdown         # markdown | confluence
  path: docs/spec/
```

### 2. GitHub Actions 워크플로 추가

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

### 3. 시크릿 설정

| 시크릿 | 필수 | 설명 |
|--------|------|------|
| `ANTHROPIC_API_KEY` | 필수 (Anthropic) | Claude API 키 |
| `GOOGLE_API_KEY` | 필수 (Google) | Gemini API 키 |
| `OPENAI_API_KEY` | 필수 (OpenAI) | OpenAI API 키 |
| `CONFLUENCE_TOKEN` | 선택 | Confluence 기획서 연동 시 |

---

## 문서 모드

| 모드 | 동작 |
|------|------|
| `overwrite` | 문서 전체를 새로 작성 (README, 아키텍처 등 현재 상태 반영) |
| `append` | 기존 내용 유지 + 변경 이력 섹션 추가 (CHANGELOG, 기획서 등) |

---

## 지원 플랫폼

각 플랫폼별로 최적화된 문서 템플릿과 파일 패턴 매핑이 내장돼 있다.

| 플랫폼 | 감지 패턴 예시 | 생성 문서 |
|--------|--------------|----------|
| `android` | `*Activity.kt`, `build.gradle.kts`, `*Test.kt` | README, architecture, modules, testing |
| `ios` | `*.swift`, `*.xcodeproj` | README, architecture, testing |
| `backend` | `*Controller.kt`, `*Api.kt`, `*Repository.kt` | README, architecture, api, database |
| `frontend` | `*.tsx`, `*.vue`, `*.stories.*` | README, components, storybook |
| `generic` | 모든 플랫폼 공통 | README, CHANGELOG, setup, spec |

---

## 커스텀 템플릿

팀 규칙에 맞게 템플릿을 직접 정의할 수 있다. 레포에 파일을 두면 내장 템플릿보다 우선 적용된다.

```
.autodoc/
└── templates/
    └── android/
        └── README.md.tmpl   ← 이 파일이 있으면 내장 템플릿 대신 사용
```

템플릿 우선순위: **레포 커스텀** → **플랫폼 내장** → **generic 내장**

---

## Confluence 연동 (기획서 자동 동기화)

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

Confluence 페이지 내용을 가져와 PR diff와 비교 후 `spec/*.md`를 append 모드로 업데이트한다.

---

## 로컬 실행

```bash
export ANTHROPIC_API_KEY=sk-ant-...
export GITHUB_TOKEN=ghp_...
export GITHUB_REPOSITORY=owner/repo
export PR_NUMBER=42
export TARGET_REPO_PATH=/path/to/target-repo

./gradlew run
```

---

## 프로젝트 구조

```
src/main/kotlin/io/github/veronikapj/autodoc/
├── Main.kt                          # 진입점, 전체 파이프라인
├── config/
│   ├── AutoDocConfig.kt             # 설정 데이터 모델
│   └── ConfigLoader.kt             # .autodoc/config.yml 파싱
├── platform/
│   ├── PlatformConfig.kt           # 파일 패턴 → 에이전트 매핑
│   └── TemplateResolver.kt         # 3단계 템플릿 우선순위 로드
├── agent/
│   ├── OrchestratorAgent.kt        # PR diff 분석, 병렬 A2A 호출
│   └── specialist/
│       ├── ReadmeAgent.kt
│       ├── ArchDocAgent.kt
│       ├── ApiDocAgent.kt
│       ├── TestDocAgent.kt
│       ├── ChangelogAgent.kt
│       ├── SetupDocAgent.kt
│       └── SpecDocAgent.kt
├── a2a/
│   ├── DocAgentExecutor.kt         # A2A 서버 실행기
│   ├── A2AServerManager.kt         # 동적 포트 할당, 헬스체크
│   └── A2AClientManager.kt         # 병렬 연결 관리
└── tools/
    ├── GitHubTool.kt               # PR 조회, 문서 PR 생성
    └── ConfluenceTool.kt           # Confluence 페이지 조회
```

---

## 라이선스

MIT
