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

class ChangelogAgent(
    private val executor: MultiLLMPromptExecutor,
    private val templateResolver: TemplateResolver,
) : SpecialistDocAgent {

    private val registry = ToolRegistry {
        tool(::readFile)
        tool(::listFiles)
        tool(::codeSearchTool)
    }

    override suspend fun process(request: String): String {
        val template = templateResolver.resolve("CHANGELOG")
        val agent = AIAgent(
            promptExecutor = executor,
            agentConfig = AIAgentConfig(
                prompt = prompt("changelog-doc", params = AnthropicParams(maxTokens = 8192)) {
                    system(buildSystemPrompt(template))
                },
                model = AnthropicModels.Haiku_4_5,
                maxAgentIterations = 30,
            ),
            toolRegistry = registry,
            installFeatures = {
                install(EventHandler) {
                    onToolCallStarting {
                        println("\u001B[34m[changelog] 도구 호출중: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                    onToolCallCompleted {
                        println("\u001B[34m[changelog] 도구 완료: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                    onToolCallFailed {
                        println("\u001B[31m[changelog] 도구 실패: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                }
            }
        )
        return agent.run(request)
    }

    private fun buildSystemPrompt(template: String): String = """
# CHANGELOG 문서 생성 전문 에이전트

## 역할
당신은 프로젝트의 CHANGELOG.md를 작성하거나 업데이트하는 전문 문서 에이전트입니다.
PR 커밋 메시지를 분석하여 Keep a Changelog 형식으로 변환합니다.

## 사용 가능한 도구
- readFile: 파일 내용을 읽습니다
- listFile: 디렉터리 구조를 탐색합니다
- code_search: ripgrep을 사용해 코드 패턴을 검색합니다

## 작업 절차
1. 기존 CHANGELOG.md가 있으면 전체를 읽고 시작합니다.
2. PR 정보(제목, 커밋 메시지)를 분석합니다.
3. 커밋 메시지의 prefix를 Keep a Changelog 카테고리로 변환합니다.
4. [Unreleased] 섹션에 새 항목을 추가합니다.
5. 결과로 문서의 전체 내용만 출력합니다 (설명이나 코멘트 없이).

## 커밋 prefix 변환 규칙
- feat: → ### Added
- fix: → ### Fixed
- refactor: → ### Changed
- perf: → ### Changed
- docs: → ### Changed
- chore: → ### Changed
- style: → ### Changed
- test: → ### Changed
- remove: / revert: → ### Removed
- security: → ### Security
- deprecated: → ### Deprecated

## 템플릿
아래 템플릿을 참고하여 문서를 작성하세요:

$template

## 행동 규칙
1. 기존 CHANGELOG.md가 있으면 기존 형식과 스타일을 유지합니다.
2. [Unreleased] 섹션에만 새 항목을 추가합니다 (기존 버전 섹션 수정 금지).
3. PR 제목을 사용자 관점에서 읽기 쉽게 다듬어서 작성합니다 (기술 용어 최소화).
4. 같은 카테고리의 변경 사항은 하나로 그룹핑합니다.
5. Keep a Changelog(https://keepachangelog.com) 형식을 준수합니다.
6. 결과로 문서 전체 내용만 출력합니다 (설명, 코멘트, 해설 없이).
7. 한국어로 작성합니다.
""".trimIndent()
}
