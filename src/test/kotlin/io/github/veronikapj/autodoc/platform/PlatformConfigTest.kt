package io.github.veronikapj.autodoc.platform

import io.github.veronikapj.autodoc.config.Platform
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PlatformConfigTest {

    @Test
    fun `Android build gradle 변경은 ArchDocAgent와 SetupDocAgent를 반환한다`() {
        val config = PlatformConfig(Platform.ANDROID)
        val agents = config.resolveAgents(listOf("app/build.gradle.kts"))
        assertTrue(AgentType.ARCH_DOC in agents)
        assertTrue(AgentType.SETUP_DOC in agents)
    }

    @Test
    fun `테스트 파일 변경은 TestDocAgent를 반환한다`() {
        val config = PlatformConfig(Platform.ANDROID)
        val agents = config.resolveAgents(listOf("app/src/test/LoginViewModelTest.kt"))
        assertTrue(AgentType.TEST_DOC in agents)
    }

    @Test
    fun `스킵 조건 파일은 빈 목록을 반환한다`() {
        val config = PlatformConfig(Platform.ANDROID)
        val agents = config.resolveAgents(listOf("app/src/main/res/drawable/icon.png"))
        assertTrue(agents.isEmpty())
    }

    @Test
    fun `ChangelogAgent는 PR 라벨로 판단한다`() {
        val config = PlatformConfig(Platform.ANDROID)
        assertTrue(config.needsChangelog("feat: 로그인 기능 추가"))
        assertFalse(config.needsChangelog("chore: 빌드 설정 변경"))
        assertFalse(config.needsChangelog("docs: 문서 업데이트"))
    }

    @Test
    fun `여러 파일 변경 시 중복 없이 에이전트를 합산한다`() {
        val config = PlatformConfig(Platform.ANDROID)
        val agents = config.resolveAgents(listOf(
            "app/build.gradle.kts",
            "feature/login/LoginActivity.kt",
            "feature/login/LoginViewModelTest.kt",
        ))
        assertTrue(AgentType.ARCH_DOC in agents)
        assertTrue(AgentType.SETUP_DOC in agents)
        assertTrue(AgentType.TEST_DOC in agents)
        assertTrue(AgentType.README in agents)
    }
}
