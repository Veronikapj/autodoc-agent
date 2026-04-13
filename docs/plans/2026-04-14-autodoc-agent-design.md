# autodoc-agent 설계 문서

> PR이 머지될 때 코드 변경을 감지하고 관련 문서를 자동으로 업데이트하는 AI 멀티에이전트 시스템

---

## 배경 및 문제 정의

Android 개발 현장에서 PR이 머지될 때 코드는 바뀌지만 문서(README, 아키텍처 문서, API 문서 등)는 아무도 업데이트하지 않는 문제가 반복된다. 이 프로젝트는 그 갭을 AI 에이전트로 자동화한다.

---

## 아키텍처 개요

```
PR 머지 이벤트 (GitHub Actions)
        ↓
  OrchestratorAgent
  - PR diff 분석
  - 어떤 문서가 영향받는지 판단
        ↓ 병렬 A2A 호출 (coroutineScope + async)
  ┌──────────────────────────────────┐
  │  ReadmeAgent                     │
  │  ArchDocAgent                    │
  │  ApiDocAgent                     │
  │  TestDocAgent                    │
  │  ChangelogAgent                  │
  │  SetupDocAgent                   │
  │  SpecDocAgent                    │
  └──────────────────────────────────┘
        ↓
  각 에이전트가 문서 초안 작성
        ↓
  문서 업데이트 PR 자동 생성
  → 사람이 검토 후 머지
```

---

## 에이전트 역할 설계

### OrchestratorAgent
- **역할**: PR diff 분석 후 어떤 에이전트를 호출할지 결정
- **도구**:
  - `fetchPRDiff(prNumber)` — 변경 파일 목록과 diff 가져오기
  - `listExistingDocs(repoPath)` — docs/ 폴더 구조 탐색
- **판단 로직 — 파일 변경 → 에이전트 매핑**:

  | 변경된 파일 패턴 | 호출 에이전트 | 액션 |
  |---|---|---|
  | `**/build.gradle.kts`, `settings.gradle.kts` | ArchDocAgent, SetupDocAgent | 모듈/의존성/SDK 변경 반영 |
  | `**/*Module*.kt`, `**/di/*.kt` | ArchDocAgent | DI 구조 변경 반영 |
  | `**/*Activity.kt`, `**/*Fragment.kt` | ArchDocAgent | 화면 흐름 변경 반영 |
  | `**/*Api.kt`, `**/*Service.kt`, `**/*Endpoint*.kt` | ApiDocAgent | 엔드포인트 추가/변경 반영 |
  | `**/*Repository.kt` | ApiDocAgent, ArchDocAgent | 데이터 레이어 변경 |
  | `**/Application.kt`, `**/MainActivity.kt` | ReadmeAgent | 앱 진입점 변경 |
  | `**/*Test.kt`, `**/*Fake*.kt`, `**/*Mock*.kt` | TestDocAgent | 테스트 시나리오 변경 반영 |
  | `docs/spec/*.md` | SpecDocAgent | 기획서 변경 (레포 모드) |
  | PR 라벨: `feat:`, `fix:`, `refactor:` | ChangelogAgent | 변경 이력 추가 |

- **스킵 조건**:
  - `*.png`, `*.xml` (strings.xml 제외) — 리소스 파일
  - `docs/**`, `*.md` — 문서 파일 자체 변경 (순환 방지)
  - `.github/**`, `*.yml` — CI/CD 설정
  - PR 라벨: `docs:`, `chore:` — ChangelogAgent 스킵

- **행동 규칙**:
  1. diff 전체를 먼저 분류한 후 에이전트를 선별한다
  2. 관련 없는 에이전트는 절대 호출하지 않는다 (비용/시간 최소화)
  3. 에이전트 호출 전 "이 PR이 어떤 문서에 영향을 주는지" 한 줄 요약을 생성한다
  4. 모든 에이전트 결과를 받은 후 PR 본문에 변경 이유를 요약해서 포함한다

---

