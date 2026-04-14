package io.github.veronikapj.autodoc.config

enum class Platform { ANDROID, IOS, BACKEND, FRONTEND, GENERIC }
enum class DocumentMode { OVERWRITE, APPEND }
enum class SpecSource { MARKDOWN, CONFLUENCE }
enum class ModelProvider { ANTHROPIC, GOOGLE, OPENAI }

data class AutoDocConfig(
    val platform: Platform = Platform.GENERIC,
    val documents: Map<String, DocumentMode> = emptyMap(),
    val spec: SpecConfig = SpecConfig(),
    val model: ModelConfig = ModelConfig(),
)

data class ModelConfig(
    val provider: ModelProvider = ModelProvider.ANTHROPIC,
    val name: String? = null,  // null이면 provider 기본 모델 사용
)

data class SpecConfig(
    val source: SpecSource = SpecSource.MARKDOWN,
    val path: String = "docs/spec/",
    val baseUrl: String? = null,
    val spaceKey: String? = null,
    val pageIds: List<Long> = emptyList(),
)
