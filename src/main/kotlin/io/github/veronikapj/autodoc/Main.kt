package io.github.veronikapj.autodoc

import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import io.github.veronikapj.autodoc.a2a.A2AClientManager
import io.github.veronikapj.autodoc.a2a.A2AServerManager
import io.github.veronikapj.autodoc.llm.ClaudeCodeLLMClient
import io.github.veronikapj.autodoc.agent.OrchestratorAgent
import io.github.veronikapj.autodoc.agent.SyncOrchestrator
import io.github.veronikapj.autodoc.agent.TriageAgent
import io.github.veronikapj.autodoc.agent.toDocPath
import io.github.veronikapj.autodoc.agent.specialist.ApiDocAgent
import io.github.veronikapj.autodoc.agent.specialist.ArchDocAgent
import io.github.veronikapj.autodoc.agent.specialist.ChangelogAgent
import io.github.veronikapj.autodoc.agent.specialist.ReadmeAgent
import io.github.veronikapj.autodoc.agent.specialist.SetupDocAgent
import io.github.veronikapj.autodoc.agent.specialist.SpecDocAgent
import io.github.veronikapj.autodoc.agent.specialist.TestDocAgent
import io.github.veronikapj.autodoc.config.ConfigLoader
import io.github.veronikapj.autodoc.config.ModelProvider
import io.github.veronikapj.autodoc.platform.AgentType
import io.github.veronikapj.autodoc.platform.PlatformConfig
import io.github.veronikapj.autodoc.platform.TemplateResolver
import io.github.veronikapj.autodoc.tools.GitHubTool
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("AutoDocAgent")

fun main(args: Array<String>) = runBlocking {
    val isSyncMode = args.contains("--sync")
    val docTypeArg = args.indexOf("--doc").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val isSingleDocMode = docTypeArg != null
    val prNumber = when {
        isSyncMode || isSingleDocMode -> null
        else -> args.firstOrNull()?.toIntOrNull()
            ?: error("PR 번호를 인자로 전달해주세요. 예: ./gradlew run --args='42'")
    }

    val githubToken = System.getenv("GITHUB_TOKEN")
        ?: error("GITHUB_TOKEN 환경변수가 없습니다")
    val repoName = System.getenv("GITHUB_REPOSITORY")
        ?: error("GITHUB_REPOSITORY 환경변수가 없습니다")
    val repoPath = System.getenv("TARGET_REPO_PATH") ?: "."

    // 설정 로드
    val config = ConfigLoader.load(repoPath)
    val modeLabel = docTypeArg ?: if (isSyncMode) "sync" else prNumber.toString()
    log.info("starting — platform={} mode={} repo={} model={} {}",
        config.platform, modeLabel, repoName,
        config.model.provider, config.model.name ?: "(default)")

    // 공통 컴포넌트
    val executor = buildExecutor(config.model.provider, config.model.name)
    val templateResolver = TemplateResolver(config.platform, repoPath)
    val platformConfig = PlatformConfig(config.platform)
    val githubTool = GitHubTool(githubToken)

    // 전문 에이전트 생성
    val specialists = mapOf(
        AgentType.README to ReadmeAgent(executor, templateResolver),
        AgentType.ARCH_DOC to ArchDocAgent(executor, templateResolver),
        AgentType.API_DOC to ApiDocAgent(executor, templateResolver),
        AgentType.TEST_DOC to TestDocAgent(executor, templateResolver),
        AgentType.CHANGELOG to ChangelogAgent(executor, templateResolver),
        AgentType.SETUP_DOC to SetupDocAgent(executor, templateResolver),
        AgentType.SPEC_DOC to SpecDocAgent(executor, templateResolver),
    )

    // A2A 서버 시작
    log.info("starting A2A servers...")
    val serverManager = A2AServerManager(specialists)
    val ports = serverManager.start()

    // A2A 클라이언트 연결
    val clientManager = A2AClientManager()
    clientManager.connectAll(ports)

    try {
        if (isSingleDocMode) {
            val agentType = runCatching { AgentType.valueOf(docTypeArg) }.getOrElse {
                error("알 수 없는 문서 타입: $docTypeArg. 가능한 값: ${AgentType.entries.joinToString()}")
            }
            log.info("single doc mode — target={}", agentType.name)

            val commits = githubTool.fetchRecentCommits(repoName)
            val existingContent = githubTool.fetchFileContent(repoName, agentType.toDocPath())
            val specialist = specialists[agentType] ?: error("specialist not found: $agentType")
            val request = SyncOrchestrator.buildSyncRequest(
                agentType = agentType,
                commitLog = commits.joinToString("\n") { "- ${it.message}" },
                searchScopeHint = specialist.searchScopeHint,
                existingContent = existingContent,
            )
            val content = clientManager.sendMessage(agentType, request)
            if (content.isBlank()) {
                log.error("doc generation failed for {}, skipping PR", agentType.name)
                return@runBlocking
            }
            val changedDocs = mapOf(agentType.toDocPath() to content)

            log.info("doc generated, creating PR...")
            val summary = buildSingleDocPRSummary(agentType.name, agentType.toDocPath())
            githubTool.createDocsPR(repoName, "main", 0, changedDocs, summary)
            log.info("done")
        } else if (isSyncMode) {
            log.info("starting sync mode — repo={}", repoName)
            val triageAgent = TriageAgent(executor)
            val syncOrchestrator = SyncOrchestrator(triageAgent, clientManager, specialists, githubTool, repoName)
            val changedDocs = syncOrchestrator.run()

            if (changedDocs.isEmpty()) {
                log.info("all documents are up to date, exiting")
                return@runBlocking
            }

            log.info("{} document(s) to update: {}", changedDocs.size, changedDocs.keys.joinToString())
            val summary = buildSyncPRSummary(changedDocs.keys.toList())
            githubTool.createDocsPR(repoName, "main", 0, changedDocs, summary)
            log.info("done")
        } else {
            val orchestrator = OrchestratorAgent(clientManager, platformConfig, githubTool, repoName)
            val changedDocs = orchestrator.run(prNumber!!)

            if (changedDocs.isEmpty()) {
                log.info("no documents to update, exiting")
                return@runBlocking
            }

            log.info("{} document(s) updated: {}", changedDocs.size, changedDocs.keys.joinToString())

            val summary = buildPRSummary(changedDocs.keys.toList(), prNumber)
            githubTool.createDocsPR(repoName, "main", prNumber, changedDocs, summary)

            log.info("done")
        }
    } finally {
        serverManager.stop()
    }
}