### ReadmeAgent
- **역할**: `README.md` 생성 또는 업데이트
- **모드**: Overwrite
- **도구**: `readFile`, `listFiles`, `loadTemplate("README.md.tmpl")`
- **입력**: PR diff + 기존 README (없으면 템플릿)
- **출력**: 업데이트된 `README.md`
- **행동 규칙**:
  1. 기존 README가 있으면 반드시 전체를 읽고 시작한다
  2. 기존 내용의 톤/스타일을 유지한다 (임의로 구조를 바꾸지 않는다)
  3. PR diff에서 변경된 것만 반영한다 (관련 없는 섹션은 건드리지 않는다)
  4. 신규 생성 시 템플릿 기반으로 실제 값을 코드에서 추출해 채운다
     - 앱 이름: `AndroidManifest.xml`에서 추출
     - 최소 SDK: `build.gradle.kts`에서 추출
     - 주요 라이브러리: `dependencies` 블록에서 추출
  5. 확인할 수 없는 내용은 추측하지 않고 `TODO`로 표시한다

---

### ArchDocAgent
- **역할**: `architecture.md` + `modules.md` 생성 또는 업데이트
- **모드**: Overwrite
- **도구**: `readFile`, `listFiles`, `codeSearch`, `generateMermaidGraph`
- **입력**: `build.gradle` 변경 + 모듈 구조
- **출력**: Mermaid 다이어그램이 포함된 `architecture.md`, `modules.md`
- **행동 규칙**:
  1. `build.gradle.kts`와 `settings.gradle.kts`를 반드시 읽고 모듈 목록을 파악한다
  2. Mermaid 그래프는 실제 의존성 기반으로만 생성한다 (추측 금지)
  3. 모듈 표에는 실제 존재하는 클래스만 기재한다
  4. 기존 다이어그램이 있으면 추가/삭제된 모듈만 수정한다 (전체 재작성 금지)
  5. ADR이 필요한 변경(레이어 구조 변경, 새 아키텍처 패턴 도입)은 직접 쓰지 않고 PR 본문에 사람에게 알린다

---

### ApiDocAgent
- **역할**: `api.md` 생성 또는 업데이트
- **모드**: Overwrite
- **도구**: `readFile`, `codeSearch`
- **입력**: API 관련 파일 diff
- **출력**: 엔드포인트 추가/수정된 `api.md`
- **행동 규칙**:
  1. `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH` 어노테이션을 `codeSearch`로 전부 찾는다
  2. 요청/응답 모델은 실제 `data class`를 읽어서 필드를 파악한다
  3. 기존 문서에 있는 엔드포인트는 건드리지 않는다 (변경된 것만 업데이트)
  4. 삭제된 엔드포인트는 제거하지 않고 `[DEPRECATED]` 표시를 한다
  5. 인증 방식, 에러 코드는 기존 패턴을 참고해서 일관성을 유지한다

---

### TestDocAgent
- **역할**: 테스트 시나리오 문서 생성 또는 업데이트
- **모드**: Overwrite
- **도구**: `readFile`, `listFiles`, `codeSearch`
- **입력**: `**/*Test.kt` 변경 diff
- **출력**: `docs/testing.md` (테스트 시나리오 중심)
- **행동 규칙**:
  1. `@Test` 함수명과 내용을 분석해서 "무엇을 검증하는지" 자연어로 정리한다
  2. `given / when / then` 구조로 시나리오를 서술한다
  3. 기능별로 테스트 시나리오를 그룹핑한다
     - 예) 로그인 기능: 성공 케이스, 실패 케이스, 엣지 케이스
  4. 테스트가 없는 기능은 "미검증 시나리오"로 별도 표시한다
  5. 커버리지 수치보다 "어떤 상황을 테스트하는가"에 집중한다
  6. 신규 테스트 추가 시 해당 시나리오만 문서에 추가한다 (전체 재작성 금지)
  7. Mockk, Turbine, Robolectric 등 사용 중인 라이브러리를 탐지해서 반영한다

---

