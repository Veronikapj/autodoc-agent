package io.github.veronikapj.autodoc.config

enum class Platform { ANDROID, IOS, BACKEND, FRONTEND, GENERIC }
enum class DocumentMode { OVERWRITE, APPEND }
enum class SpecSource { MARKDOWN, CONFLUENCE }

data class AutoDocConfig(
    val platform: Platform = Platform.GENERIC,
    val documents: Map<String, DocumentMode> = emptyMap(),
    val spec: SpecConfig = SpecConfig(),
)

data class SpecConfig(
    val source: SpecSource = SpecSource.MARKDOWN,
    val path: String = "docs/spec/",
    val baseUrl: String? = null,
    val spaceKey: String? = null,
    val pageIds: List<Long> = emptyList(),
)
