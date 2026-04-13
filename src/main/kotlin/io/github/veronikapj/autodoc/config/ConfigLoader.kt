package io.github.veronikapj.autodoc.config

import java.io.File

object ConfigLoader {
    fun load(repoPath: String = "."): AutoDocConfig {
        val configFile = File("$repoPath/.autodoc/config.yml")
        if (!configFile.exists()) return AutoDocConfig()
        return parseYaml(configFile.readText())
    }

    fun parseYaml(yaml: String): AutoDocConfig {
        val lines = yaml.lines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }

        var platform = Platform.GENERIC
        val documents = mutableMapOf<String, DocumentMode>()
        var inDocuments = false
        var specSource = SpecSource.MARKDOWN
        var specPath = "docs/spec/"
        var specBaseUrl: String? = null
        var specSpaceKey: String? = null
        val specPageIds = mutableListOf<Long>()
        var inSpec = false

        for (line in lines) {
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length

            when {
                indent == 0 && trimmed.startsWith("platform:") -> {
                    inDocuments = false
                    inSpec = false
                    platform = trimmed.substringAfter("platform:").trim()
                        .uppercase().let { runCatching { Platform.valueOf(it) }.getOrDefault(Platform.GENERIC) }
                }
                indent == 0 && trimmed.startsWith("documents:") -> {
                    inDocuments = true
                    inSpec = false
                }
                indent == 0 && trimmed.startsWith("spec:") -> {
                    inDocuments = false
                    inSpec = true
                }
                inDocuments && indent > 0 -> {
                    val key = trimmed.substringBefore(":").trim()
                    val value = trimmed.substringAfter(":").trim()
                    documents[key] = value.uppercase()
                        .let { runCatching { DocumentMode.valueOf(it) }.getOrDefault(DocumentMode.OVERWRITE) }
                }
                inSpec && indent > 0 -> {
                    when {
                        trimmed.startsWith("source:") -> specSource = trimmed.substringAfter(":").trim()
                            .uppercase().let { runCatching { SpecSource.valueOf(it) }.getOrDefault(SpecSource.MARKDOWN) }
                        trimmed.startsWith("path:") -> specPath = trimmed.substringAfter(":").trim()
                        trimmed.startsWith("base_url:") -> specBaseUrl = trimmed.substringAfter(":").trim()
                        trimmed.startsWith("space_key:") -> specSpaceKey = trimmed.substringAfter(":").trim()
                        trimmed.startsWith("- ") -> trimmed.removePrefix("- ").trim().toLongOrNull()?.let { specPageIds.add(it) }
                    }
                }
            }
        }

        return AutoDocConfig(
            platform = platform,
            documents = documents,
            spec = SpecConfig(
                source = specSource,
                path = specPath,
                baseUrl = specBaseUrl,
                spaceKey = specSpaceKey,
                pageIds = specPageIds,
            )
        )
    }
}