### ChangelogAgent
- **역할**: `CHANGELOG.md` 자동 업데이트
- **모드**: Overwrite
- **도구**: `fetchPRInfo(prNumber)`, `readFile`, `loadTemplate`
- **입력**: PR 제목, 라벨, 본문
- **출력**: `CHANGELOG.md`에 항목 추가
- **행동 규칙**:
  1. PR 제목의 prefix를 기준으로 분류한다
     - `feat:` → `### Added`
     - `fix:` → `### Fixed`
     - `refactor:` → `### Changed`
     - `docs:`, `chore:` → 스킵
  2. 현재 버전이 없으면 `## [Unreleased]` 섹션에 추가한다
  3. PR 제목을 그대로 쓰지 않고 사용자 관점으로 다듬어 쓴다
     - 예) `"feat: add UserRepository"` → `"사용자 데이터 로컬 캐싱 추가"`
  4. Keep a Changelog 형식을 유지한다

---

### SetupDocAgent
- **역할**: `docs/setup.md` 생성 또는 업데이트
- **모드**: Overwrite
- **도구**: `readFile`, `codeSearch`
- **입력**: `build.gradle.kts` 변경
- **출력**: `docs/setup.md` (SDK, 환경변수, 실행 방법)
- **행동 규칙**:
  1. `compileSdk`, `minSdk`, `targetSdk`를 `build.gradle.kts`에서 추출한다
  2. `local.properties.example`이 있으면 반드시 읽어서 환경변수 목록을 파악한다
  3. 실행 방법은 `gradlew` 명령어 기반으로 작성한다
  4. JDK 버전은 `toolchain` 설정에서 추출한다
  5. 설정 방법이 불명확한 항목은 `TODO`로 표시하고 PR 본문에 알린다

---

### SpecDocAgent
- **역할**: 기획서 변경을 감지하고 문서에 반영
- **모드**: Append (변경 이력 누적)
- **두 가지 소스 모드** (`.autodoc/config.yml`로 설정):

  **레포 모드** (`source: markdown`)
  - `docs/spec/*.md` 변경 감지
  - 변경된 기획 내용을 읽고 관련 문서 업데이트
  - 트리거: 기획서 파일이 PR에 포함된 경우

  **Confluence 모드** (`source: confluence`)
  - Confluence API로 특정 페이지 변경 여부 확인
  - 변경된 페이지 내용을 `docs/spec/`에 마크다운으로 동기화
  - 트리거: PR 머지 시 + 스케줄 (매일 1회)

- **도구**: `readFile`, `listFiles`, `fetchConfluencePage(pageId)`, `diffSpecContent()`
- **출력 형식 (Append 모드)**:

  ```markdown
  ## 현재 스펙
  ...최신 내용...

  ## 변경 이력
  ### 2026-04-14 | PR #42
  - 소셜 로그인 카카오 추가
  - 자동 로그인 만료 기간 30일 → 7일로 변경
  - 이유: 보안 정책 강화

  ### 2026-03-20 | PR #31
  - 이메일 로그인 추가
  ```

---

## 플랫폼 설정

autodoc-agent는 플랫폼별로 최적화된 템플릿과 파일 패턴을 사용한다. Android 특화 기능은 옵셔널이며, 플랫폼 설정에 따라 자동으로 적용된다.

### 지원 플랫폼

| 플랫폼 | 특화 에이전트/도구 | 특화 문서 |
|---|---|---|
| `android` | Gradle 모듈 분석, Mermaid 모듈 그래프 | architecture.md, modules.md |
| `ios` | Swift Package 분석 | architecture.md |
| `backend` | REST/GraphQL 어노테이션 탐색, DB 스키마 분석 | api.md, database.md |
| `frontend` | 컴포넌트 구조 분석 | components.md, storybook.md |
| `generic` | 공통 기본값 (플랫폼 미지정 시 폴백) | README.md, CHANGELOG.md |

### 템플릿 우선순위

