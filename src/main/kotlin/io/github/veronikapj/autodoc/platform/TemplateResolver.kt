package io.github.veronikapj.autodoc.platform

import io.github.veronikapj.autodoc.config.Platform
import java.io.File

class TemplateResolver(
    private val platform: Platform,
    private val repoPath: String = ".",
) {
    fun resolve(docName: String): String {
        val templateName = "$docName.tmpl"
        val platformDir = platform.name.lowercase()

        // 1순위: 타겟 레포 커스텀 템플릿
        File("$repoPath/.autodoc/templates/$platformDir/$templateName")
            .takeIf { it.exists() }?.let { return it.readText() }

        // 2순위: 내장 플랫폼 템플릿
        javaClass.getResourceAsStream("/templates/$platformDir/$templateName")
            ?.bufferedReader()?.readText()?.let { return it }

        // 3순위: generic 폴백
        return javaClass.getResourceAsStream("/templates/generic/$templateName")
            ?.bufferedReader()?.readText()
            ?: "# {{PROJECT_NAME}}\n\n{{CONTENT}}\n"  // 최후 폴백
    }
}
