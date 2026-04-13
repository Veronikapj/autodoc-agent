package io.github.veronikapj.autodoc.platform

import io.github.veronikapj.autodoc.config.Platform

enum class AgentType {
    README, ARCH_DOC, API_DOC, TEST_DOC, CHANGELOG, SETUP_DOC, SPEC_DOC
}

class PlatformConfig(private val platform: Platform) {

    private val skipPatterns = listOf(
        Regex(".*\\.(png|jpg|gif|svg|webp)$"),
        Regex("^docs/.*\\.md$"),
        Regex("^\\.github/.*"),
        Regex(".*\\.yml$"),
        Regex(".*\\.yaml$"),
    )

    private val patterns: Map<Platform, List<Pair<Regex, List<AgentType>>>> = mapOf(
        Platform.ANDROID to listOf(
            Regex(".*/build\\.gradle\\.kts$|^settings\\.gradle\\.kts$") to listOf(AgentType.ARCH_DOC, AgentType.SETUP_DOC),
            Regex(".*/di/.*\\.kt$|.*Module.*\\.kt$") to listOf(AgentType.ARCH_DOC),
            Regex(".*(Activity|Fragment)\\.kt$") to listOf(AgentType.ARCH_DOC, AgentType.README),
            Regex(".*(Api|Service|Endpoint)\\.kt$") to listOf(AgentType.API_DOC),
            Regex(".*Repository\\.kt$") to listOf(AgentType.API_DOC, AgentType.ARCH_DOC),
            Regex(".*(Application|MainActivity)\\.kt$") to listOf(AgentType.README),
            Regex(".*Test\\.kt$|.*Fake.*\\.kt$|.*Mock.*\\.kt$") to listOf(AgentType.TEST_DOC),
            Regex("^docs/spec/.*\\.md$") to listOf(AgentType.SPEC_DOC),
        ),
        Platform.IOS to listOf(
            Regex(".*\\.swift$") to listOf(AgentType.ARCH_DOC),
            Regex(".*Package\\.swift$") to listOf(AgentType.SETUP_DOC),
            Regex(".*Tests\\.swift$") to listOf(AgentType.TEST_DOC),
        ),
        Platform.BACKEND to listOf(
            Regex(".*(Controller)\\.kt$|.*(Controller)\\.java$") to listOf(AgentType.API_DOC),
            Regex(".*\\.sql$|.*\\.migration$") to listOf(AgentType.ARCH_DOC),
            Regex(".*Test\\.(kt|java)$") to listOf(AgentType.TEST_DOC),
            Regex(".*build\\.gradle.*$|.*pom\\.xml$") to listOf(AgentType.SETUP_DOC),
        ),
        Platform.FRONTEND to listOf(
            Regex(".*\\.(tsx|vue|svelte)$") to listOf(AgentType.ARCH_DOC),
            Regex(".*\\.stories\\.tsx$") to listOf(AgentType.ARCH_DOC),
            Regex(".*\\.(test|spec)\\.(ts|tsx|js)$") to listOf(AgentType.TEST_DOC),
            Regex(".*package\\.json$") to listOf(AgentType.SETUP_DOC),
        ),
        Platform.GENERIC to listOf(
            Regex(".*") to listOf(AgentType.README, AgentType.CHANGELOG),
        ),
    )

    private val changelogSkipPrefixes = listOf("docs:", "chore:", "style:", "test:", "ci:")

    fun resolveAgents(changedFiles: List<String>): Set<AgentType> {
        val result = mutableSetOf<AgentType>()
        for (file in changedFiles) {
            if (skipPatterns.any { it.containsMatchIn(file) }) continue
            val platformPatterns = patterns[platform] ?: patterns[Platform.GENERIC]!!
            platformPatterns.forEach { (pattern, agents) ->
                if (pattern.containsMatchIn(file)) result.addAll(agents)
            }
        }
        return result
    }

    fun needsChangelog(prTitle: String): Boolean {
        return changelogSkipPrefixes.none { prTitle.startsWith(it) }
    }
}
