package io.github.veronikapj.autodoc.agent

import io.github.veronikapj.autodoc.platform.AgentType
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
}
