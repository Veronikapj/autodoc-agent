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

class SpecDocAgent(
    private val executor: MultiLLMPromptExecutor,
    private val templateResolver: TemplateResolver,
) : SpecialistDocAgent {

    private val registry = ToolRegistry {
        tool(::readFile)
        tool(::listFiles)
        tool(::codeSearchTool)
    }

    override suspend fun process(request: String): String {
        val template = templateResolver.resolve("SPEC")
        val agent = AIAgent(
            promptExecutor = executor,
            agentConfig = AIAgentConfig(
                prompt = prompt("spec-doc", params = AnthropicParams(maxTokens = 8192)) {
                    system(buildSystemPrompt(template))
                },
                model = AnthropicModels.Haiku_4_5,
                maxAgentIterations = 30,
            ),
            toolRegistry = registry,
            installFeatures = {
                install(EventHandler) {
                    onToolCallStarting {
                        println("\u001B[34m[spec] 도구 호출중: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                    onToolCallCompleted {
                        println("\u001B[34m[spec] 도구 완료: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                    onToolCallFailed {
                        println("\u001B[31m[spec] 도구 실패: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                }
            }
        )
        return agent.run(request)
    }

    private fun buildSystemPrompt(template: String): String = """
# 기능 명세 문서 생성 전문 에이전트

## 역할
당신은 프로젝트의 기능 명세 문서(SPEC.md)를 작성하거나 업데이트하는 전문 문서 에이전트입니다.
PR 변경 사항을 분석하여 기능 명세에 변경 이력을 추가합니다.

## 사용 가능한 도구
- readFile: 파일 내용을 읽습니다
- listFile: 디렉터리 구조를 탐색합니다
- code_search: ripgrep을 사용해 코드 패턴을 검색합니다

## 작업 절차
1. 기존 SPEC.md가 있으면 반드시 전체를 읽고 시작합니다.
2. PR 변경 사항을 분석하여 어떤 기능이 추가/변경/삭제되었는지 파악합니다.
3. Append 모드로 동작합니다: 기존 내용을 유지하고 하단에 변경 이력을 추가합니다.
4. 결과로 문서의 전체 내용만 출력합니다 (설명이나 코멘트 없이).

## 변경 이력 형식
기존 문서 하단에 다음 형식으로 추가합니다:

```
## 변경 이력

### {날짜} | PR #{번호}
- {변경사항 1}
- {변경사항 2}
```

## 템플릿
기존 SPEC.md가 없을 경우 아래 템플릿을 참고하여 문서를 작성하세요:

$template

## 행동 규칙
1. Append 모드로 동작합니다: 기존 내용을 절대 수정하지 않습니다.
2. 기존 내용을 전부 유지하고 하단에 변경 이력 섹션만 추가합니다.
3. 변경 이력 형식: `## 변경 이력 / ### {날짜} | PR #{번호} / - {변경사항}`
4. 날짜는 PR 생성일 또는 오늘 날짜를 사용합니다.
5. 변경사항은 사용자 관점에서 이해하기 쉽게 서술합니다.
6. 결과로 문서 전체 내용만 출력합니다 (설명, 코멘트, 해설 없이).
7. 한국어로 작성합니다.
""".trimIndent()
}
