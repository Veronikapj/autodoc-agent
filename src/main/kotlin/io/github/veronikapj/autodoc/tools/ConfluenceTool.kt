package io.github.veronikapj.autodoc.tools

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

data class ConfluencePage(
    val id: String,
    val title: String,
    val content: String,
)

class ConfluenceTool(
    private val baseUrl: String,
    private val token: String,
) {
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }

    fun buildPageUrl(pageId: Long): String =
        "$baseUrl/wiki/rest/api/content/$pageId?expand=body.storage,version,title"

    suspend fun fetchPage(pageId: Long): ConfluencePage {
        val response: String = client.get(buildPageUrl(pageId)) {
            header("Authorization", "Bearer $token")
            header("Accept", "application/json")
        }.bodyAsText()

        return parseConfluenceResponse(response)
    }

    private fun parseConfluenceResponse(json: String): ConfluencePage {
        // 간단한 JSON 파싱 (kotlinx.serialization 없이)
        val idMatch = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(json)
        val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(json)
        val bodyMatch = Regex("\"value\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)

        val id = idMatch?.groupValues?.get(1) ?: "unknown"
        val title = titleMatch?.groupValues?.get(1) ?: "Untitled"
        val htmlContent = bodyMatch?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
            ?: ""

        return ConfluencePage(
            id = id,
            title = title,
            content = convertHtmlToMarkdown(htmlContent),
        )
    }

    fun convertHtmlToMarkdown(html: String): String {
        return html
            .replace(Regex("<h1[^>]*>"), "# ")
            .replace(Regex("<h2[^>]*>"), "## ")
            .replace(Regex("<h3[^>]*>"), "### ")
            .replace(Regex("<h4[^>]*>"), "#### ")
            .replace(Regex("</h[1-6]>"), "\n")
            .replace(Regex("<p[^>]*>"), "\n")
            .replace(Regex("</p>"), "\n")
            .replace(Regex("<br[^>]*/?>"), "\n")
            .replace(Regex("<strong[^>]*>|<b[^>]*>"), "**")
            .replace(Regex("</strong>|</b>"), "**")
            .replace(Regex("<em[^>]*>|<i[^>]*>"), "*")
            .replace(Regex("</em>|</i>"), "*")
            .replace(Regex("<ul[^>]*>|</ul>|<ol[^>]*>|</ol>"), "\n")
            .replace(Regex("<li[^>]*>"), "- ")
            .replace(Regex("</li>"), "\n")
            .replace(Regex("<code[^>]*>"), "`")
            .replace(Regex("</code>"), "`")
            .replace(Regex("<[^>]+>"), "")  // 나머지 태그 제거
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("\n{3,}"), "\n\n")  // 연속 빈줄 정리
            .trim()
    }

    fun close() = client.close()
}
