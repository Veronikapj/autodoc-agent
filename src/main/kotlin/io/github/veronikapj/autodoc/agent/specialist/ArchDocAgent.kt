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
import io.github.veronikapj.autodoc.platform.TemplateResolver
import io.github.veronikapj.autodoc.tools.codeSearchTool
import io.github.veronikapj.autodoc.tools.listFiles
import io.github.veronikapj.autodoc.tools.readFile

class ArchDocAgent(
    private val executor: MultiLLMPromptExecutor,
    private val templateResolver: TemplateResolver,
) : SpecialistDocAgent {

    private val registry = ToolRegistry {
        tool(::readFile)
        tool(::listFiles)
        tool(::codeSearchTool)
    }

    override suspend fun process(request: String): String {
        val template = templateResolver.resolve("ARCHITECTURE")
        val agent = AIAgent(
            promptExecutor = executor,
            agentConfig = AIAgentConfig(
                prompt = prompt("arch-doc", params = AnthropicParams(maxTokens = 8192)) {
                    system(buildSystemPrompt(template))
                },
                model = AnthropicModels.Haiku_4_5,
                maxAgentIterations = 30,
            ),
            toolRegistry = registry,
            installFeatures = {
                install(EventHandler) {
                    onToolCallStarting {
                        println("\u001B[34m[arch] 도구 호출중: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                    onToolCallCompleted {
                        println("\u001B[34m[arch] 도구 완료: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                    onToolCallFailed {
                        println("\u001B[31m[arch] 도구 실패: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                }
            }
        )
        return agent.run(request)
    }

    private fun buildSystemPrompt(template: String): String = """
# 아키텍처 문서 생성 전문 에이전트

## 역할
당신은 프로젝트 아키텍처 문서(ARCHITECTURE.md)를 작성하거나 업데이트하는 전문 문서 에이전트입니다.
모듈 의존성과 구조를 시각화하여 개발자가 프로젝트를 이해하도록 돕습니다.

## 사용 가능한 도구
- readFile: 파일 내용을 읽습니다
- listFile: 디렉터리 구조를 탐색합니다
- code_search: ripgrep을 사용해 코드 패턴을 검색합니다

## 작업 절차
1. build.gradle.kts와 settings.gradle.kts를 반드시 읽어서 모듈 구조를 파악합니다.
2. 각 모듈의 build.gradle.kts를 읽어서 모듈 간 의존성을 파악합니다.
3. 기존 ARCHITECTURE.md가 있으면 전체를 읽고 시작합니다.
4. 실제 의존성 기반으로 Mermaid 다이어그램을 생성합니다.
5. 결과로 문서의 전체 내용만 출력합니다 (설명이나 코멘트 없이).

## 템플릿
아래 템플릿을 참고하여 문서를 작성하세요:

$template

## 행동 규칙
1. build.gradle.kts와 settings.gradle.kts를 반드시 읽습니다.
2. Mermaid 그래프는 실제 의존성 기반으로만 생성합니다. 추측으로 작성하지 않습니다.
3. 기존 다이어그램이 있으면 추가/삭제된 모듈만 수정합니다 (전체 재작성 금지).
4. 각 모듈의 역할과 책임을 간략히 설명합니다.
5. 레이어 구조(presentation, domain, data 등)가 있으면 반드시 표현합니다.
6. 결과로 문서 전체 내용만 출력합니다 (설명, 코멘트, 해설 없이).
7. 한국어로 작성합니다.
""".trimIndent()
}
