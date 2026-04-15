package io.github.veronikapj.autodoc.agent

import io.github.veronikapj.autodoc.a2a.A2AClientManager
import io.github.veronikapj.autodoc.platform.AgentType
import io.github.veronikapj.autodoc.platform.PlatformConfig
import io.github.veronikapj.autodoc.tools.GitHubTool
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

class OrchestratorAgent(
    private val clientManager: A2AClientManager,
    private val platformConfig: PlatformConfig,
    private val githubTool: GitHubTool,
    private val repoName: String,
) {
    private val log = LoggerFactory.getLogger(OrchestratorAgent::class.java)

    suspend fun run(prNumber: Int): Map<String, String> = coroutineScope {
        // 1. PR diff 분석
        val changedFiles = githubTool.fetchChangedFiles(repoName, prNumber)
        val prInfo = githubTool.fetchPRInfo(repoName, prNumber)

        log.info("PR #{}: {} ({} files changed)", prNumber, prInfo.title, changedFiles.size)

        // 2. 필요한 에이전트 선별
        val agentsToCall = platformConfig.resolveAgents(changedFiles).toMutableSet()
        if (platformConfig.needsChangelog(prInfo.title)) {
            agentsToCall.add(AgentType.CHANGELOG)
        }

        if (agentsToCall.isEmpty()) {
            log.info("no documents to update, skipping")
            return@coroutineScope emptyMap()
        }

        log.info("agents to call: {}", agentsToCall.joinToString { it.name })

        val request = buildRequest(prNumber, prInfo.title, changedFiles)

        // 3. 병렬 A2A 호출
        val results = agentsToCall.map { agentType ->
            async {
                try {
                    log.info("calling {} agent...", agentType.name)
                    val result = clientManager.sendMessage(agentType, request)
                    log.info("{} agent completed", agentType.name)
                    agentType to result
                } catch (e: Exception) {
                    log.error("{} agent failed: {}", agentType.name, e.message)
                    agentType to null
                }
            }
        }.awaitAll()

        // 4. 결과를 문서 경로 → 내용으로 매핑 (실패한 것 제외)
        results
            .filter { (_, content) -> content != null }
            .associate { (agentType, content) -> agentType.toDocPath() to content!! }
    }

    private fun buildRequest(prNumber: Int, prTitle: String, changedFiles: List<String>) = """
        PR #$prNumber: $prTitle

        변경된 파일 목록:
        ${changedFiles.take(50).joinToString("\n") { "- $it" }}
        ${if (changedFiles.size > 50) "... 외 ${changedFiles.size - 50}개" else ""}

        위 변경사항을 분석하고 담당 문서를 업데이트해주세요.
        기존 문서가 있으면 먼저 읽고, 없으면 템플릿 기반으로 새로 생성하세요.
        출력은 반드시 마크다운 문서 내용만 포함하세요.
        분석 과정, 판단 이유, 설명은 절대 포함하지 마세요.
    """.trimIndent()
}

internal fun AgentType.toDocPath() = when (this) {
    AgentType.README -> "README.md"
    AgentType.ARCH_DOC -> "docs/architecture.md"
    AgentType.API_DOC -> "docs/api.md"
    AgentType.TEST_DOC -> "docs/testing.md"
    AgentType.CHANGELOG -> "CHANGELOG.md"
    AgentType.SETUP_DOC -> "docs/setup.md"
    AgentType.SPEC_DOC -> "docs/spec/latest.md"
}
