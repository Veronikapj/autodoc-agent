package io.github.veronikapj.autodoc.a2a

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.client.UrlAgentCardResolver
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
import io.github.veronikapj.autodoc.platform.AgentType
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID

data class DocAgentClientInfo(
    val agentType: AgentType,
    val client: A2AClient,
    val httpClient: HttpClient,
)

class A2AClientManager {
    private val clients = mutableListOf<DocAgentClientInfo>()

    val allClients: List<DocAgentClientInfo>
        get() = clients.toList()

    suspend fun connectAll(ports: Map<AgentType, Int>) = coroutineScope {
        val connected = ports.map { (agentType, port) ->
            async {
                val httpClient = HttpClient(CIO) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 300_000
                        connectTimeoutMillis = 10_000
                        socketTimeoutMillis = 300_000
                    }
                }
                val transport = HttpJSONRPCClientTransport(
                    url = "http://localhost:$port/a2a",
                    baseHttpClient = httpClient,
                )
                val agentCardResolver = UrlAgentCardResolver(
                    baseUrl = "http://localhost:$port",
                    path = "/.well-known/agent-card.json",
                )
                val client = A2AClient(
                    transport = transport,
                    agentCardResolver = agentCardResolver,
                )
                client.connect()
                println("\u001B[32mA2A Doc Client 연결: $agentType (port $port)\u001B[0m")
                DocAgentClientInfo(agentType, client, httpClient)
            }
        }.awaitAll()
        clients.addAll(connected)
    }

    suspend fun sendMessage(agentType: AgentType, message: String): String {
        val clientInfo = clients.find { it.agentType == agentType }
            ?: error("AgentType $agentType 에 대한 클라이언트가 연결되어 있지 않습니다.")

        val a2aMessage = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.User,
            parts = listOf(TextPart(message)),
            contextId = "doc-${UUID.randomUUID()}",
        )
        val request = Request(data = MessageSendParams(a2aMessage))
        val response = clientInfo.client.sendMessage(request)

        return response.data.let { event ->
            when (event) {
                is Message -> event.parts
                    .filterIsInstance<TextPart>()
                    .joinToString("\n") { it.text }
                else -> "응답을 파싱할 수 없습니다."
            }
        }
    }

    fun getClient(agentType: AgentType): A2AClient? =
        clients.find { it.agentType == agentType }?.client

    fun close() {
        clients.forEach { it.httpClient.close() }
        clients.clear()
    }
}