```
1순위: 타겟 레포의 .autodoc/templates/{platform}/   ← 팀 커스터마이징
2순위: autodoc-agent 내장 templates/{platform}/     ← 플랫폼 기본값
3순위: autodoc-agent 내장 templates/generic/        ← 공통 폴백
```

### 플랫폼별 파일 패턴 → 에이전트 매핑

```yaml
patterns:
  android:
    - pattern: "**/build.gradle.kts, settings.gradle.kts"
      agents: [ArchDocAgent, SetupDocAgent]
    - pattern: "**/*Activity.kt, **/*Fragment.kt, **/di/*.kt"
      agents: [ArchDocAgent]
    - pattern: "**/*Api.kt, **/*Service.kt, **/*Endpoint*.kt"
      agents: [ApiDocAgent]
    - pattern: "**/*Test.kt, **/*Fake*.kt, **/*Mock*.kt"
      agents: [TestDocAgent]

  ios:
    - pattern: "**/*.swift"
      agents: [ArchDocAgent]
    - pattern: "**/Package.swift"
      agents: [SetupDocAgent]
    - pattern: "**/*Tests.swift"
      agents: [TestDocAgent]

  backend:
    - pattern: "**/*Controller.kt, **/*Controller.java"
      agents: [ApiDocAgent]
    - pattern: "**/schema/*.sql, **/*.migration"
      agents: [DatabaseDocAgent]
    - pattern: "**/*Test.kt, **/*Test.java"
      agents: [TestDocAgent]

  frontend:
    - pattern: "**/*.tsx, **/*.vue, **/*.svelte"
      agents: [ArchDocAgent]
    - pattern: "**/*.stories.tsx"
      agents: [StorybookDocAgent]
    - pattern: "**/*.test.tsx, **/*.spec.ts"
      agents: [TestDocAgent]

  generic:
    - pattern: "** (문서 파일 제외 전체)"
      agents: [ReadmeAgent, ChangelogAgent]
```

---

## 문서 모드 설정

문서별로 Overwrite / Append 모드를 `.autodoc/config.yml`로 지정한다.

```yaml
# .autodoc/config.yml
platform: android    # android | ios | backend | frontend | generic

documents:
  README.md:        overwrite   # 항상 최신 상태 유지
  architecture.md:  overwrite
  modules.md:       overwrite
  api.md:           overwrite
  setup.md:         overwrite
  testing.md:       overwrite
  CHANGELOG.md:     overwrite   # Keep a Changelog 형식으로 누적
  spec/*.md:        append      # 기획서는 변경 이력 추적

spec:
  # 사이드 프로젝트
  source: markdown
  path: docs/spec/

  # 회사 레포 (Confluence)
  # source: confluence
  # base_url: https://yourcompany.atlassian.net
  # space_key: ANDROID
  # page_ids:
  #   - 123456
  #   - 789012
```

---

## 전체 데이터 흐름

```
1. 트리거
   GitHub Actions (on: pull_request merged)
   → autodoc-agent 실행 (PR 번호 전달)

2. OrchestratorAgent
   → fetchPRDiff(prNumber)
   → listExistingDocs()
   → 영향받는 에이전트 선별
   → 병렬 A2A 호출

3. 각 전문 에이전트 (병렬 실행)
   → 기존 문서 읽기 or 템플릿 로드
   → 코드 읽기 + diff 분석
   → 문서 초안 생성 (Mermaid 다이어그램 포함)
   → 결과 반환

4. OrchestratorAgent
   → 결과 수집 (awaitAll)
   → 변경된 파일 목록 정리

5. PR 생성
   → 새 브랜치 생성: docs/auto-update-pr-{prNumber}
   → 문서 파일 커밋
   → GitHub PR 오픈
      제목: "docs: PR #{prNumber} 변경사항 문서 반영"
      본문: 어떤 에이전트가 어떤 문서를 왜 변경했는지 요약
```

---

## 문서 존재 여부 분기

