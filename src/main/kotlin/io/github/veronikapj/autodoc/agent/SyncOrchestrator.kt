package io.github.veronikapj.autodoc.agent

import io.github.veronikapj.autodoc.a2a.A2AClientManager
import io.github.veronikapj.autodoc.agent.specialist.BaseDocAgent
import io.github.veronikapj.autodoc.agent.specialist.BaseDocAgent.Companion.isRateLimitError
import io.github.veronikapj.autodoc.platform.AgentType
import io.github.veronikapj.autodoc.tools.GitHubTool
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

class SyncOrchestrator(
    private val triageAgent: TriageAgent,
    private val clientManager: A2AClientManager,
    private val specialists: Map<AgentType, BaseDocAgent>,
    private val githubTool: GitHubTool,
    private val repoName: String,
) {
    suspend fun run(): Map<String, String> = coroutineScope {
        val commits = githubTool.fetchRecentCommits(repoName)
        log.info("fetched {} recent commits", commits.size)

        val existingDocs = AgentType.entries.associateWith { agentType ->
            githubTool.fetchFileContent(repoName, agentType.toDocPath())
        }

        log.info("phase 1: triage...")
        val triageResults = triageAgent.triage(commits, existingDocs)
        val needed = filterNeeded(triageResults)

        if (needed.isEmpty()) {
            log.info("all documents are up to date, skipping")
            return@coroutineScope emptyMap()
        }

        log.info("phase 2: running {} agent(s) sequentially: {}", needed.size, needed.joinToString { it.name })
        val commitLog = commits.joinToString("\n") { "- ${it.message}" }

        val results = mutableListOf<Pair<AgentType, String?>>()
        needed.forEachIndexed { index, agentType ->
            if (index > 0) {
                log.info("waiting {}ms before next agent...", AGENT_DELAY_MS)
                delay(AGENT_DELAY_MS)
            }
            val specialist = specialists[agentType]
            if (specialist == null) {
                results.add(agentType to null)
                return@forEachIndexed
            }
            val request = buildSyncRequest(
                agentType = agentType,
                commitLog = commitLog,
                searchScopeHint = specialist.searchScopeHint,
                existingContent = existingDocs[agentType],
            )
            val result = retryOnRateLimit(agentType) {
                val content = clientManager.sendMessage(agentType, request)
                log.info("{} agent completed ({}/{})", agentType.name, index + 1, needed.size)
                agentType to content
            }
            results.add(result)
        }

        results
            .filter { (_, content) -> content != null }
            .associate { (agentType, content) -> agentType.toDocPath() to content!! }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncOrchestrator::class.java)
        private const val AGENT_DELAY_MS = 60_000L

        private val rateLimitDelaysMs = listOf(30_000L, 60_000L, 120_000L)

        suspend fun retryOnRateLimit(
            agentType: AgentType,
            block: suspend () -> Pair<AgentType, String?>,
        ): Pair<AgentType, String?> {
            for ((attempt, delayMs) in rateLimitDelaysMs.withIndex()) {
                val result = runCatching { block() }
                val exception = result.exceptionOrNull()
                if (exception == null) return result.getOrThrow()
                if (!isRateLimitError(exception)) {
                    log.error("{} agent failed: {}", agentType.name, exception.message)
                    return agentType to null
                }
                log.warn(
                    "{} agent hit rate limit (attempt {}/{}), waiting {}ms...",
                    agentType.name, attempt + 1, rateLimitDelaysMs.size, delayMs,
                )
                delay(delayMs)
            }
            // final attempt after last delay
            return runCatching { block() }.getOrElse { e ->
                log.error("{} agent failed after all retries: {}", agentType.name, e.message)
                agentType to null
            }
        }

        fun filterNeeded(triageResults: Map<AgentType, TriageResult>): List<AgentType> =
            triageResults.filter { (_, result) -> result == TriageResult.NEEDED }.keys.toList()

        fun buildSyncRequest(
            agentType: AgentType,
            commitLog: String,
            searchScopeHint: String,
            existingContent: String?,
        ): String = """
            [SYNC 모드] ${agentType.name} 문서 동기화 요청

            최근 커밋 로그:
            $commitLog

            우선 탐색 범위: $searchScopeHint

            기존 문서 상태: ${if (existingContent == null) "문서 없음 — 새로 생성하세요" else "존재함 — 업데이트가 필요한 부분만 수정하세요"}

            코드를 탐색하여 문서를 최신 상태로 만들어주세요.
            출력은 반드시 마크다운 문서 내용만 포함하세요. 설명, 코멘트 절대 금지.
        """.trimIndent()
    }
}
