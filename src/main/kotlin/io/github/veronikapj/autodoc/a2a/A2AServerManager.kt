package io.github.veronikapj.autodoc.a2a

import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentSkill
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.server.A2AServer
import ai.koog.a2a.transport.server.jsonrpc.http.HttpJSONRPCServerTransport
import io.github.veronikapj.autodoc.agent.specialist.SpecialistDocAgent
import io.github.veronikapj.autodoc.platform.AgentType
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.net.ServerSocket

data class RunningDocServer(
    val agentType: AgentType,
    val port: Int,
    val transport: HttpJSONRPCServerTransport,
)

class A2AServerManager(
    private val agents: Map<AgentType, SpecialistDocAgent>,
) {
    private val log = LoggerFactory.getLogger(A2AServerManager::class.java)
    private val runningServers = mutableListOf<RunningDocServer>()
    private val serverJobs = mutableListOf<Job>()
    private var serverScope: CoroutineScope? = null
    private var isRunning = false

    val ports: Map<AgentType, Int>
        get() = runningServers.associate { it.agentType to it.port }

    suspend fun start(): Map<AgentType, Int> = coroutineScope {
        if (isRunning) return@coroutineScope ports

        val allocatedPorts = mutableMapOf<AgentType, Int>()
        for (agentType in agents.keys) {
            val port = ServerSocket(0).use { it.localPort }
            allocatedPorts[agentType] = port
        }

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        serverScope = scope

        for ((agentType, agent) in agents) {
            val port = allocatedPorts[agentType] ?: continue
            val agentCard = createAgentCard(agentType, port)

            val executor = DocAgentExecutor(agent)
            val server = A2AServer(
                agentExecutor = executor,
                agentCard = agentCard,
            )
            val transport = HttpJSONRPCServerTransport(requestHandler = server)

            val job = scope.launch {
                transport.start(
                    engineFactory = Netty,
                    port = port,
                    path = "/a2a",
                    wait = true,
                    agentCard = agentCard,
                )
            }

            runningServers.add(RunningDocServer(agentType, port, transport))
            serverJobs.add(job)
        }

        waitForServersReady(allocatedPorts.values.toList())
        isRunning = true

        log.info("A2A servers started:")
        for (server in runningServers) {
            log.info("  {} -> http://localhost:{}/a2a", server.agentType, server.port)
        }

        allocatedPorts
    }

    private fun createAgentCard(agentType: AgentType, port: Int): AgentCard {
        val (name, description, skillId, skillDescription) = when (agentType) {
            AgentType.README -> AgentCardMeta(
                name = "README Doc Agent",
                description = "프로젝트 README 문서를 생성하는 에이전트",
                skillId = "readme-doc",
                skillDescription = "프로젝트 개요, 설치 방법, 사용법 등 README 문서 생성",
            )
            AgentType.ARCH_DOC -> AgentCardMeta(
                name = "Architecture Doc Agent",
                description = "프로젝트 아키텍처 문서를 생성하는 에이전트",
                skillId = "arch-doc",
                skillDescription = "시스템 구조, 모듈 설계, 의존성 관계 등 아키텍처 문서 생성",
            )
            AgentType.API_DOC -> AgentCardMeta(
                name = "API Doc Agent",
                description = "API 문서를 생성하는 에이전트",
                skillId = "api-doc",
                skillDescription = "API 엔드포인트, 파라미터, 응답 형식 등 API 문서 생성",
            )
            AgentType.TEST_DOC -> AgentCardMeta(
                name = "Test Doc Agent",
                description = "테스트 문서를 생성하는 에이전트",
                skillId = "test-doc",
                skillDescription = "테스트 전략, 커버리지, 시나리오 등 테스트 문서 생성",
            )
            AgentType.CHANGELOG -> AgentCardMeta(
                name = "Changelog Agent",
                description = "변경 이력 문서를 생성하는 에이전트",
                skillId = "changelog-doc",
                skillDescription = "PR 변경사항 기반 CHANGELOG 문서 생성",
            )
            AgentType.SETUP_DOC -> AgentCardMeta(
                name = "Setup Doc Agent",
                description = "설치 및 환경 설정 문서를 생성하는 에이전트",
                skillId = "setup-doc",
                skillDescription = "개발 환경 설정, 의존성 설치, 빌드 방법 등 Setup 문서 생성",
            )
            AgentType.SPEC_DOC -> AgentCardMeta(
                name = "Spec Doc Agent",
                description = "기능 명세 문서를 생성하는 에이전트",
                skillId = "spec-doc",
                skillDescription = "기능 요구사항, 기술 명세, 설계 결정 등 스펙 문서 생성",
            )
        }

        return AgentCard(
            name = name,
            description = description,
            version = "1.0.0",
            protocolVersion = "0.3.0",
            url = "http://localhost:$port/a2a",
            preferredTransport = TransportProtocol.JSONRPC,
            capabilities = AgentCapabilities(
                streaming = false,
                pushNotifications = false,
                stateTransitionHistory = false,
            ),
            defaultInputModes = listOf("text/plain"),
            defaultOutputModes = listOf("text/plain", "text/markdown"),
            skills = listOf(
                AgentSkill(
                    id = skillId,
                    name = name,
                    description = skillDescription,
                    tags = listOf("documentation", agentType.name.lowercase()),
                )
            ),
        )
    }

    private suspend fun waitForServersReady(ports: List<Int>, maxRetries: Int = 20, delayMs: Long = 500) {
        val httpClient = HttpClient(CIO)
        try {
            for (port in ports) {
                var ready = false
                for (i in 1..maxRetries) {
                    try {
                        val response = httpClient.get("http://localhost:$port/.well-known/agent-card.json")
                        if (response.status.value == 200) {
                            ready = true
                            break
                        }
                    } catch (_: Exception) {
                        delay(delayMs)
                    }
                }
                if (!ready) {
                    log.warn("server port {} health check timed out", port)
                }
            }
        } finally {
            httpClient.close()
        }
    }

    fun stop() {
        if (!isRunning) return
        log.info("stopping A2A servers...")
        serverJobs.forEach { it.cancel() }
        serverScope?.cancel()
        runningServers.clear()
        serverJobs.clear()
        serverScope = null
        isRunning = false
        log.info("A2A servers stopped")
    }

    private data class AgentCardMeta(
        val name: String,
        val description: String,
        val skillId: String,
        val skillDescription: String,
    )
}
