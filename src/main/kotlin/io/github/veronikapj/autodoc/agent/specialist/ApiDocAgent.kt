package io.github.veronikapj.autodoc.agent.specialist

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import io.github.veronikapj.autodoc.platform.TemplateResolver
import io.github.veronikapj.autodoc.tools.codeSearchTool
import io.github.veronikapj.autodoc.tools.listFiles
import io.github.veronikapj.autodoc.tools.readFile

class ApiDocAgent(
    executor: MultiLLMPromptExecutor,
    templateResolver: TemplateResolver,
) : BaseDocAgent(executor, templateResolver) {

    override val tag = "api"
    override val templateName = "API"

    override fun buildToolRegistry() = ToolRegistry {
        tool(::readFile)
        tool(::listFiles)
        tool(::codeSearchTool)
    }

    override fun buildSystemPrompt(template: String) = """
# API 문서 생성 전문 에이전트

## 역할
당신은 프로젝트의 API 엔드포인트 문서(API.md)를 작성하거나 업데이트하는 전문 문서 에이전트입니다.

## 사용 가능한 도구
- readFile: 파일 내용을 읽습니다
- listFile: 디렉터리 구조를 탐색합니다
- code_search: ripgrep을 사용해 코드 패턴을 검색합니다

## 작업 절차
1. code_search로 @GET, @POST, @PUT, @DELETE, @PATCH 어노테이션을 탐색합니다.
2. 각 엔드포인트 파일을 readFile로 읽어 요청/응답 파라미터를 파악합니다.
3. 기존 API.md가 있으면 전체를 읽고 시작합니다.
4. 결과로 문서의 전체 내용만 출력합니다.

## 템플릿
${'$'}template

## 행동 규칙
1. 삭제된 엔드포인트는 [DEPRECATED] 표시 (문서에서 제거하지 않음).
2. HTTP 메서드, 경로, 요청 파라미터, 응답 형식을 명시합니다.
3. 확인할 수 없는 내용은 TODO로 표시합니다.
4. 결과로 문서 전체 내용만 출력합니다.
5. 한국어로 작성합니다.
""".trimIndent()
}
