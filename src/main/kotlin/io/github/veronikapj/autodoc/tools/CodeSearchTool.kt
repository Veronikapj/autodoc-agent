package io.github.veronikapj.autodoc.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import java.util.concurrent.TimeUnit

@Tool("code_search")
@LLMDescription(
    """
    ripgrep을 사용해 코드를 검색합니다. 정규식을 지원합니다.
    파일 경로, 라인 번호, 매칭된 내용을 반환합니다.
    타임아웃: 30초
    가독성을 위해 결과를 50개로 제한합니다.
"""
)
fun codeSearchTool(
    @LLMDescription("검색 패턴 (정규식 지원)")
    pattern: String,
    @LLMDescription("검색할 파일 경로(기본 값: 현재 디렉터리)")
    path: String = ".",
    @LLMDescription("대소문자 구분 여부 (기본값: false)")
    caseSensitive: Boolean = false,
    @LLMDescription("파일 타입 필터 (예: 'kt', 'java', 'py')")
    fileType: String? = null,
): String {
    val cmdArgs = buildList {
        add("rg")
        add("--line-number")
        add("--with-filename")
        add("--color=never")
        add("--no-heading")
        if (!caseSensitive) {
            add("--ignore-case")
        }
        if (fileType != null) {
            add("--type")
            add(fileType)
        }
        add(pattern)
        add(path)
    }

    val process = ProcessBuilder(cmdArgs)
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream
        .bufferedReader()
        .use { it.readText() }
    val finished = process.waitFor(30, TimeUnit.SECONDS)

    if (!finished) {
        process.destroyForcibly()
        return "타임아웃: 30초 초과"
    }

    return when (val exitCode = process.exitValue()) {
        0 -> {
            val lines = output.trim().lines()
            if (lines.size > 50) {
                buildString {
                    appendLine("${lines.size} 개 결과 발견 (처음 50개만 표시): ")
                    appendLine()
                    appendLine(lines.take(50).joinToString("\n"))
                    appendLine()
                    appendLine("... ${lines.size - 50}개 결과 생략")
                }
            } else {
                buildString {
                    appendLine("${lines.size} 개 결과 발견")
                    appendLine()
                    appendLine(output.trim())
                }
            }
        }
        1 -> "검색 결과 없음: '$pattern'"
        else -> "ripgrep 오류 (exit code: $exitCode): $output"
    }
}
