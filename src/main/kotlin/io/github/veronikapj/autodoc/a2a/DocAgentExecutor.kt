package io.github.veronikapj.autodoc.a2a

import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.TaskIdParams
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import io.github.veronikapj.autodoc.agent.specialist.SpecialistDocAgent
import kotlinx.coroutines.Deferred
import java.util.UUID

class DocAgentExecutor(
    private val agent: SpecialistDocAgent,
) : AgentExecutor {

    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor,
    ) {
        val userMessage = context.params.message
        val userText = userMessage.parts
            .filterIsInstance<TextPart>()
            .joinToString(" ") { it.text }

        val result = agent.process(userText)

        val response = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.Agent,
            parts = listOf(TextPart(result)),
            contextId = context.contextId,
        )
        eventProcessor.sendMessage(response)
    }

    override suspend fun cancel(
        context: RequestContext<TaskIdParams>,
        eventProcessor: SessionEventProcessor,
        agentJob: Deferred<Unit>?,
    ) {
        agentJob?.cancel()
    }
}
