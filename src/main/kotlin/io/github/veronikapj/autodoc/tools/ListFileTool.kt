package io.github.veronikapj.autodoc.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import java.io.File

@Tool("listFile")
@LLMDescription("주어진 경로에서 모든 파일과 디렉터리를 재귀적으로 탐색합니다.")
fun listFiles(
    @LLMDescription("탐색할 디렉터리의 경로")
    path: String
): String {
    require(path.isNotBlank()) { "디렉터리 경로가 비어있습니다." }

    val basePath = resolveFilePath(path)

    if (!basePath.exists()) return "오류: 경로를 찾을 수 없습니다: $path"
    if (!basePath.isDirectory) return "오류: 경로가 디렉터리가 아닙니다: $path"
    if (!basePath.canRead()) return "오류: 디렉터리의 읽기 권한이 없습니다: $path"

    val files = walkDirectory(basePath, basePath)
    return if (files.isEmpty()) {
        "디렉터리에서 파일을 찾을 수 없습니다: $path"
    } else {
        "Found ${files.size} items:\n" + files.sorted().joinToString("\n")
    }
}

internal fun walkDirectory(dir: File, base: File): List<String> {
    val entries = dir.listFiles() ?: return emptyList()
    val files = mutableListOf<String>()
    for (entry in entries) {
        val relativePath = entry.relativeTo(base).path
        if (entry.isDirectory && !shouldExclude(relativePath)) {
            files.add("$relativePath/")
            files.addAll(walkDirectory(entry, base))
        } else if (!entry.isDirectory) {
            files.add(relativePath)
        }
    }
    return files
}

private fun shouldExclude(path: String): Boolean {
    val excludedDirs = setOf(".git", ".devenv", "build", "node_modules", ".idea")
    return excludedDirs.any { path == it || path.startsWith("$it/") || path.contains("/$it/") }
}