```
OrchestratorAgent
  ↓
  문서 탐색 (listFiles)
  ↓
  ┌──────────────────────┬──────────────────────┐
  │ 문서 있음            │ 문서 없음             │
  │ → 기존 문서 읽기     │ → 템플릿 로드         │
  │ → diff 반영 업데이트 │ → 코드 구조 분석 후  │
  │                      │   새 문서 초안 생성   │
  └──────────────────────┴──────────────────────┘
  ↓
  PR 생성
```

---

## 템플릿 구조

템플릿은 플랫폼별로 분리되며 우선순위에 따라 로드된다.

> 다이어그램은 Mermaid로 통일 — GitHub 네이티브 렌더링 지원, 텍스트 기반이라 에이전트가 직접 생성/수정 가능

---

### generic/README.md.tmpl
```markdown
# {{PROJECT_NAME}}

{{PROJECT_DESCRIPTION}}

## Overview
{{OVERVIEW}}

## Getting Started
→ [setup.md](docs/setup.md) 참고

## Contributing
- 브랜치 전략: `feature/`, `fix/`, `docs/`
- PR 머지 전 리뷰 최소 1명 승인 필요
- 커밋 컨벤션: `feat:` `fix:` `docs:` `chore:`
```

### generic/CHANGELOG.md.tmpl
```markdown
# Changelog
Keep a Changelog 형식을 따릅니다.

## [Unreleased]

### Added

### Fixed

### Changed
```

### generic/setup.md.tmpl
```markdown
# 개발 환경 설정

## 요구사항
| 항목 | 버전 |
|------|------|
| {{RUNTIME_NAME}} | {{RUNTIME_VERSION}} |

## 환경변수
| 변수명 | 설명 | 필수 |
|--------|------|------|
| {{ENV_VAR_NAME}} | {{ENV_VAR_DESC}} | ✅ |

## 실행 방법
\`\`\`bash
{{RUN_COMMAND}}
\`\`\`
```

### generic/spec.md.tmpl
```markdown
# {{FEATURE_NAME}} 기획서

## 현재 스펙
{{SPEC_CONTENT}}

## 변경 이력
### {{DATE}} | PR #{{PR_NUMBER}}
- {{CHANGE_DESCRIPTION}}
```

---

### android/README.md.tmpl
```markdown
# {{APP_NAME}}

![Build Status](https://github.com/{{REPO}}/actions/workflows/build.yml/badge.svg)
[![API](https://img.shields.io/badge/API-{{MIN_SDK}}%2B-brightgreen.svg)](https://android-arsenal.com/api?level={{MIN_SDK}})

{{APP_DESCRIPTION}}

## 스크린샷
| 홈 | 상세 | 설정 |
|---|---|---|
| TODO | TODO | TODO |

## 아키텍처
→ [architecture.md](docs/architecture.md) 참고

\`\`\`mermaid
graph TD
  {{MERMAID_LAYER_GRAPH}}
\`\`\`

## 기술 스택
| 분류 | 라이브러리 |
|------|-----------|
| UI | {{UI_LIBRARIES}} |
| DI | {{DI_LIBRARY}} |
| Network | {{NETWORK_LIBRARY}} |
| DB | {{DB_LIBRARY}} |

## 요구사항
- Android {{MIN_SDK}}+ ({{MIN_SDK_NAME}})
- compileSdk {{COMPILE_SDK}}
```

### android/architecture.md.tmpl
```markdown
# 아키텍처

## 레이어 구조
\`\`\`mermaid
graph TD
  subgraph Presentation
    UI[UI Layer\nActivity / Fragment / Composable]
    VM[ViewModel]
  end
  subgraph Domain
    UC[UseCase]
    REPO_IF[Repository Interface]
  end
  subgraph Data
    REPO[Repository Impl]
    DS_LOCAL[Local DataSource\nRoom]
    DS_REMOTE[Remote DataSource\nRetrofit]
  end
  UI --> VM --> UC --> REPO_IF
  REPO --> REPO_IF
  REPO --> DS_LOCAL
  REPO --> DS_REMOTE
\`\`\`

## 모듈 의존성 그래프
\`\`\`mermaid
{{MERMAID_MODULE_GRAPH}}
\`\`\`

## 주요 설계 결정
| 결정 | 이유 | 날짜 |
|------|------|------|
| {{DECISION}} | {{REASON}} | {{DATE}} |
```

