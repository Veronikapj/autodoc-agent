package io.github.veronikapj.autodoc.platform

import io.github.veronikapj.autodoc.config.Platform
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.nio.file.Files

class TemplateResolverTest {

    @Test
    fun `generic 폴백 템플릿이 반환된다`() {
        val resolver = TemplateResolver(Platform.GENERIC, ".")
        val template = resolver.resolve("README.md")
        assertNotNull(template)
        assertTrue(template.isNotBlank())
    }

    @Test
    fun `타겟 레포 커스텀 템플릿을 최우선으로 로드한다`() {
        val tmpDir = Files.createTempDirectory("autodoc-test").toFile()
        val customDir = File(tmpDir, ".autodoc/templates/android").apply { mkdirs() }
        File(customDir, "README.md.tmpl").writeText("# CUSTOM TEMPLATE")

        val resolver = TemplateResolver(Platform.ANDROID, tmpDir.absolutePath)
        assertEquals("# CUSTOM TEMPLATE", resolver.resolve("README.md"))

        tmpDir.deleteRecursively()
    }

    @Test
    fun `존재하지 않는 템플릿은 최후 폴백을 반환한다`() {
        val resolver = TemplateResolver(Platform.GENERIC, "/nonexistent")
        val template = resolver.resolve("nonexistent.md")
        assertTrue(template.contains("PROJECT_NAME"))
    }
}
