@file:OptIn(ExperimentalAgentsApi::class)

package io.github.veronikapj.autodoc.agent.specialist

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import io.github.veronikapj.autodoc.platform.TemplateResolver
import org.slf4j.LoggerFactory

abstract class BaseDocAgent(
    protected val executor: MultiLLMPromptExecutor,
    protected val templateResolver: TemplateResolver,
) : SpecialistDocAgent {

    protected val log = LoggerFactory.getLogger(this::class.java)

    /** 에이전트 식별 태그. 로그와 prompt 이름에 사용됩니다. */
    abstract val tag: String

    /** sync 모드에서 에이전트가 우선 탐색할 파일 패턴 힌트 */
    abstract val searchScopeHint: String

    /** templateResolver.resolve() 에 넘길 템플릿 이름 */
    abstract val templateName: String

    /** 에이전트가 사용할 도구 목록 */
    abstract fun buildToolRegistry(): ToolRegistry

    /** 시스템 프롬프트 구성 */
    abstract fun buildSystemPrompt(template: String): String

    override suspend fun process(request: String): String {
        val template = templateResolver.resolve(templateName)
        val fallbackModels = listOf(AnthropicModels.Haiku_4_5, AnthropicModels.Sonnet_4)
        for ((index, model) in fallbackModels.withIndex()) {
            val result = runCatching { buildAgent(template, model).run(request) }
            val ex = result.exceptionOrNull()
            if (ex == null) return result.getOrThrow()
            if (isRateLimitError(ex) && index < fallbackModels.lastIndex) {
                log.warn("[{}] rate limit on {}, retrying with {}", tag, model.id, fallbackModels[index + 1].id)
                continue
            }
            throw ex
        }
        error("unreachable")
    }

    private fun buildAgent(template: String, model: LLModel): AIAgent<String, String> =
        AIAgent(
            promptExecutor = executor,
            agentConfig = AIAgentConfig(
                prompt = prompt(tag, params = AnthropicParams(maxTokens = 8192)) {
                    system(buildSystemPrompt(template))
                },
                model = model,
                maxAgentIterations = 30,
            ),
            toolRegistry = buildToolRegistry(),
            installFeatures = {
                install(EventHandler) {
                    onToolCallStarting {
                        log.debug("[{}] tool call: {}", tag, it.toolName)
                        it.toolName
                    }
                    onToolCallCompleted {
                        log.debug("[{}] tool done: {}", tag, it.toolName)
                        it.toolName
                    }
                    onToolCallFailed {
                        log.warn("[{}] tool failed: {}", tag, it.toolName)
                        it.toolName
                    }
                }
            }
        )

    companion object {
        fun isRateLimitError(e: Throwable): Boolean {
            val msg = e.message ?: return false
            return msg.contains("429") || msg.contains("rate_limit")
        }
    }
}
