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
- **판단 로직**:
  - `*.kt` 파일 변경 + 모듈 추가/삭제 → ArchDocAgent 호출
  - `*Controller`, `*Router`, `*Api` 변경 → ApiDocAgent 호출
  - 진입점(`Application`, `MainActivity`) 변경 → ReadmeAgent 호출
  - 문서가 없으면 해당 에이전트에 "신규 생성" 플래그 전달

### ReadmeAgent
- **역할**: `README.md` 생성 또는 업데이트
- **도구**: `readFile`, `listFiles`, `loadTemplate("README.md.tmpl")`
- **입력**: PR diff + 기존 README (없으면 템플릿)
- **출력**: 업데이트된 `README.md`

### ArchDocAgent
- **역할**: `architecture.md` + `modules.md` 생성 또는 업데이트
- **도구**: `readFile`, `listFiles`, `codeSearch`, `generateMermaidGraph`
- **입력**: `build.gradle` 변경 + 모듈 구조
- **출력**: Mermaid 다이어그램이 포함된 `architecture.md`

### ApiDocAgent
- **역할**: `api.md` 생성 또는 업데이트
- **도구**: `readFile`, `codeSearch` (`@GET`, `@POST`, `@Api` 등 어노테이션 탐색)
- **입력**: API 관련 파일 diff
- **출력**: 엔드포인트 추가/수정된 `api.md`

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

템플릿은 `.autodoc/templates/`에 내장되며, 사용하는 레포에 같은 경로가 존재하면 그것을 우선 사용한다.

```
.autodoc/
└── templates/
    ├── README.md.tmpl          # 프로젝트 진입점
    ├── architecture.md.tmpl    # 레이어 구조 + Mermaid 모듈 그래프
    ├── modules.md.tmpl         # 모듈 역할 표 + 의존성 다이어그램
    └── api.md.tmpl             # 엔드포인트 목록 + 요청/응답 예시
```

### README.md 템플릿 구성
- 배지 (빌드 상태, 커버리지)
- Overview
- Screenshots
- Architecture (Mermaid 레이어 다이어그램)
- Tech Stack
- Getting Started
- Contributing

### architecture.md 템플릿 구성
- Module Graph (Mermaid)
- Layer Structure (UI / Domain / Data)
- Key Design Decisions (ADR)
- Data Flow (시퀀스 다이어그램)

### modules.md 템플릿 구성
- 모듈명 / 역할 / 핵심 클래스 / 의존 모듈 표
- 모듈 간 통신 다이어그램 (Mermaid)

### api.md 템플릿 구성
- Endpoints 목록
- Request / Response 예시
- Error Codes

> 다이어그램은 Mermaid로 통일 — GitHub 네이티브 렌더링 지원, 텍스트 기반이라 에이전트가 직접 생성/수정 가능

---

## 프로젝트 구조

```
autodoc-agent/
├── .autodoc/
│   └── templates/
│       ├── README.md.tmpl
│       ├── architecture.md.tmpl
│       ├── modules.md.tmpl
│       └── api.md.tmpl
├── src/main/kotlin/.../
│   ├── agent/
│   │   ├── OrchestratorAgent.kt
│   │   ├── ReadmeAgent.kt
│   │   ├── ArchDocAgent.kt
│   │   └── ApiDocAgent.kt
│   ├── a2a/                        # koog-practice에서 재사용
│   ├── tools/
│   │   ├── GitHubTool.kt           # PR diff, 브랜치, PR 생성
│   │   ├── ReadFileTool.kt
│   │   ├── ListFileTool.kt
│   │   └── MermaidTool.kt          # 모듈 의존성 → Mermaid 다이어그램
│   └── Main.kt
└── .github/
    └── workflows/
        └── autodoc.yml             # PR 머지 시 자동 실행
```

---

## 설계 결정 요약

| 항목 | 결정 | 이유 |
|------|------|------|
| 트리거 | GitHub Actions (PR 머지) | 별도 서버 불필요, 레포에 내장 |
| 에이전트 수 | 4개 (Orchestrator + 3 전문가) | 역할 분리로 품질 향상 |
| 통신 방식 | A2A HTTP | koog-practice 구조 재사용 |
| 다이어그램 | Mermaid | GitHub 네이티브, 텍스트 기반 |
| 템플릿 위치 | `.autodoc/templates/` | 레포별 커스터마이징 가능 |
| 출력 | 문서 업데이트 PR | 사람이 최종 검토 후 머지 |
