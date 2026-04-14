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
        var modelProvider = ModelProvider.ANTHROPIC
        var modelName: String? = null
        var inModel = false

        for (line in lines) {
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length

            when {
                indent == 0 && trimmed.startsWith("platform:") -> {
                    inDocuments = false
                    inSpec = false
                    inModel = false
                    platform = trimmed.substringAfter("platform:").trim()
                        .uppercase()
                        .let { runCatching { Platform.valueOf(it) }.getOrDefault(Platform.GENERIC) }
                }
                indent == 0 && trimmed.startsWith("documents:") -> {
                    inDocuments = true
                    inSpec = false
                    inModel = false
                }
                indent == 0 && trimmed.startsWith("spec:") -> {
                    inDocuments = false
                    inSpec = true
                    inModel = false
                }
                indent == 0 && trimmed.startsWith("model:") -> {
                    inDocuments = false
                    inSpec = false
                    inModel = true
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
                            .uppercase()
                            .let { runCatching { SpecSource.valueOf(it) }.getOrDefault(SpecSource.MARKDOWN) }
                        trimmed.startsWith("path:") -> specPath = trimmed.substringAfter(":").trim()
                        trimmed.startsWith("base_url:") -> specBaseUrl = trimmed.substringAfter(":").trim()
                        trimmed.startsWith("space_key:") -> specSpaceKey = trimmed.substringAfter(":").trim()
                        trimmed.startsWith("- ") ->
                            trimmed.removePrefix("- ").trim().toLongOrNull()?.let { specPageIds.add(it) }
                    }
                }
                inModel && indent > 0 -> {
                    when {
                        trimmed.startsWith("provider:") -> modelProvider = trimmed.substringAfter(":").trim()
                            .uppercase()
                            .let { runCatching { ModelProvider.valueOf(it) }.getOrDefault(ModelProvider.ANTHROPIC) }
                        trimmed.startsWith("name:") ->
                            modelName = trimmed.substringAfter(":").trim().takeIf { it.isNotEmpty() }
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
            ),
            model = ModelConfig(
                provider = modelProvider,
                name = modelName,
            )
        )
    }
}
