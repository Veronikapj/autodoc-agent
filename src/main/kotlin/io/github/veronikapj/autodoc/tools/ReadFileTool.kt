package io.github.veronikapj.autodoc.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import java.io.File

@Tool("readFile")
@LLMDescription("주어진 경로의 파일 내용을 읽어서 반환합니다.")
fun readFile(
    @LLMDescription("읽을 파일의 경로")
    path: String
): String {
    require(path.isNotBlank()) { "파일 경로가 비었습니다." }

    val file = resolveFilePath(path)

    if (!file.exists()) return "오류: 파일을 찾을 수 없습니다: $path"
    if (!file.isFile) return "오류: 디렉터리입니다: $path"
    if (!file.canRead()) return "오류: 읽기 권한이 없습니다: $path"

    return runCatching {
        file.readText()
    }.getOrElse {
        "오류: ${it.message}"
    }
}

internal fun resolveFilePath(path: String): File {
    val workingDir = File(System.getProperty("user.dir"))

    val directFile = File(path)
    if (directFile.exists()) {
        return directFile.canonicalFile
    }

    val trimmedPath = path.trimStart('/')
    if (trimmedPath != path) {
        val relativeFile = File(workingDir, trimmedPath)
        if (relativeFile.exists()) {
            return relativeFile.canonicalFile
        }
    }

    return File(workingDir, path).canonicalFile
}
