package io.github.veronikapj.autodoc.tools

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class ConfluenceToolTest {

    @Test
    fun `페이지 URL을 올바르게 구성한다`() {
        val tool = ConfluenceTool(
            baseUrl = "https://test.atlassian.net",
            token = "test-token",
        )
        val url = tool.buildPageUrl(123456L)
        assertEquals(
            "https://test.atlassian.net/wiki/rest/api/content/123456?expand=body.storage,version,title",
            url
        )
    }

    @Test
    fun `HTML을 Markdown으로 변환한다`() {
        val tool = ConfluenceTool("https://test.atlassian.net", "token")

        val html = "<h1>제목</h1><p>내용입니다.</p><ul><li>항목1</li><li>항목2</li></ul>"
        val markdown = tool.convertHtmlToMarkdown(html)

        assertTrue(markdown.contains("# 제목"))
        assertTrue(markdown.contains("내용입니다."))
        assertTrue(markdown.contains("- 항목1"))
        assertTrue(markdown.contains("- 항목2"))
    }

    @Test
    fun `HTML 엔티티를 올바르게 변환한다`() {
        val tool = ConfluenceTool("https://test.atlassian.net", "token")
        val html = "<p>A &amp; B &lt;C&gt;</p>"
        val markdown = tool.convertHtmlToMarkdown(html)
        assertTrue(markdown.contains("A & B <C>"))
    }
}
