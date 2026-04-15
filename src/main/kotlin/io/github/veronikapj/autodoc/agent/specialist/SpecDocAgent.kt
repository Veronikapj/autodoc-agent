package io.github.veronikapj.autodoc.agent.specialist

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import io.github.veronikapj.autodoc.platform.TemplateResolver
import io.github.veronikapj.autodoc.tools.codeSearchTool
import io.github.veronikapj.autodoc.tools.listFiles
import io.github.veronikapj.autodoc.tools.readFile

class SpecDocAgent(
    executor: MultiLLMPromptExecutor,
    templateResolver: TemplateResolver,
) : BaseDocAgent(executor, templateResolver) {

    override val tag = "spec"
    override val templateName = "SPEC"
    override val searchScopeHint = "docs/spec/, .autodoc/config.yml"

    override fun buildToolRegistry() = ToolRegistry {
        tool(::readFile)
        tool(::listFiles)
        tool(::codeSearchTool)
    }

    override fun buildSystemPrompt(template: String) = """
# 기능 명세 문서 생성 전문 에이전트

## 역할
당신은 프로젝트의 기능 명세 문서(SPEC.md)를 append 모드로 업데이트하는 전문 문서 에이전트입니다.

## 사용 가능한 도구
- readFile: 파일 내용을 읽습니다
- listFile: 디렉터리 구조를 탐색합니다
- code_search: ripgrep을 사용해 코드 패턴을 검색합니다

## 작업 절차
1. 기존 SPEC.md가 있으면 반드시 전체를 읽고 시작합니다.
2. PR 변경 사항을 분석하여 추가/변경/삭제된 기능을 파악합니다.
3. 기존 내용을 유지하고 하단에 변경 이력을 추가합니다.
4. 결과로 문서의 전체 내용만 출력합니다.

## 변경 이력 형식
```
## 변경 이력

### {날짜} | PR #{번호}
- {변경사항}
```

## 템플릿
${'$'}template

## 행동 규칙
1. 기존 내용을 절대 수정하지 않습니다 (append 전용).
2. 변경사항은 사용자 관점에서 이해하기 쉽게 서술합니다.
3. 결과로 문서 전체 내용만 출력합니다.
4. 한국어로 작성합니다.
""".trimIndent()
}
