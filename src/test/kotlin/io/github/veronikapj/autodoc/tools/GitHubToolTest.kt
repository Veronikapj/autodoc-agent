package io.github.veronikapj.autodoc.tools

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GitHubToolTest {
    @Test
    fun `feat prefix를 올바르게 추출한다`() {
        assertEquals("feat", extractPRType("feat: 로그인 기능 추가"))
    }

    @Test
    fun `fix prefix를 올바르게 추출한다`() {
        assertEquals("fix", extractPRType("fix: 크래시 수정"))
    }

    @Test
    fun `prefix 없는 제목은 null을 반환한다`() {
        assertNull(extractPRType("로그인 기능 추가"))
    }

    @Test
    fun `chore prefix를 올바르게 추출한다`() {
        assertEquals("chore", extractPRType("chore: 빌드 설정 변경"))
    }
}