### android/modules.md.tmpl
```markdown
# 모듈 구조

## 모듈 목록
| 모듈명 | 유형 | 역할 | 핵심 클래스 |
|--------|------|------|------------|
| {{MODULE_NAME}} | app/feature/core/library | {{MODULE_ROLE}} | {{KEY_CLASS}} |

## 의존성 규칙
- `app` → `feature:*` → `core:*` → `library:*`
- 같은 레벨 모듈 간 직접 의존 금지

## 모듈 간 통신
\`\`\`mermaid
{{MERMAID_MODULE_COMM_GRAPH}}
\`\`\`
```

### android/testing.md.tmpl
```markdown
# 테스트 시나리오

## {{FEATURE_NAME}}

### 단위 테스트 (Unit Test)
| 시나리오 | Given | When | Then | 상태 |
|----------|-------|------|------|------|
| {{SCENARIO}} | {{GIVEN}} | {{WHEN}} | {{THEN}} | ✅/❌ |

### UI 테스트 (Instrumented Test)
| 시나리오 | 사전 조건 | 액션 | 기대 결과 | 상태 |
|----------|-----------|------|-----------|------|
| {{SCENARIO}} | {{PRECONDITION}} | {{ACTION}} | {{EXPECTED}} | ✅/❌ |

## 미검증 시나리오
- {{UNVERIFIED_SCENARIO}}

## 테스트 환경
| 항목 | 라이브러리 |
|------|-----------|
| Unit | JUnit5, MockK, Turbine |
| UI | Espresso / Compose Test |
```

---

### ios/README.md.tmpl
```markdown
# {{APP_NAME}}

![Xcode](https://img.shields.io/badge/Xcode-{{XCODE_VERSION}}-blue)
![Swift](https://img.shields.io/badge/Swift-{{SWIFT_VERSION}}-orange)
![iOS](https://img.shields.io/badge/iOS-{{MIN_IOS}}%2B-lightgrey)

{{APP_DESCRIPTION}}

## 요구사항
- Xcode {{XCODE_VERSION}}+
- Swift {{SWIFT_VERSION}}
- iOS {{MIN_IOS}}+

## 기술 스택
| 분류 | 라이브러리 |
|------|-----------|
| UI | {{UI_FRAMEWORK}} |
| 의존성 관리 | {{PACKAGE_MANAGER}} |
| 네트워크 | {{NETWORK_LIBRARY}} |
```

### ios/architecture.md.tmpl
```markdown
# 아키텍처

## 패턴
{{ARCHITECTURE_PATTERN}} (MVVM / TCA / VIPER)

## 레이어 구조
\`\`\`mermaid
graph TD
  View --> ViewModel
  ViewModel --> UseCase
  UseCase --> Repository
  Repository --> RemoteDataSource
  Repository --> LocalDataSource
\`\`\`

## Swift Package 의존성
| 패키지 | 용도 | 버전 |
|--------|------|------|
| {{PACKAGE_NAME}} | {{PURPOSE}} | {{VERSION}} |
```

### ios/testing.md.tmpl
```markdown
# 테스트 시나리오

## {{FEATURE_NAME}}

### 단위 테스트 (XCTest)
| 시나리오 | Given | When | Then | 상태 |
|----------|-------|------|------|------|
| {{SCENARIO}} | {{GIVEN}} | {{WHEN}} | {{THEN}} | ✅/❌ |

### UI 테스트 (XCUITest)
| 시나리오 | 사전 조건 | 액션 | 기대 결과 | 상태 |
|----------|-----------|------|-----------|------|
| {{SCENARIO}} | {{PRECONDITION}} | {{ACTION}} | {{EXPECTED}} | ✅/❌ |

## 미검증 시나리오
- {{UNVERIFIED_SCENARIO}}
```

