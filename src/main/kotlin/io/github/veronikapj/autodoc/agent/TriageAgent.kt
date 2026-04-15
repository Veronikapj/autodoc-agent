@file:OptIn(ExperimentalAgentsApi::class)

package io.github.veronikapj.autodoc.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import io.github.veronikapj.autodoc.platform.AgentType
import io.github.veronikapj.autodoc.tools.CommitInfo
import org.slf4j.LoggerFactory

enum class TriageResult { NEEDED, SKIP }

class TriageAgent(private val executor: MultiLLMPromptExecutor) {

    suspend fun triage(
        commits: List<CommitInfo>,
        existingDocs: Map<AgentType, String?>,
    ): Map<AgentType, TriageResult> {
        val commitSummary = commits.joinToString("\n") { "- ${it.message}" }
        val docSummary = existingDocs.entries.joinToString("\n") { (type, content) ->
            if (content == null) "${type.name}: 문서 없음"
            else "${type.name}: 존재 (${content.length}자)"
        }

        val request = """
            최근 커밋 로그:
            $commitSummary

            현재 문서 상태:
            $docSummary

            각 문서가 최신 커밋 내용을 반영하고 있는지 판단하여 아래 형식으로만 응답하세요.
            문서가 없으면 반드시 NEEDED입니다.
            응답 예시:
            README: NEEDED
            ARCH_DOC: SKIP
            API_DOC: NEEDED
            TEST_DOC: SKIP
            CHANGELOG: NEEDED
            SETUP_DOC: SKIP
            SPEC_DOC: SKIP
        """.trimIndent()

        val agent = AIAgent(
            promptExecutor = executor,
            agentConfig = AIAgentConfig(
                prompt = prompt("triage", params = AnthropicParams(maxTokens = 512)) {
                    system(
                        """
                        당신은 문서 업데이트 필요 여부를 판단하는 에이전트입니다.
                        반드시 각 항목을 "AGENT_TYPE: NEEDED" 또는 "AGENT_TYPE: SKIP" 형식으로만 응답합니다.
                        설명, 이유, 부가 텍스트 없이 오직 판정 결과만 출력합니다.
                        """.trimIndent()
                    )
                },
                model = AnthropicModels.Haiku_4_5,
                maxAgentIterations = 5,
            ),
            toolRegistry = ToolRegistry { },
        )

        val response = agent.run(request)
        log.info("triage response: {}", response)
        return parseResponse(response)
    }

    companion object {
        private val log = LoggerFactory.getLogger(TriageAgent::class.java)

        fun parseResponse(response: String): Map<AgentType, TriageResult> {
            val parsed = response.lines()
                .mapNotNull { line ->
                    val parts = line.trim().split(":")
                    if (parts.size != 2) return@mapNotNull null
                    val type = runCatching { AgentType.valueOf(parts[0].trim()) }.getOrNull()
                        ?: return@mapNotNull null
                    val result = if (parts[1].trim() == "SKIP") TriageResult.SKIP else TriageResult.NEEDED
                    type to result
                }.toMap()

            return AgentType.entries.associateWith { parsed[it] ?: TriageResult.NEEDED }
        }
    }
}
