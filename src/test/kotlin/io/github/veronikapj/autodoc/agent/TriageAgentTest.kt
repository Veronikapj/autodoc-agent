package io.github.veronikapj.autodoc.agent

import io.github.veronikapj.autodoc.platform.AgentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TriageAgentTest {

    @Test
    fun `NEEDED 응답을 올바르게 파싱한다`() {
        val result = TriageAgent.parseResponse("README: NEEDED\nCHANGELOG: SKIP")
        assertEquals(TriageResult.NEEDED, result[AgentType.README])
        assertEquals(TriageResult.SKIP, result[AgentType.CHANGELOG])
    }

    @Test
    fun `파싱 실패 시 NEEDED로 폴백한다`() {
        val result = TriageAgent.parseResponse("README: GARBAGE")
        assertEquals(TriageResult.NEEDED, result[AgentType.README])
    }

    @Test
    fun `응답에 없는 AgentType은 NEEDED로 폴백한다`() {
        val result = TriageAgent.parseResponse("")
        AgentType.entries.forEach {
            assertEquals(TriageResult.NEEDED, result[it])
        }
    }
}
