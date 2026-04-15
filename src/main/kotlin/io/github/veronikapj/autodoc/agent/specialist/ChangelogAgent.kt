package io.github.veronikapj.autodoc.agent.specialist

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import io.github.veronikapj.autodoc.platform.TemplateResolver
import io.github.veronikapj.autodoc.tools.codeSearchTool
import io.github.veronikapj.autodoc.tools.listFiles
import io.github.veronikapj.autodoc.tools.readFile

class ChangelogAgent(
    executor: MultiLLMPromptExecutor,
    templateResolver: TemplateResolver,
) : BaseDocAgent(executor, templateResolver) {

    override val tag = "changelog"
    override val templateName = "CHANGELOG"

    override fun buildToolRegistry() = ToolRegistry {
        tool(::readFile)
        tool(::listFiles)
        tool(::codeSearchTool)
    }

    override fun buildSystemPrompt(template: String) = """
# CHANGELOG 문서 생성 전문 에이전트

## 역할
당신은 프로젝트의 CHANGELOG.md를 작성하거나 업데이트하는 전문 문서 에이전트입니다.
PR 커밋 메시지를 Keep a Changelog 형식으로 변환합니다.

## 사용 가능한 도구
- readFile: 파일 내용을 읽습니다
- listFile: 디렉터리 구조를 탐색합니다
- code_search: ripgrep을 사용해 코드 패턴을 검색합니다

## 작업 절차
1. 기존 CHANGELOG.md가 있으면 전체를 읽고 시작합니다.
2. PR 정보(제목, 커밋 메시지)를 분석합니다.
3. [Unreleased] 섹션에 새 항목을 추가합니다.
4. 결과로 문서의 전체 내용만 출력합니다.

## 커밋 prefix 변환
- feat: → Added / fix: → Fixed / refactor:, perf: → Changed
- remove:, revert: → Removed / security: → Security

## 템플릿
${'$'}template

## 행동 규칙
1. [Unreleased] 섹션에만 새 항목을 추가합니다 (기존 버전 섹션 수정 금지).
2. PR 제목을 사용자 관점에서 읽기 쉽게 다듬어 작성합니다.
3. Keep a Changelog(https://keepachangelog.com) 형식을 준수합니다.
4. 한국어로 작성합니다.

## 출력 형식 (엄수)
- 분석 내용, 판단 과정, 설명, 코멘트를 절대 출력하지 않습니다.
- 응답의 첫 글자부터 마지막 글자까지 오직 마크다운 문서 내용만 출력합니다.
- 반드시 `# Changelog`로 시작합니다 (`# # Changelog` 등 중복 헤더 금지).
""".trimIndent()
}
