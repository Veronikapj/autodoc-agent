package io.github.veronikapj.autodoc.agent.specialist

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import io.github.veronikapj.autodoc.platform.TemplateResolver
import io.github.veronikapj.autodoc.tools.codeSearchTool
import io.github.veronikapj.autodoc.tools.listFiles
import io.github.veronikapj.autodoc.tools.readFile

class SetupDocAgent(
    executor: MultiLLMPromptExecutor,
    templateResolver: TemplateResolver,
) : BaseDocAgent(executor, templateResolver) {

    override val tag = "setup"
    override val templateName = "SETUP"

    override fun buildToolRegistry() = ToolRegistry {
        tool(::readFile)
        tool(::listFiles)
        tool(::codeSearchTool)
    }

    override fun buildSystemPrompt(template: String) = """
# 개발 환경 설정 문서 생성 전문 에이전트

## 역할
당신은 프로젝트의 개발 환경 설정 문서(SETUP.md)를 작성하거나 업데이트하는 전문 문서 에이전트입니다.

## 사용 가능한 도구
- readFile: 파일 내용을 읽습니다
- listFile: 디렉터리 구조를 탐색합니다
- code_search: ripgrep을 사용해 코드 패턴을 검색합니다

## 작업 절차
1. 루트 build.gradle.kts를 반드시 읽습니다.
2. settings.gradle.kts를 읽어 프로젝트 구조를 파악합니다.
3. compileSdk, minSdk, targetSdk, JDK 버전을 추출합니다.
4. 기존 SETUP.md가 있으면 전체를 읽고 시작합니다.
5. 결과로 문서의 전체 내용만 출력합니다.

## 템플릿
${'$'}template

## 행동 규칙
1. 버전 정보는 파일에서 직접 읽어 추출합니다 (추측 금지).
2. 불명확한 항목은 TODO로 표시합니다.
3. 단계별 설치/실행 가이드를 명확하게 작성합니다.
4. 결과로 문서 전체 내용만 출력합니다.
5. 한국어로 작성합니다.
""".trimIndent()
}
