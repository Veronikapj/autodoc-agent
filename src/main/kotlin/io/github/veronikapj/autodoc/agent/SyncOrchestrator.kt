package io.github.veronikapj.autodoc.agent

import io.github.veronikapj.autodoc.a2a.A2AClientManager
import io.github.veronikapj.autodoc.agent.specialist.BaseDocAgent
import io.github.veronikapj.autodoc.platform.AgentType
import io.github.veronikapj.autodoc.tools.GitHubTool
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

        log.info("phase 2: running {} agent(s) in batches of {}: {}", needed.size, BATCH_SIZE, needed.joinToString { it.name })
        val commitLog = commits.take(30).joinToString("\n") { "- ${it.message}" }

        val results = needed.chunked(BATCH_SIZE).flatMapIndexed { batchIndex, batch ->
            if (batchIndex > 0) {
                log.info("waiting {}ms before next batch...", BATCH_DELAY_MS)
                delay(BATCH_DELAY_MS)
            }
            log.info("batch {}: {}", batchIndex + 1, batch.joinToString { it.name })
            batch.map { agentType ->
                async {
                    val specialist = specialists[agentType] ?: return@async agentType to null
                    val request = buildSyncRequest(
                        agentType = agentType,
                        commitLog = commitLog,
                        searchScopeHint = specialist.searchScopeHint,
                        existingContent = existingDocs[agentType],
                    )
                    runCatching {
                        val result = clientManager.sendMessage(agentType, request)
                        log.info("{} agent completed", agentType.name)
                        agentType to result
                    }.getOrElse { e ->
                        log.error("{} agent failed: {}", agentType.name, e.message)
                        agentType to null
                    }
                }
            }.awaitAll()
        }

        results
            .filter { (_, content) -> content != null }
            .associate { (agentType, content) -> agentType.toDocPath() to content!! }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SyncOrchestrator::class.java)
        private const val BATCH_SIZE = 2
        private const val BATCH_DELAY_MS = 30_000L

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
