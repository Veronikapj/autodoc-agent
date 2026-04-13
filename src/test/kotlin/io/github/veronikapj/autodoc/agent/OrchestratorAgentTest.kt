package io.github.veronikapj.autodoc.agent

import io.github.veronikapj.autodoc.config.Platform
import io.github.veronikapj.autodoc.platform.AgentType
import io.github.veronikapj.autodoc.platform.PlatformConfig
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class OrchestratorAgentTest {

    @Test
    fun `Android 변경 파일에서 올바른 에이전트를 선별한다`() {
        val platformConfig = PlatformConfig(Platform.ANDROID)
        val changedFiles = listOf(
            "app/build.gradle.kts",
            "app/src/main/LoginActivity.kt",
        )
        val agents = platformConfig.resolveAgents(changedFiles)

        assertTrue(AgentType.ARCH_DOC in agents)
        assertTrue(AgentType.SETUP_DOC in agents)
        assertTrue(AgentType.README in agents)
    }

    @Test
    fun `feat PR 제목은 Changelog 에이전트가 필요하다`() {
        val platformConfig = PlatformConfig(Platform.ANDROID)
        assertTrue(platformConfig.needsChangelog("feat: 로그인 기능 추가"))
    }

    @Test
    fun `AgentType이 올바른 문서 경로로 변환된다`() {
        assertEquals("README.md", AgentType.README.toDocPath())
        assertEquals("docs/architecture.md", AgentType.ARCH_DOC.toDocPath())
        assertEquals("CHANGELOG.md", AgentType.CHANGELOG.toDocPath())
    }
}
