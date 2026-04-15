package io.github.veronikapj.autodoc.agent

import io.github.veronikapj.autodoc.agent.specialist.BaseDocAgent
import io.github.veronikapj.autodoc.platform.AgentType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncOrchestratorTest {

    @Test
    fun `NEEDED인 에이전트만 실행 대상에 포함된다`() {
        val triageResults = mapOf(
            AgentType.README to TriageResult.NEEDED,
            AgentType.CHANGELOG to TriageResult.SKIP,
            AgentType.ARCH_DOC to TriageResult.NEEDED,
        )
        val toRun = SyncOrchestrator.filterNeeded(triageResults)
        assertTrue(AgentType.README in toRun)
        assertTrue(AgentType.ARCH_DOC in toRun)
        assertFalse(AgentType.CHANGELOG in toRun)
    }

    @Test
    fun `sync 요청 메시지에 커밋 로그와 탐색 범위 힌트가 포함된다`() {
        val request = SyncOrchestrator.buildSyncRequest(
            agentType = AgentType.API_DOC,
            commitLog = "- feat: API 추가",
            searchScopeHint = "*Api.kt, *Controller.kt",
            existingContent = null,
        )
        assertTrue("feat: API 추가" in request)
        assertTrue("*Api.kt" in request)
        assertTrue("문서 없음" in request)
    }

    @Test
    fun `기존 문서가 있으면 업데이트 메시지가 포함된다`() {
        val request = SyncOrchestrator.buildSyncRequest(
            agentType = AgentType.README,
            commitLog = "- fix: 버그 수정",
            searchScopeHint = "Main.kt",
            existingContent = "# 기존 README",
        )
        assertTrue("존재함" in request)
    }

    // --- retryOnRateLimit ---

    @Test
    fun `rate limit 없이 성공하면 결과를 그대로 반환한다`() = runTest {
        val result = SyncOrchestrator.retryOnRateLimit(AgentType.README) {
            AgentType.README to "content"
        }
        assertEquals(AgentType.README to "content", result)
    }

    @Test
    fun `rate limit 예외 후 재시도에서 성공하면 결과를 반환한다`() = runTest {
        var attempt = 0
        val result = SyncOrchestrator.retryOnRateLimit(AgentType.README) {
            attempt++
            if (attempt == 1) throw RuntimeException("Status code: 429")
            AgentType.README to "retried content"
        }
        assertEquals(AgentType.README to "retried content", result)
        assertEquals(2, attempt)
    }

    @Test
    fun `rate limit 아닌 예외는 즉시 null로 처리된다`() = runTest {
        val result = SyncOrchestrator.retryOnRateLimit(AgentType.README) {
            throw RuntimeException("connection refused")
        }
        assertEquals(AgentType.README to null, result)
    }

    @Test
    fun `모든 재시도 소진 시 null을 반환한다`() = runTest {
        val result = SyncOrchestrator.retryOnRateLimit(AgentType.README) {
            throw RuntimeException("rate_limit_error")
        }
        assertEquals(AgentType.README to null, result)
    }

    // --- isRateLimitError ---

    @Test
    fun `429 포함 메시지는 rate limit 에러로 판단한다`() {
        assertTrue(BaseDocAgent.isRateLimitError(RuntimeException("Status code: 429")))
    }

    @Test
    fun `rate_limit 포함 메시지는 rate limit 에러로 판단한다`() {
        assertTrue(BaseDocAgent.isRateLimitError(RuntimeException("rate_limit_error occurred")))
    }

    @Test
    fun `일반 예외는 rate limit 에러가 아니다`() {
        assertFalse(BaseDocAgent.isRateLimitError(RuntimeException("connection refused")))
    }

    @Test
    fun `메시지 없는 예외는 rate limit 에러가 아니다`() {
        assertFalse(BaseDocAgent.isRateLimitError(RuntimeException()))
    }
}
