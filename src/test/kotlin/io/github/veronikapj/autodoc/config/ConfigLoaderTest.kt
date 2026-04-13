package io.github.veronikapj.autodoc.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ConfigLoaderTest {

    @Test
    fun `플랫폼 설정을 올바르게 파싱한다`() {
        val yaml = """
            platform: android
            documents:
              README.md: overwrite
              spec/*.md: append
        """.trimIndent()

        val config = ConfigLoader.parseYaml(yaml)

        assertEquals(Platform.ANDROID, config.platform)
        assertEquals(DocumentMode.OVERWRITE, config.documents["README.md"])
        assertEquals(DocumentMode.APPEND, config.documents["spec/*.md"])
    }

    @Test
    fun `플랫폼 미지정 시 generic으로 폴백한다`() {
        val yaml = "documents:\n  README.md: overwrite"
        val config = ConfigLoader.parseYaml(yaml)
        assertEquals(Platform.GENERIC, config.platform)
    }

    @Test
    fun `config 파일이 없으면 기본값을 반환한다`() {
        val config = ConfigLoader.load("/nonexistent/path")
        assertEquals(Platform.GENERIC, config.platform)
        assertTrue(config.documents.isEmpty())
    }

    @Test
    fun `Confluence 설정을 파싱한다`() {
        val yaml = """
            platform: android
            spec:
              source: confluence
              base_url: https://test.atlassian.net
              space_key: ANDROID
              page_ids:
                - 123456
                - 789012
        """.trimIndent()

        val config = ConfigLoader.parseYaml(yaml)
        assertEquals(SpecSource.CONFLUENCE, config.spec.source)
        assertEquals("https://test.atlassian.net", config.spec.baseUrl)
        assertEquals(listOf(123456L, 789012L), config.spec.pageIds)
    }
}