---

### backend/README.md.tmpl
```markdown
# {{SERVICE_NAME}}

{{SERVICE_DESCRIPTION}}

## 요구사항
- JDK {{JAVA_VERSION}} / Node {{NODE_VERSION}}
- Docker {{DOCKER_VERSION}}

## 실행
\`\`\`bash
{{RUN_COMMAND}}
\`\`\`

## API 문서
→ [api.md](docs/api.md) 참고
→ Swagger: `http://localhost:{{PORT}}/swagger-ui`
```

### backend/api.md.tmpl
```markdown
# API 문서

## Base URL
`{{BASE_URL}}`

## 인증
{{AUTH_METHOD}} (Bearer Token / API Key / OAuth2)

## 엔드포인트

### {{RESOURCE_NAME}}
| 메서드 | 경로 | 설명 | 인증 필요 |
|--------|------|------|-----------|
| {{METHOD}} | {{PATH}} | {{DESC}} | ✅/❌ |

#### 요청
\`\`\`json
{{REQUEST_EXAMPLE}}
\`\`\`

#### 응답
\`\`\`json
{{RESPONSE_EXAMPLE}}
\`\`\`

## 에러 코드
| 코드 | 의미 | 발생 상황 |
|------|------|-----------|
| 400 | Bad Request | {{CAUSE}} |
| 401 | Unauthorized | 인증 토큰 없음/만료 |
| 404 | Not Found | 리소스 없음 |
| 500 | Server Error | 서버 내부 오류 |
```

### backend/architecture.md.tmpl
```markdown
# 아키텍처

## 시스템 구성
\`\`\`mermaid
graph TD
  Client --> API[API Server\n:{{PORT}}]
  API --> DB[(Database\n{{DB_NAME}})]
  API --> Cache[(Redis Cache)]
  API --> MQ[Message Queue]
\`\`\`

## 레이어 구조
Controller → Service → Repository → Database

## 주요 설계 결정
| 결정 | 이유 | 날짜 |
|------|------|------|
| {{DECISION}} | {{REASON}} | {{DATE}} |
```

### backend/database.md.tmpl
```markdown
# 데이터베이스

## ERD
\`\`\`mermaid
erDiagram
  {{ERD_CONTENT}}
\`\`\`

## 테이블 목록
| 테이블명 | 설명 | 주요 컬럼 |
|----------|------|-----------|
| {{TABLE_NAME}} | {{DESC}} | {{COLUMNS}} |

## 마이그레이션 이력
| 버전 | 변경 내용 | 날짜 |
|------|-----------|------|
| {{VERSION}} | {{CHANGE}} | {{DATE}} |
```

---

### frontend/README.md.tmpl
```markdown
# {{PROJECT_NAME}}

{{PROJECT_DESCRIPTION}}

## 요구사항
- Node {{NODE_VERSION}}
- {{PACKAGE_MANAGER}} {{PM_VERSION}}

## 실행
\`\`\`bash
{{INSTALL_COMMAND}}
{{DEV_COMMAND}}
\`\`\`

## 기술 스택
| 분류 | 라이브러리 |
|------|-----------|
| Framework | {{FRAMEWORK}} |
| 상태관리 | {{STATE_LIBRARY}} |
| 스타일 | {{STYLE_LIBRARY}} |
```

### frontend/components.md.tmpl
```markdown
# 컴포넌트 구조

## 컴포넌트 계층
\`\`\`mermaid
graph TD
  {{COMPONENT_HIERARCHY}}
\`\`\`

## 컴포넌트 목록
| 컴포넌트 | 경로 | 역할 | Props |
|----------|------|------|-------|
| {{COMPONENT_NAME}} | {{PATH}} | {{ROLE}} | {{PROPS}} |
```