private suspend fun buildExecutor(provider: ModelProvider, modelName: String?): MultiLLMPromptExecutor {
    val client = when (provider) {
        ModelProvider.ANTHROPIC -> {
            val key = System.getenv("ANTHROPIC_API_KEY")
                ?: error("ANTHROPIC_API_KEY 환경변수가 없습니다")
            AnthropicLLMClient(apiKey = key)
        }
        ModelProvider.GOOGLE -> {
            val key = System.getenv("GOOGLE_API_KEY")
                ?: error("GOOGLE_API_KEY 환경변수가 없습니다")
            GoogleLLMClient(apiKey = key)
        }
        ModelProvider.OPENAI -> {
            val key = System.getenv("OPENAI_API_KEY")
                ?: error("OPENAI_API_KEY 환경변수가 없습니다")
            OpenAILLMClient(apiKey = key)
        }
        ModelProvider.CLAUDE_CODE -> {
            val client = ClaudeCodeLLMClient()
            client.checkAuth()
            client
        }
    }
    return MultiLLMPromptExecutor(client)
}

private fun buildPRSummary(updatedDocs: List<String>, prNumber: Int) = """
    ## AutoDoc Agent 자동 문서 업데이트

    PR #${prNumber}의 변경사항을 분석하여 다음 문서를 업데이트했습니다:

    ${updatedDocs.joinToString("\n") { "- `$it`" }}

    > 내용을 검토 후 머지해주세요.

    Generated by [AutoDoc Agent](https://github.com/Veronikapj/autodoc-agent)
""".trimIndent()

private fun buildSingleDocPRSummary(agentType: String, docPath: String) = """
    ## AutoDoc Agent 단일 문서 생성/업데이트

    `$agentType` 문서를 생성 또는 업데이트했습니다:

    - `$docPath`

    > 내용을 검토 후 머지해주세요.

    Generated by [AutoDoc Agent](https://github.com/Veronikapj/autodoc-agent)
""".trimIndent()

private fun buildSyncPRSummary(updatedDocs: List<String>) = """
    ## AutoDoc Agent 문서 동기화

    전체 코드베이스를 분석하여 다음 문서를 업데이트했습니다:

    ${updatedDocs.joinToString("\n") { "- `$it`" }}

    > 내용을 검토 후 머지해주세요.

    Generated by [AutoDoc Agent](https://github.com/Veronikapj/autodoc-agent)
""".trimIndent()
