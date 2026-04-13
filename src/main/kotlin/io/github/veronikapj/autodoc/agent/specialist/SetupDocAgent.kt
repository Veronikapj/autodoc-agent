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

class SetupDocAgent(
    private val executor: MultiLLMPromptExecutor,
    private val templateResolver: TemplateResolver,
) : SpecialistDocAgent {

    private val registry = ToolRegistry {
        tool(::readFile)
        tool(::listFiles)
        tool(::codeSearchTool)
    }

    override suspend fun process(request: String): String {
        val template = templateResolver.resolve("SETUP")
        val agent = AIAgent(
            promptExecutor = executor,
            agentConfig = AIAgentConfig(
                prompt = prompt("setup-doc", params = AnthropicParams(maxTokens = 8192)) {
                    system(buildSystemPrompt(template))
                },
                model = AnthropicModels.Haiku_4_5,
                maxAgentIterations = 30,
            ),
            toolRegistry = registry,
            installFeatures = {
                install(EventHandler) {
                    onToolCallStarting {
                        println("\u001B[34m[setup] 도구 호출중: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                    onToolCallCompleted {
                        println("\u001B[34m[setup] 도구 완료: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                    onToolCallFailed {
                        println("\u001B[31m[setup] 도구 실패: ${it.toolName}\u001B[0m")
                        it.toolName
                    }
                }
            }
        )
        return agent.run(request)
    }

    private fun buildSystemPrompt(template: String): String = """
# 개발 환경 설정 문서 생성 전문 에이전트

## 역할
당신은 프로젝트의 개발 환경 설정 문서(SETUP.md)를 작성하거나 업데이트하는 전문 문서 에이전트입니다.
빌드 설정 파일을 분석하여 정확한 환경 요구 사항을 문서화합니다.

## 사용 가능한 도구
- readFile: 파일 내용을 읽습니다
- listFile: 디렉터리 구조를 탐색합니다
- code_search: ripgrep을 사용해 코드 패턴을 검색합니다

## 작업 절차
1. 루트 build.gradle.kts 또는 build.gradle을 반드시 읽습니다.
2. settings.gradle.kts 또는 settings.gradle을 읽어 프로젝트 구조를 파악합니다.
3. compileSdk, minSdk, targetSdk 값을 build.gradle.kts에서 추출합니다.
4. JDK 버전은 kotlin.jvmToolchain 또는 java.toolchain 설정에서 추출합니다.
5. local.properties, .env 파일 등 환경 설정 파일을 확인합니다.
6. 기존 SETUP.md가 있으면 전체를 읽고 시작합니다.
7. 결과로 문서의 전체 내용만 출력합니다 (설명이나 코멘트 없이).

## 추출해야 할 정보
- JDK 버전 (kotlin.jvmToolchain 또는 compileOptions.sourceCompatibility)
- Android SDK 버전 (compileSdk, minSdk, targetSdk) - Android 프로젝트인 경우
- Kotlin 버전 (plugins 블록의 kotlin 버전)
- 필수 환경 변수 및 secrets
- 빌드 명령어
- 테스트 실행 방법

## 템플릿
아래 템플릿을 참고하여 문서를 작성하세요:

$template

## 행동 규칙
1. compileSdk, minSdk, targetSdk를 build.gradle.kts에서 직접 읽어 추출합니다.
2. JDK 버전은 toolchain 설정에서 추출합니다 (없으면 TODO 표시).
3. 불명확하거나 확인할 수 없는 항목은 TODO로 표시합니다.
4. 추측으로 버전 정보를 작성하지 않습니다.
5. 단계별 설치/실행 가이드를 명확하게 작성합니다.
6. 결과로 문서 전체 내용만 출력합니다 (설명, 코멘트, 해설 없이).
7. 한국어로 작성합니다.
""".trimIndent()
}
