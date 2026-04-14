package io.github.veronikapj.autodoc.agent.specialist

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import io.github.veronikapj.autodoc.platform.TemplateResolver
import io.github.veronikapj.autodoc.tools.codeSearchTool
import io.github.veronikapj.autodoc.tools.listFiles
import io.github.veronikapj.autodoc.tools.readFile

class TestDocAgent(
    executor: MultiLLMPromptExecutor,
    templateResolver: TemplateResolver,
) : BaseDocAgent(executor, templateResolver) {

    override val tag = "test"
    override val templateName = "TESTING"

    override fun buildToolRegistry() = ToolRegistry {
        tool(::readFile)
        tool(::listFiles)
        tool(::codeSearchTool)
    }

    override fun buildSystemPrompt(template: String) = """
# 테스트 문서 생성 전문 에이전트

## 역할
당신은 프로젝트의 테스트 시나리오 문서(TESTING.md)를 작성하거나 업데이트하는 전문 문서 에이전트입니다.
@Test 함수를 분석하여 사람이 읽기 쉬운 테스트 시나리오로 변환합니다.

## 사용 가능한 도구
- readFile: 파일 내용을 읽습니다
- listFile: 디렉터리 구조를 탐색합니다
- code_search: ripgrep을 사용해 코드 패턴을 검색합니다

## 작업 절차
1. code_search로 @Test 어노테이션 함수를 탐색합니다.
2. 각 테스트 파일을 readFile로 읽어 내용을 파악합니다.
3. 기존 TESTING.md가 있으면 전체를 읽고 시작합니다.
4. 테스트 함수명을 given/when/then 구조로 서술합니다.
5. 결과로 문서의 전체 내용만 출력합니다.

## 템플릿
${'$'}template

## 행동 규칙
1. 테스트를 기능별로 그룹핑하여 시나리오를 구성합니다.
2. 테스트가 없는 기능은 "미검증 시나리오" 섹션에 표시합니다.
3. 성공 케이스와 실패 케이스(경계값, 예외 등)를 구분합니다.
4. 결과로 문서 전체 내용만 출력합니다.
5. 한국어로 작성합니다.
""".trimIndent()
}
