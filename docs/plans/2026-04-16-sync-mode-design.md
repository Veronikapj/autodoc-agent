# Sync 모드 설계

## 배경

PR 머지 기반 자동 문서화와 별도로, 전체 문서를 한 번에 초기화·동기화하는 수단이 필요하다.
사용 시나리오:
- 프로젝트 최초 설정 시 모든 문서 일괄 생성
- 템플릿 변경 후 전체 문서 재반영
- 오랜 기간 누적된 문서 drift 일괄 해소

## 목표

- GitHub Actions UI에서 버튼 클릭 한 번으로 실행
- 이미 최신인 문서는 건너뛰어 토큰 비용 최소화
- 업데이트가 필요한 문서만 풀 에이전트 실행
- 결과는 기존 PR 흐름과 동일하게 리뷰용 PR로 생성

## 아키텍처

### 전체 흐름

```
[GitHub Actions UI] workflow_dispatch
        ↓
  autodoc-sync.yml
  ./gradlew run --args="--sync"
        ↓
  SyncOrchestrator
        ↓
  Phase 1: Triage
  Phase 2: Full Update (필요한 것만)
        ↓
  docs/auto-sync 브랜치 → PR 생성
```

### Phase 1 — Triage (저비용)

- 모델: Haiku (빠르고 저렴)
- 입력: 최근 커밋 로그 + 기존 문서 내용
- 코드 탐색: 없음
- 출력: 각 AgentType별 `NEEDED` / `SKIP`
- 대상: 전체 AgentType (플랫폼 무관)

### Phase 2 — Full Update

- Phase 1에서 `NEEDED` 판정된 것 + 문서가 아예 없는 것만 실행
- 기존 specialist 에이전트 재사용
- 에이전트별 탐색 범위 제한으로 추가 비용 절감

| 에이전트 | 탐색 범위 힌트 |
|---------|--------------|
| README | 진입점, 설정파일 |
| ARCH_DOC | 전체 구조 |
| API_DOC | `*Api.kt`, `*Controller.kt` |
| TEST_DOC | `*Test.kt`, `*Spec.kt` |
| CHANGELOG | 커밋 로그 |
| SETUP_DOC | `build.gradle*`, `Dockerfile` |
| SPEC_DOC | `docs/spec/` |

## 변경/추가 파일

| 파일 | 유형 | 내용 |
|------|------|------|
| `Main.kt` | 수정 | `--sync` 분기 추가 |
| `agent/SyncOrchestrator.kt` | 신규 | Phase 1 + Phase 2 orchestration |
| `agent/TriageAgent.kt` | 신규 | Haiku 기반 NEEDED/SKIP 판정 |
| `tools/GitHubTool.kt` | 수정 | 최근 커밋 로그 조회 메서드 추가 |
| `agent/specialist/BaseDocAgent.kt` | 수정 | 에이전트별 탐색 범위 힌트 프로퍼티 추가 |
| `.github/workflows/autodoc-sync.yml` | 신규 | workflow_dispatch 트리거 |

## PR vs Sync 비교

| | PR 기반 | Sync |
|---|---|---|
| 트리거 | PR 머지 | 수동 (workflow_dispatch) |
| 컨텍스트 | 변경 파일 목록 | 최근 커밋 로그 + 전체 코드 탐색 |
| 대상 에이전트 | 파일 패턴 매칭 | 전체 AgentType (Triage 필터링) |
| 브랜치명 | `docs/auto-update-pr-N` | `docs/auto-sync` |
| 비용 최적화 | 해당 없음 | Phase 1 Triage로 불필요한 에이전트 제거 |
