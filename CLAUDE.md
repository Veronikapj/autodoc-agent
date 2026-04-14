# autodoc-agent 개발 가이드

## 프로젝트 개요

Kotlin + [Koog 0.8.0](https://github.com/JetBrains/koog) + A2A Protocol 기반의 GitHub Actions 자동 문서화 에이전트.
PR 머지 시 코드 변경을 분석해 영향받는 문서 초안을 작성하고 리뷰용 PR을 생성한다.

## 빌드 & 테스트

```bash
./gradlew test          # 전체 테스트
./gradlew shadowJar     # 배포용 fat JAR 생성
```

## Kotlin 코딩 컨벤션

이 프로젝트에서 적용하는 규칙. [Kotlin 공식 컨벤션](https://kotlinlang.org/docs/coding-conventions.html) 기반.

### 세미콜론 금지

```kotlin
// ❌
inDocuments = false; inSpec = false; inModel = false

// ✅
inDocuments = false
inSpec = false
inModel = false
```

### 최대 줄 길이 120자

체이닝 메서드는 120자 초과 시 개행한다. 들여쓰기는 계속 4칸.

```kotlin
// ❌
platform = trimmed.substringAfter("platform:").trim().uppercase().let { runCatching { Platform.valueOf(it) }.getOrDefault(Platform.GENERIC) }

// ✅
platform = trimmed.substringAfter("platform:").trim()
    .uppercase()
    .let { runCatching { Platform.valueOf(it) }.getOrDefault(Platform.GENERIC) }
```

### 단일 표현식 함수

`return`이 하나뿐인 함수는 `=` 형태로 쓴다.

```kotlin
// ❌
fun needsChangelog(prTitle: String): Boolean {
    return changelogSkipPrefixes.none { prTitle.startsWith(it) }
}

// ✅
fun needsChangelog(prTitle: String): Boolean =
    changelogSkipPrefixes.none { prTitle.startsWith(it) }
```

### 로거는 companion object에 선언

```kotlin
// ❌
class Foo {
    private val log = LoggerFactory.getLogger(Foo::class.java)
}

// ✅
class Foo {
    companion object {
        private val log = LoggerFactory.getLogger(Foo::class.java)
    }
}
```

### `javaClass` 사용

멤버 함수 내에서 자기 자신의 클래스를 참조할 때.

```kotlin
// ❌
LoggerFactory.getLogger(MyClass::class.java)

// ✅ (멤버 함수 내부에서)
LoggerFactory.getLogger(javaClass)
```

## 아키텍처 규칙

- **OrchestratorAgent**: PR diff 분석 + 필요한 에이전트 선별 + 병렬 A2A 호출만 담당
- **SpecialistDocAgent**: 각 문서 타입별 1개. `process(request)` 하나만 구현
- **A2AServerManager**: 동적 포트 할당 → 헬스체크 후 준비 완료 신호
- **println 금지**: 모든 로그는 SLF4J (`log.info`, `log.warn`, `log.error`)

## 의존성 주의

- Jackson은 `2.15.2`로 고정 (`resolutionStrategy.force`). Koog가 2.21.x를 끌어오면 `github-api`와 충돌 발생.
- Koog API는 `@ExperimentalAgentsApi` 어노테이션 필요 — 파일 상단 `@file:OptIn` 사용.
