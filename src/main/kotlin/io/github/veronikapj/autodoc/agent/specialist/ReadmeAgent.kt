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

class ReadmeAgent(
    private val executor: MultiLLMPromptExecutor,
    private val templateResolver: TemplateResolver,
) : SpecialistDocAgent {

    private val registry = ToolRegistry {
        tool(::readFile)
        tool(::listFiles)
        tool(::codeSearchTool)
    }

    override suspend fun process(request: String): String {
        val template = templateResolver.resolve("README")
        val agent = AIAgent(
            promptExecutor = executor,
            agentConfig = AIAgentConfig(
                prompt = prompt("readme-doc", params = AnthropicParams(maxTokens = 8192)) {
                    system(buildSystemPrompt(template))
                },
                model = AnthropicModels.Haiku_4_5,
                maxAgentIterations = 30,
            ),
            toolRegistry = registry,
            installFeatures = {
                install(EventHandler) {
                    onToolCallStarting {
                        println("\u001B[34m[readme] 도구 호출중: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                    onToolCallCompleted {
                        println("\u001B[34m[readme] 도구 완료: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                    onToolCallFailed {
                        println("\u001B[31m[readme] 도구 실패: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                }
            }
        )
        return agent.run(request)
    }

    private fun buildSystemPrompt(template: String): String = """
# README 문서 생성 전문 에이전트

## 역할
당신은 프로젝트 README.md를 작성하거나 업데이트하는 전문 문서 에이전트입니다.
PR 변경 사항을 분석하여 README를 최신 상태로 유지합니다.

## 사용 가능한 도구
- readFile: 파일 내용을 읽습니다
- listFile: 디렉터리 구조를 탐색합니다
- code_search: ripgrep을 사용해 코드 패턴을 검색합니다

## 작업 절차
1. 기존 README.md가 있으면 반드시 전체를 읽고 시작합니다.
2. 변경된 파일들을 분석하여 README에 영향을 주는 변경 사항을 파악합니다.
3. 아래 행동 규칙에 따라 README를 작성 또는 업데이트합니다.
4. 결과로 문서의 전체 내용만 출력합니다 (설명이나 코멘트 없이).

## 템플릿
아래 템플릿을 참고하여 문서를 작성하세요:

$template

## 행동 규칙
1. 기존 README가 있으면 기존 톤과 스타일을 그대로 유지합니다.
2. PR 변경 사항과 관련 없는 섹션은 절대 수정하지 않습니다.
3. 확인할 수 없는 내용(버전, 환경 변수 등)은 TODO로 표시합니다.
4. 결과로 문서 전체 내용만 출력합니다 (설명, 코멘트, 해설 없이).
5. 마크다운 형식으로 작성합니다.
6. 한국어로 작성합니다.
""".trimIndent()
}
