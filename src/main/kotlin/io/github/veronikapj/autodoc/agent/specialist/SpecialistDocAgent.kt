package io.github.veronikapj.autodoc.agent.specialist

interface SpecialistDocAgent {
    suspend fun process(request: String): String
}
