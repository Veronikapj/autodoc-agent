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

class ApiDocAgent(
    private val executor: MultiLLMPromptExecutor,
    private val templateResolver: TemplateResolver,
) : SpecialistDocAgent {

    private val registry = ToolRegistry {
        tool(::readFile)
        tool(::listFiles)
        tool(::codeSearchTool)
    }

    override suspend fun process(request: String): String {
        val template = templateResolver.resolve("API")
        val agent = AIAgent(
            promptExecutor = executor,
            agentConfig = AIAgentConfig(
                prompt = prompt("api-doc", params = AnthropicParams(maxTokens = 8192)) {
                    system(buildSystemPrompt(template))
                },
                model = AnthropicModels.Haiku_4_5,
                maxAgentIterations = 30,
            ),
            toolRegistry = registry,
            installFeatures = {
                install(EventHandler) {
                    onToolCallStarting {
                        println("\u001B[34m[api] 도구 호출중: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                    onToolCallCompleted {
                        println("\u001B[34m[api] 도구 완료: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                    onToolCallFailed {
                        println("\u001B[31m[api] 도구 실패: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                }
            }
        )
        return agent.run(request)
    }

    private fun buildSystemPrompt(template: String): String = """
# API 문서 생성 전문 에이전트

## 역할
당신은 프로젝트의 API 엔드포인트 문서(API.md)를 작성하거나 업데이트하는 전문 문서 에이전트입니다.
Retrofit 또는 Ktor 기반의 HTTP API를 분석하여 문서화합니다.

## 사용 가능한 도구
- readFile: 파일 내용을 읽습니다
- listFile: 디렉터리 구조를 탐색합니다
- code_search: ripgrep을 사용해 코드 패턴을 검색합니다

## 작업 절차
1. code_search를 사용해 @GET, @POST, @PUT, @DELETE, @PATCH 어노테이션을 탐색합니다.
2. 각 엔드포인트 파일을 readFile로 읽어 요청/응답 파라미터를 파악합니다.
3. 기존 API.md가 있으면 전체를 읽고 시작합니다.
4. 각 엔드포인트를 정리하여 문서를 작성합니다.
5. 결과로 문서의 전체 내용만 출력합니다 (설명이나 코멘트 없이).

## 템플릿
아래 템플릿을 참고하여 문서를 작성하세요:

$template

## 행동 규칙
1. @GET, @POST, @PUT, @DELETE, @PATCH 어노테이션을 반드시 codeSearch로 탐색합니다.
2. 삭제된 엔드포인트는 [DEPRECATED] 표시를 추가합니다 (문서에서 제거하지 않음).
3. 각 엔드포인트마다 HTTP 메서드, 경로, 요청 파라미터, 응답 형식을 명시합니다.
4. 인증 방식(헤더, 토큰 등)이 있으면 표시합니다.
5. 확인할 수 없는 내용은 TODO로 표시합니다.
6. 결과로 문서 전체 내용만 출력합니다 (설명, 코멘트, 해설 없이).
7. 한국어로 작성합니다.
""".trimIndent()
}