### frontend/storybook.md.tmpl
```markdown
# 컴포넌트 문서

## {{COMPONENT_NAME}}

### 설명
{{DESCRIPTION}}

### Props
| Prop | 타입 | 기본값 | 필수 | 설명 |
|------|------|--------|------|------|
| {{PROP_NAME}} | {{TYPE}} | {{DEFAULT}} | ✅/❌ | {{DESC}} |

### 사용 예시
\`\`\`tsx
{{USAGE_EXAMPLE}}
\`\`\`
```

---

## 프로젝트 구조

```
autodoc-agent/
├── action.yml                        # GitHub Marketplace 등록 진입점
├── Dockerfile                        # Kotlin 앱 Docker 패키징
├── .autodoc/
│   ├── config.yml
│   └── templates/
│       ├── generic/
│       │   ├── README.md.tmpl
│       │   ├── CHANGELOG.md.tmpl
│       │   ├── setup.md.tmpl
│       │   └── spec.md.tmpl
│       ├── android/
│       │   ├── README.md.tmpl
│       │   ├── architecture.md.tmpl
│       │   ├── modules.md.tmpl
│       │   └── testing.md.tmpl
│       ├── ios/
│       │   ├── README.md.tmpl
│       │   ├── architecture.md.tmpl
│       │   └── testing.md.tmpl
│       ├── backend/
│       │   ├── README.md.tmpl
│       │   ├── api.md.tmpl
│       │   ├── architecture.md.tmpl
│       │   └── database.md.tmpl
│       └── frontend/
│           ├── README.md.tmpl
│           ├── components.md.tmpl
│           └── storybook.md.tmpl
├── src/main/kotlin/.../
│   ├── agent/
│   │   ├── OrchestratorAgent.kt
│   │   ├── ReadmeAgent.kt
│   │   ├── ArchDocAgent.kt
│   │   ├── ApiDocAgent.kt
│   │   ├── TestDocAgent.kt
│   │   ├── ChangelogAgent.kt
│   │   ├── SetupDocAgent.kt
│   │   └── SpecDocAgent.kt
│   ├── a2a/                          # koog-practice에서 재사용
│   ├── platform/
│   │   ├── PlatformConfig.kt         # 플랫폼별 패턴/에이전트 매핑
│   │   └── TemplateResolver.kt       # 템플릿 우선순위 로드
│   ├── tools/
│   │   ├── GitHubTool.kt             # PR diff, 브랜치, PR 생성
│   │   ├── ConfluenceTool.kt         # Confluence API 연동
│   │   ├── ReadFileTool.kt
│   │   ├── ListFileTool.kt
│   │   └── MermaidTool.kt            # 모듈 의존성 → Mermaid 다이어그램
│   └── Main.kt
└── .github/
    └── workflows/
        ├── autodoc.yml               # PR 머지 시 자동 실행 (자체 테스트용)
        └── publish.yml               # 태그 시 Marketplace 배포
```

---

## 설계 결정 요약

| 항목 | 결정 | 이유 |
|------|------|------|
| 트리거 | GitHub Actions (PR 머지) | 별도 서버 불필요, 레포에 내장 |
| 에이전트 수 | 8개 (Orchestrator + 7 전문가) | 역할 분리로 품질 향상 |
| 통신 방식 | A2A HTTP | koog-practice 구조 재사용 |
| 다이어그램 | Mermaid | GitHub 네이티브, 텍스트 기반 |
| 문서 모드 | Overwrite / Append (문서별 설정) | 현황 문서 vs 이력 추적 문서 분리 |
| 플랫폼 지원 | generic / android / ios / backend / frontend | Android 특화는 옵셔널, 플랫폼별 확장 가능 |
| 템플릿 위치 | `.autodoc/templates/{platform}/` | 플랫폼별 분리 + 레포 커스터마이징 가능 |
| 기획서 소스 | Markdown / Confluence (설정 기반) | 사이드 프로젝트 / 회사 레포 대응 |
| 배포 방식 | Docker + GitHub Marketplace Action | 외부 레포에서 `uses: Veronikapj/autodoc-agent@v1`로 사용 |
| 출력 | 문서 업데이트 PR | 사람이 최종 검토 후 머지 |
