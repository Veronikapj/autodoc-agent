# autodoc-agent Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** PR이 머지될 때 코드 변경을 감지하고, 관련 문서를 AI 멀티에이전트로 자동 업데이트한 뒤 문서 PR을 생성하는 GitHub Marketplace Action을 구현한다.

**Architecture:** OrchestratorAgent가 PR diff를 분석해 관련 전문 에이전트(7개)를 A2A로 병렬 호출한다. 각 에이전트는 플랫폼별 템플릿을 기반으로 문서 초안을 작성하고, 결과를 새 PR로 자동 생성한다.

**Tech Stack:** Kotlin, Gradle, Koog (ai.koog), A2A HTTP, GitHub API, Confluence API, Docker, JUnit5, MockK

---

## Task 1: 프로젝트 스캐폴딩

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/Main.kt`

**Step 1: settings.gradle.kts 작성**

```kotlin
rootProject.name = "autodoc-agent"
```

**Step 2: build.gradle.kts 작성**

```kotlin
plugins {
    kotlin("jvm") version "2.1.0"
    application
}

group = "io.github.veronikapj"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://packages.jetbrains.team/maven/p/koog/public")
}

dependencies {
    implementation("ai.koog:koog-agents-core:0.2.0")
    implementation("ai.koog:koog-agents-features:0.2.0")
    implementation("ai.koog:koog-a2a:0.2.0")
    implementation("ai.koog:koog-prompt-executor-anthropic:0.2.0")
    implementation("io.ktor:ktor-client-cio:3.1.2")
    implementation("io.ktor:ktor-server-netty:3.1.2")
    implementation("com.charleskorn.kaml:kaml:0.67.0")
    implementation("org.kohsuke:github-api:1.326")
    implementation("ch.qos.logback:logback-classic:1.5.13")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}

application {
    mainClass.set("io.github.veronikapj.autodoc.MainKt")
}

tasks.test {
    useJUnitPlatform()
}
```

**Step 3: Main.kt 작성 (진입점 스켈레톤)**

```kotlin
package io.github.veronikapj.autodoc

fun main(args: Array<String>) {
    val prNumber = args.firstOrNull()?.toIntOrNull()
        ?: error("PR 번호를 인자로 전달해주세요. 예: ./gradlew run --args='42'")
    println("AutoDoc Agent 시작 - PR #$prNumber")
}
```

**Step 4: 빌드 확인**

```bash
cd /Users/piljubae/AndroidStudioProjects/autodoc-agent
./gradlew build
```
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add build.gradle.kts settings.gradle.kts src/ gradle/
git commit -m "chore: 프로젝트 초기 스캐폴딩"
```

---

## Task 2: 설정 파일 로더 (ConfigLoader)

**Files:**
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/config/AutoDocConfig.kt`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/config/ConfigLoader.kt`
- Create: `src/test/kotlin/io/github/veronikapj/autodoc/config/ConfigLoaderTest.kt`
- Create: `.autodoc/config.yml` (테스트용 샘플)

**Step 1: 테스트 작성**

```kotlin
// ConfigLoaderTest.kt
class ConfigLoaderTest {
    @Test
    fun `플랫폼 설정을 올바르게 파싱한다`() {
        val yaml = """
            platform: android
            documents:
              README.md: overwrite
              spec/*.md: append
        """.trimIndent()

        val config = ConfigLoader.parseYaml(yaml)

        assertEquals(Platform.ANDROID, config.platform)
        assertEquals(DocumentMode.OVERWRITE, config.documents["README.md"])
        assertEquals(DocumentMode.APPEND, config.documents["spec/*.md"])
    }

    @Test
    fun `플랫폼 미지정 시 generic으로 폴백한다`() {
        val yaml = "documents:\n  README.md: overwrite"
        val config = ConfigLoader.parseYaml(yaml)
        assertEquals(Platform.GENERIC, config.platform)
    }
}
```

**Step 2: 테스트 실행 (실패 확인)**

```bash
./gradlew test --tests "*.ConfigLoaderTest"
```
Expected: FAIL - ConfigLoader not found

**Step 3: 데이터 클래스 작성**

```kotlin
// AutoDocConfig.kt
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
```

**Step 4: ConfigLoader 구현**

```kotlin
// ConfigLoader.kt
object ConfigLoader {
    fun load(repoPath: String = "."): AutoDocConfig {
        val configFile = File("$repoPath/.autodoc/config.yml")
        if (!configFile.exists()) return AutoDocConfig()
        return parseYaml(configFile.readText())
    }

    fun parseYaml(yaml: String): AutoDocConfig {
        // kaml 라이브러리로 파싱
        val map = Yaml.default.decodeFromString(
            MapSerializer(String.serializer(), YamlElement.serializer()), yaml
        )
        val platform = map["platform"]?.let {
            Platform.valueOf((it as YamlScalar).content.uppercase())
        } ?: Platform.GENERIC

        val documents = (map["documents"] as? YamlMap)?.entries
            ?.associate { (k, v) ->
                k.content to DocumentMode.valueOf((v as YamlScalar).content.uppercase())
            } ?: emptyMap()

        return AutoDocConfig(platform = platform, documents = documents)
    }
}
```

**Step 5: 테스트 통과 확인**

```bash
./gradlew test --tests "*.ConfigLoaderTest"
```
Expected: PASS

**Step 6: Commit**

```bash
git add src/
git commit -m "feat: AutoDocConfig 및 ConfigLoader 구현"
```

---

## Task 3: TemplateResolver (플랫폼별 템플릿 로더)

**Files:**
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/platform/TemplateResolver.kt`
- Create: `src/test/kotlin/io/github/veronikapj/autodoc/platform/TemplateResolverTest.kt`

**Step 1: 테스트 작성**

```kotlin
class TemplateResolverTest {
    @Test
    fun `플랫폼 템플릿이 있으면 우선 로드한다`() {
        val resolver = TemplateResolver(platform = Platform.ANDROID, repoPath = ".")
        val template = resolver.resolve("README.md")
        assertTrue(template.contains("MIN_SDK") || template.contains("PROJECT_NAME"))
    }

    @Test
    fun `플랫폼 템플릿 없으면 generic으로 폴백한다`() {
        val resolver = TemplateResolver(platform = Platform.IOS, repoPath = ".")
        // ios/CHANGELOG.md.tmpl 없음 → generic 폴백
        val template = resolver.resolve("CHANGELOG.md")
        assertTrue(template.contains("Unreleased"))
    }

    @Test
    fun `타겟 레포 커스텀 템플릿을 최우선으로 로드한다`() {
        val tmpDir = createTempDir()
        val customDir = File(tmpDir, ".autodoc/templates/android").apply { mkdirs() }
        File(customDir, "README.md.tmpl").writeText("# CUSTOM TEMPLATE")

        val resolver = TemplateResolver(Platform.ANDROID, tmpDir.absolutePath)
        assertEquals("# CUSTOM TEMPLATE", resolver.resolve("README.md"))
    }
}
```

**Step 2: 테스트 실행 (실패 확인)**

```bash
./gradlew test --tests "*.TemplateResolverTest"
```
Expected: FAIL

**Step 3: TemplateResolver 구현**

```kotlin
// TemplateResolver.kt
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
        javaClass.getResourceAsStream("/templates/generic/$templateName")
            ?.bufferedReader()?.readText()
            ?: error("템플릿을 찾을 수 없습니다: $templateName")
    }
}
```

**Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "*.TemplateResolverTest"
```
Expected: PASS

**Step 5: Commit**

```bash
git add src/ .autodoc/
git commit -m "feat: TemplateResolver - 플랫폼별 템플릿 우선순위 로드"
```

---

## Task 4: PlatformConfig (파일 패턴 → 에이전트 매핑)

**Files:**
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/platform/PlatformConfig.kt`
- Create: `src/test/kotlin/io/github/veronikapj/autodoc/platform/PlatformConfigTest.kt`

**Step 1: 테스트 작성**

```kotlin
class PlatformConfigTest {
    @Test
    fun `Android build gradle 변경은 ArchDocAgent와 SetupDocAgent를 반환한다`() {
        val config = PlatformConfig(Platform.ANDROID)
        val agents = config.resolveAgents(listOf("app/build.gradle.kts"))
        assertTrue(AgentType.ARCH_DOC in agents)
        assertTrue(AgentType.SETUP_DOC in agents)
    }

    @Test
    fun `테스트 파일 변경은 TestDocAgent를 반환한다`() {
        val config = PlatformConfig(Platform.ANDROID)
        val agents = config.resolveAgents(listOf("app/src/test/LoginViewModelTest.kt"))
        assertTrue(AgentType.TEST_DOC in agents)
    }

    @Test
    fun `스킵 조건 파일은 빈 목록을 반환한다`() {
        val config = PlatformConfig(Platform.ANDROID)
        val agents = config.resolveAgents(listOf("app/src/main/res/drawable/icon.png"))
        assertTrue(agents.isEmpty())
    }

    @Test
    fun `ChangelogAgent는 PR 라벨로 판단한다`() {
        val config = PlatformConfig(Platform.ANDROID)
        assertTrue(config.needsChangelog("feat: 로그인 기능 추가"))
        assertFalse(config.needsChangelog("chore: 빌드 설정 변경"))
    }
}
```

**Step 2: 테스트 실행 (실패 확인)**

```bash
./gradlew test --tests "*.PlatformConfigTest"
```
Expected: FAIL

**Step 3: PlatformConfig 구현**

```kotlin
// PlatformConfig.kt
enum class AgentType {
    README, ARCH_DOC, API_DOC, TEST_DOC, CHANGELOG, SETUP_DOC, SPEC_DOC
}

class PlatformConfig(private val platform: Platform) {
    private val skipPatterns = listOf(
        Regex(".*\\.(png|jpg|gif|svg)$"),
        Regex("docs/.*\\.md$"),
        Regex("\\.github/.*"),
    )

    private val patterns: Map<Platform, List<Pair<Regex, List<AgentType>>>> = mapOf(
        Platform.ANDROID to listOf(
            Regex(".*/build\\.gradle\\.kts$|settings\\.gradle\\.kts$") to listOf(AgentType.ARCH_DOC, AgentType.SETUP_DOC),
            Regex(".*/(di|module)/.*\\.kt$|.*Module.*\\.kt$") to listOf(AgentType.ARCH_DOC),
            Regex(".*(Activity|Fragment)\\.kt$") to listOf(AgentType.ARCH_DOC),
            Regex(".*(Api|Service|Endpoint)\\.kt$") to listOf(AgentType.API_DOC),
            Regex(".*(Repository)\\.kt$") to listOf(AgentType.API_DOC, AgentType.ARCH_DOC),
            Regex(".*(Application|MainActivity)\\.kt$") to listOf(AgentType.README),
            Regex(".*Test\\.kt$|.*Fake.*\\.kt$|.*Mock.*\\.kt$") to listOf(AgentType.TEST_DOC),
            Regex("docs/spec/.*\\.md$") to listOf(AgentType.SPEC_DOC),
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
        ),
        Platform.FRONTEND to listOf(
            Regex(".*\\.(tsx|vue|svelte)$") to listOf(AgentType.ARCH_DOC),
            Regex(".*\\.stories\\.tsx$") to listOf(AgentType.ARCH_DOC),
            Regex(".*\\.(test|spec)\\.(ts|tsx)$") to listOf(AgentType.TEST_DOC),
        ),
        Platform.GENERIC to listOf(
            Regex(".*") to listOf(AgentType.README, AgentType.CHANGELOG),
        ),
    )

    fun resolveAgents(changedFiles: List<String>): Set<AgentType> {
        val result = mutableSetOf<AgentType>()
        for (file in changedFiles) {
            if (skipPatterns.any { it.matches(file) }) continue
            patterns[platform]?.forEach { (pattern, agents) ->
                if (pattern.containsMatchIn(file)) result.addAll(agents)
            }
        }
        return result
    }

    fun needsChangelog(prTitle: String): Boolean {
        val skipPrefixes = listOf("docs:", "chore:", "style:", "test:")
        return skipPrefixes.none { prTitle.startsWith(it) }
    }
}
```

**Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "*.PlatformConfigTest"
```
Expected: PASS

**Step 5: Commit**

```bash
git add src/
git commit -m "feat: PlatformConfig - 파일 패턴 기반 에이전트 선별"
```

---

## Task 5: 핵심 도구 구현 (Tools)

**Files:**
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/tools/ReadFileTool.kt`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/tools/ListFileTool.kt`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/tools/CodeSearchTool.kt`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/tools/GitHubTool.kt`
- Create: `src/test/kotlin/io/github/veronikapj/autodoc/tools/GitHubToolTest.kt`

**Step 1: ReadFileTool 구현**

```kotlin
// ReadFileTool.kt
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool

@Tool
@LLMDescription("파일 경로를 받아 내용을 반환합니다")
fun readFile(
    @LLMDescription("읽을 파일의 절대 또는 상대 경로") path: String
): String {
    val file = File(path)
    if (!file.exists()) return "파일을 찾을 수 없습니다: $path"
    if (file.length() > 500_000) return "파일이 너무 큽니다 (500KB 초과): $path"
    return file.readText()
}
```

**Step 2: ListFileTool 구현**

```kotlin
// ListFileTool.kt
@Tool
@LLMDescription("디렉터리 구조를 트리 형태로 반환합니다")
fun listFiles(
    @LLMDescription("탐색할 디렉터리 경로") path: String,
    @LLMDescription("최대 깊이 (기본 3)") maxDepth: Int = 3
): String {
    val root = File(path)
    if (!root.exists()) return "경로를 찾을 수 없습니다: $path"
    return buildString {
        root.walkTopDown()
            .maxDepth(maxDepth)
            .filter { !it.name.startsWith(".") }
            .forEach { file ->
                val depth = file.relativeTo(root).path.count { it == '/' }
                appendLine("${"  ".repeat(depth)}${if (file.isDirectory) "📁" else "📄"} ${file.name}")
            }
    }
}
```

**Step 3: CodeSearchTool 구현**

```kotlin
// CodeSearchTool.kt
@Tool
@LLMDescription("ripgrep으로 코드 패턴을 검색합니다")
fun codeSearch(
    @LLMDescription("검색할 정규식 패턴") pattern: String,
    @LLMDescription("검색할 디렉터리 (기본: 현재 디렉터리)") path: String = ".",
): String {
    val result = ProcessBuilder("rg", "--line-number", "--max-count=20", pattern, path)
        .redirectErrorStream(true)
        .start()
        .inputStream.bufferedReader().readText()
    return result.ifBlank { "검색 결과 없음" }
}
```

**Step 4: GitHubTool 테스트 작성**

```kotlin
// GitHubToolTest.kt
class GitHubToolTest {
    @Test
    fun `PR 제목에서 타입을 올바르게 추출한다`() {
        assertEquals("feat", extractPRType("feat: 로그인 기능 추가"))
        assertEquals("fix", extractPRType("fix: 크래시 수정"))
        assertNull(extractPRType("로그인 기능 추가"))
    }
}
```

**Step 5: GitHubTool 구현**

```kotlin
// GitHubTool.kt
class GitHubTool(private val token: String) {
    private val github = GitHubBuilder().withOAuthToken(token).build()

    fun fetchPRDiff(repoName: String, prNumber: Int): List<String> {
        val repo = github.getRepository(repoName)
        val pr = repo.getPullRequest(prNumber)
        return pr.listFiles().toList().map { it.filename }
    }

    fun fetchPRInfo(repoName: String, prNumber: Int): PRInfo {
        val repo = github.getRepository(repoName)
        val pr = repo.getPullRequest(prNumber)
        return PRInfo(
            number = prNumber,
            title = pr.title,
            body = pr.body ?: "",
            labels = pr.labels.map { it.name },
        )
    }

    fun createDocsPR(
        repoName: String,
        baseBranch: String,
        prNumber: Int,
        changedDocs: Map<String, String>,
        summary: String,
    ) {
        val repo = github.getRepository(repoName)
        val branchName = "docs/auto-update-pr-$prNumber"

        // 브랜치 생성 + 파일 커밋 + PR 오픈
        val baseRef = repo.getRef("heads/$baseBranch")
        repo.createRef("refs/heads/$branchName", baseRef.`object`.sha)

        changedDocs.forEach { (path, content) ->
            try {
                val existing = repo.getFileContent(path)
                repo.createContent()
                    .path(path).content(content)
                    .message("docs: $path 업데이트 (PR #$prNumber)")
                    .sha(existing.sha)
                    .branch(branchName)
                    .commit()
            } catch (e: Exception) {
                repo.createContent()
                    .path(path).content(content)
                    .message("docs: $path 생성 (PR #$prNumber)")
                    .branch(branchName)
                    .commit()
            }
        }

        repo.createPullRequest(
            "docs: PR #$prNumber 변경사항 문서 반영",
            branchName,
            baseBranch,
            summary,
        )
    }
}

data class PRInfo(
    val number: Int,
    val title: String,
    val body: String,
    val labels: List<String>,
)

fun extractPRType(title: String): String? =
    Regex("^(feat|fix|refactor|docs|chore|style|test):").find(title)?.groupValues?.get(1)
```

**Step 6: 테스트 통과 확인**

```bash
./gradlew test --tests "*.GitHubToolTest"
```
Expected: PASS

**Step 7: Commit**

```bash
git add src/
git commit -m "feat: 핵심 도구 구현 (ReadFile, ListFile, CodeSearch, GitHub)"
```

---

## Task 6: A2A 인프라 세팅

koog-practice의 A2A 코드를 베이스로 autodoc-agent에 맞게 적용한다.

**Files:**
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/a2a/DocAgentExecutor.kt`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/a2a/A2AServerManager.kt`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/a2a/A2AClientManager.kt`

**Step 1: DocAgentExecutor 구현**

```kotlin
// DocAgentExecutor.kt - 전문 에이전트를 A2A로 노출하는 실행기
class DocAgentExecutor(
    private val agent: SpecialistDocAgent,
) : AgentExecutor {
    override suspend fun execute(
        request: TaskRequest,
        eventStream: FlowCollector<AgentEvent>,
    ) {
        val userMessage = request.params.messages
            .filterIsInstance<Message>()
            .lastOrNull { it.role == Role.User }
            ?.parts?.filterIsInstance<TextPart>()
            ?.joinToString("\n") { it.text }
            ?: return

        val result = agent.process(userMessage)
        eventStream.emit(AgentEvent.Message(
            Message(
                messageId = UUID.randomUUID().toString(),
                role = Role.Agent,
                parts = listOf(TextPart(result)),
            )
        ))
    }
}
```

**Step 2: A2AServerManager 구현**

```kotlin
// A2AServerManager.kt
class A2AServerManager(
    private val agents: Map<AgentType, SpecialistDocAgent>,
) {
    private val runningServers = mutableListOf<RunningServer>()
    private val serverJobs = mutableListOf<Job>()
    private var serverScope: CoroutineScope? = null

    suspend fun start(): Map<AgentType, Int> = coroutineScope {
        val ports = agents.keys.associateWith {
            ServerSocket(0).use { s -> s.localPort }
        }
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        serverScope = scope

        ports.forEach { (agentType, port) ->
            val agent = agents[agentType] ?: return@forEach
            val card = AgentCard(
                name = agentType.name,
                description = "autodoc ${agentType.name} agent",
                url = "http://localhost:$port/a2a",
                version = "1.0.0",
            )
            val executor = DocAgentExecutor(agent)
            val server = A2AServer(agentExecutor = executor, agentCard = card)
            val transport = HttpJSONRPCServerTransport(requestHandler = server)
            val job = scope.launch {
                transport.start(engineFactory = Netty, port = port, path = "/a2a", wait = true, agentCard = card)
            }
            runningServers.add(RunningServer(agentType, port, transport))
            serverJobs.add(job)
        }

        waitForServersReady(ports.values.toList())
        ports
    }

    fun stop() {
        serverJobs.forEach { it.cancel() }
        serverScope?.cancel()
        runningServers.clear()
        serverJobs.clear()
    }

    private suspend fun waitForServersReady(ports: List<Int>, maxRetries: Int = 20) {
        val client = HttpClient(CIO)
        ports.forEach { port ->
            repeat(maxRetries) {
                try {
                    client.get("http://localhost:$port/.well-known/agent-card.json")
                    return@forEach
                } catch (_: Exception) { delay(500) }
            }
        }
        client.close()
    }
}

data class RunningServer(val agentType: AgentType, val port: Int, val transport: HttpJSONRPCServerTransport)
```

**Step 3: A2AClientManager 구현**

```kotlin
// A2AClientManager.kt
class A2AClientManager {
    private val clients = mutableMapOf<AgentType, A2AClient>()

    suspend fun connectAll(ports: Map<AgentType, Int>) = coroutineScope {
        ports.map { (agentType, port) ->
            async {
                val httpClient = HttpClient(CIO) {
                    install(HttpTimeout) {
                        requestTimeoutMillis = 300_000
                        connectTimeoutMillis = 10_000
                    }
                }
                val transport = HttpJSONRPCClientTransport(
                    url = "http://localhost:$port/a2a",
                    baseHttpClient = httpClient,
                )
                val resolver = UrlAgentCardResolver("http://localhost:$port", "/.well-known/agent-card.json")
                val client = A2AClient(transport = transport, agentCardResolver = resolver)
                client.connect()
                agentType to client
            }
        }.awaitAll().forEach { (type, client) -> clients[type] = client }
    }

    suspend fun sendMessage(agentType: AgentType, message: String): String {
        val client = clients[agentType] ?: error("클라이언트 없음: $agentType")
        val msg = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.User,
            parts = listOf(TextPart(message)),
            contextId = "doc-${UUID.randomUUID()}",
        )
        val response = client.sendMessage(Request(data = MessageSendParams(msg)))
        return (response.data as? Message)?.parts
            ?.filterIsInstance<TextPart>()
            ?.joinToString("\n") { it.text }
            ?: "응답 없음"
    }
}
```

**Step 4: Commit**

```bash
git add src/
git commit -m "feat: A2A 인프라 세팅 (Executor, Server, Client)"
```

---

## Task 7: 전문 에이전트 구현 (SpecialistDocAgent)

**Files:**
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/agent/specialist/SpecialistDocAgent.kt`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/agent/specialist/ReadmeAgent.kt`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/agent/specialist/ArchDocAgent.kt`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/agent/specialist/ApiDocAgent.kt`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/agent/specialist/TestDocAgent.kt`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/agent/specialist/ChangelogAgent.kt`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/agent/specialist/SetupDocAgent.kt`
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/agent/specialist/SpecDocAgent.kt`

**Step 1: SpecialistDocAgent 인터페이스**

```kotlin
// SpecialistDocAgent.kt
interface SpecialistDocAgent {
    suspend fun process(request: String): String
}
```

**Step 2: 공통 에이전트 팩토리 함수**

```kotlin
// AgentFactory.kt
fun buildDocAgent(
    executor: MultiLLMPromptExecutor,
    systemPrompt: String,
    agentName: String,
): AIAgent {
    val registry = ToolRegistry {
        tool(::readFile)
        tool(::listFiles)
        tool(::codeSearch)
    }
    return AIAgent(
        promptExecutor = executor,
        agentConfig = AIAgentConfig(
            prompt = prompt(agentName, params = AnthropicParams(maxTokens = 8192)) {
                system(systemPrompt)
            },
            model = AnthropicModels.Sonnet_4_5,
            maxAgentIterations = 30,
        ),
        toolRegistry = registry,
        installFeatures = {
            install(EventHandler) {
                onToolCallStarting { println("🔧 [${agentName}] ${it.toolName}"); it.toolName }
                onToolCallCompleted { println("✅ [${agentName}] ${it.toolName}"); it.toolName }
            }
        }
    )
}
```

**Step 3: ReadmeAgent 구현**

```kotlin
// ReadmeAgent.kt
class ReadmeAgent(
    private val executor: MultiLLMPromptExecutor,
    private val templateResolver: TemplateResolver,
) : SpecialistDocAgent {

    override suspend fun process(request: String): String {
        val template = templateResolver.resolve("README.md")
        val agent = buildDocAgent(executor, systemPrompt(template), "readme-agent")
        return agent.run(request)
    }

    private fun systemPrompt(template: String) = """
        당신은 README.md 문서를 작성하는 전문 에이전트입니다.
        
        ## 행동 규칙
        1. 기존 README.md가 있으면 반드시 전체를 읽고 시작한다
        2. 기존 내용의 톤/스타일을 유지한다 (임의로 구조를 바꾸지 않는다)
        3. PR diff에서 변경된 것만 반영한다
        4. 신규 생성 시 아래 템플릿을 기반으로 실제 값을 코드에서 추출해 채운다
        5. 확인할 수 없는 내용은 추측하지 않고 TODO로 표시한다
        6. 최종 결과로 README.md의 전체 내용만 출력한다 (설명 없이)
        
        ## 템플릿
        $template
    """.trimIndent()
}
```

**Step 4: 나머지 전문 에이전트 구현 (ArchDocAgent, ApiDocAgent, TestDocAgent, ChangelogAgent, SetupDocAgent, SpecDocAgent)**

각 에이전트는 ReadmeAgent와 동일한 구조로, systemPrompt만 설계 문서의 행동 규칙을 참고해 작성한다.

핵심 차이:
- `ArchDocAgent`: Mermaid 그래프 생성 규칙 포함
- `TestDocAgent`: given/when/then 시나리오 형식 강제
- `ChangelogAgent`: PR 제목 prefix 분류 로직
- `SpecDocAgent`: Append 모드 - 기존 내용 하단에 변경 이력 추가
- `SetupDocAgent`: build.gradle.kts에서 SDK 버전 추출

**Step 5: Commit**

```bash
git add src/
git commit -m "feat: 전문 에이전트 7개 구현"
```

---

## Task 8: OrchestratorAgent 구현

**Files:**
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/agent/OrchestratorAgent.kt`
- Create: `src/test/kotlin/io/github/veronikapj/autodoc/agent/OrchestratorAgentTest.kt`

**Step 1: 테스트 작성**

```kotlin
class OrchestratorAgentTest {
    @Test
    fun `변경 파일 기반으로 올바른 에이전트를 선별한다`() = runTest {
        val mockClient = mockk<A2AClientManager>()
        val platformConfig = PlatformConfig(Platform.ANDROID)

        val changedFiles = listOf("app/build.gradle.kts", "app/src/main/LoginActivity.kt")
        val agents = platformConfig.resolveAgents(changedFiles)

        assertTrue(AgentType.ARCH_DOC in agents)
        assertTrue(AgentType.SETUP_DOC in agents)
        assertTrue(AgentType.README in agents)
    }
}
```

**Step 2: 테스트 실행 (실패 확인)**

```bash
./gradlew test --tests "*.OrchestratorAgentTest"
```

**Step 3: OrchestratorAgent 구현**

```kotlin
// OrchestratorAgent.kt
class OrchestratorAgent(
    private val clientManager: A2AClientManager,
    private val platformConfig: PlatformConfig,
    private val githubTool: GitHubTool,
    private val repoName: String,
) {
    suspend fun run(prNumber: Int): Map<String, String> = coroutineScope {
        // 1. PR diff 분석
        val changedFiles = githubTool.fetchPRDiff(repoName, prNumber)
        val prInfo = githubTool.fetchPRInfo(repoName, prNumber)

        println("📋 변경 파일 ${changedFiles.size}개 분석 중...")

        // 2. 필요한 에이전트 선별
        val agentsToCall = platformConfig.resolveAgents(changedFiles).toMutableSet()
        if (platformConfig.needsChangelog(prInfo.title)) {
            agentsToCall.add(AgentType.CHANGELOG)
        }

        println("🎯 호출할 에이전트: ${agentsToCall.joinToString { it.name }}")

        val request = buildRequest(prNumber, prInfo, changedFiles)

        // 3. 병렬 A2A 호출
        val results = agentsToCall.map { agentType ->
            async {
                try {
                    val result = clientManager.sendMessage(agentType, request)
                    agentType to result
                } catch (e: Exception) {
                    agentType to "⚠️ ${agentType.name} 실패: ${e.message}"
                }
            }
        }.awaitAll().toMap()

        // 4. 결과를 문서 경로 → 내용으로 매핑
        results.mapKeys { (agentType, _) -> agentType.toDocPath() }
    }

    private fun buildRequest(prNumber: Int, prInfo: PRInfo, changedFiles: List<String>) = """
        PR #$prNumber: ${prInfo.title}
        
        변경된 파일:
        ${changedFiles.joinToString("\n") { "- $it" }}
        
        위 변경사항을 분석하고 담당 문서를 업데이트해주세요.
        결과로 문서의 전체 내용만 출력하세요.
    """.trimIndent()

    private fun AgentType.toDocPath() = when (this) {
        AgentType.README -> "README.md"
        AgentType.ARCH_DOC -> "docs/architecture.md"
        AgentType.API_DOC -> "docs/api.md"
        AgentType.TEST_DOC -> "docs/testing.md"
        AgentType.CHANGELOG -> "CHANGELOG.md"
        AgentType.SETUP_DOC -> "docs/setup.md"
        AgentType.SPEC_DOC -> "docs/spec/latest.md"
    }
}
```

**Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "*.OrchestratorAgentTest"
```
Expected: PASS

**Step 5: Commit**

```bash
git add src/
git commit -m "feat: OrchestratorAgent 구현"
```

---

## Task 9: Main.kt 완성 및 통합

**Files:**
- Modify: `src/main/kotlin/io/github/veronikapj/autodoc/Main.kt`

**Step 1: Main.kt 완성**

```kotlin
// Main.kt
@OptIn(ExperimentalAgentsApi::class)
fun main(args: Array<String>) = runBlocking {
    val prNumber = args.firstOrNull()?.toIntOrNull()
        ?: error("PR 번호를 인자로 전달해주세요")

    val anthropicKey = System.getenv("ANTHROPIC_API_KEY")
        ?: error("ANTHROPIC_API_KEY 환경변수가 없습니다")
    val githubToken = System.getenv("GITHUB_TOKEN")
        ?: error("GITHUB_TOKEN 환경변수가 없습니다")
    val repoName = System.getenv("GITHUB_REPOSITORY")
        ?: error("GITHUB_REPOSITORY 환경변수가 없습니다")
    val repoPath = System.getenv("TARGET_REPO_PATH") ?: "."

    // 설정 로드
    val config = ConfigLoader.load(repoPath)
    println("🚀 AutoDoc Agent 시작 - 플랫폼: ${config.platform}, PR #$prNumber")

    // LLM 실행기
    val executor = MultiLLMPromptExecutor(
        AnthropicLLMClient(AnthropicConfig(apiKey = anthropicKey))
    )

    // 템플릿 리졸버
    val templateResolver = TemplateResolver(config.platform, repoPath)
    val platformConfig = PlatformConfig(config.platform)
    val githubTool = GitHubTool(githubToken)

    // 전문 에이전트 생성
    val specialists = mapOf(
        AgentType.README to ReadmeAgent(executor, templateResolver),
        AgentType.ARCH_DOC to ArchDocAgent(executor, templateResolver),
        AgentType.API_DOC to ApiDocAgent(executor, templateResolver),
        AgentType.TEST_DOC to TestDocAgent(executor, templateResolver),
        AgentType.CHANGELOG to ChangelogAgent(executor, templateResolver),
        AgentType.SETUP_DOC to SetupDocAgent(executor, templateResolver),
        AgentType.SPEC_DOC to SpecDocAgent(executor, templateResolver, config.spec),
    )

    // A2A 서버 시작
    val serverManager = A2AServerManager(specialists)
    val ports = serverManager.start()

    // A2A 클라이언트 연결
    val clientManager = A2AClientManager()
    clientManager.connectAll(ports)

    // Orchestrator 실행
    val orchestrator = OrchestratorAgent(clientManager, platformConfig, githubTool, repoName)
    val changedDocs = orchestrator.run(prNumber)

    println("📝 업데이트된 문서: ${changedDocs.keys.joinToString()}")

    // 문서 PR 생성
    val summary = buildPRSummary(changedDocs.keys.toList(), prNumber)
    githubTool.createDocsPR(repoName, "main", prNumber, changedDocs, summary)

    println("✅ 문서 PR 생성 완료!")

    serverManager.stop()
}

private fun buildPRSummary(updatedDocs: List<String>, prNumber: Int) = """
    ## AutoDoc Agent 자동 문서 업데이트
    
    PR #$prNumber의 변경사항을 분석하여 다음 문서를 업데이트했습니다:
    
    ${updatedDocs.joinToString("\n") { "- `$it`" }}
    
    > 내용을 검토 후 머지해주세요.
""".trimIndent()
```

**Step 2: 로컬 통합 테스트**

```bash
export ANTHROPIC_API_KEY=your_key
export GITHUB_TOKEN=your_token
export GITHUB_REPOSITORY=Veronikapj/autodoc-agent
./gradlew run --args="1"
```
Expected: A2A 서버 시작 → 에이전트 호출 → 문서 PR 생성

**Step 3: Commit**

```bash
git add src/
git commit -m "feat: Main.kt 완성 - 전체 파이프라인 통합"
```

---

## Task 10: 템플릿 파일 배치

**Files:**
- Create: `src/main/resources/templates/generic/*.tmpl` (4개)
- Create: `src/main/resources/templates/android/*.tmpl` (4개)
- Create: `src/main/resources/templates/ios/*.tmpl` (3개)
- Create: `src/main/resources/templates/backend/*.tmpl` (4개)
- Create: `src/main/resources/templates/frontend/*.tmpl` (3개)

설계 문서(`docs/plans/2026-04-14-autodoc-agent-design.md`)의 템플릿 내용을 그대로 각 파일로 저장한다.

**Step 1: 디렉터리 생성 및 파일 배치**

```bash
mkdir -p src/main/resources/templates/{generic,android,ios,backend,frontend}
```

각 `.tmpl` 파일에 설계 문서의 템플릿 내용을 복사한다.

**Step 2: 템플릿 로드 검증**

```bash
./gradlew test --tests "*.TemplateResolverTest"
```
Expected: PASS

**Step 3: Commit**

```bash
git add src/main/resources/
git commit -m "feat: 플랫폼별 템플릿 17개 리소스 배치"
```

---

## Task 11: Dockerfile + action.yml (배포)

**Files:**
- Create: `Dockerfile`
- Create: `action.yml`
- Create: `.github/workflows/autodoc.yml`
- Create: `.github/workflows/publish.yml`

**Step 1: Dockerfile 작성**

```dockerfile
FROM gradle:8.10-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*-all.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Step 2: action.yml 작성**

```yaml
name: 'AutoDoc Agent'
description: 'AI agent that automatically updates docs when PRs are merged'
author: 'Veronikapj'

inputs:
  anthropic-api-key:
    description: 'Anthropic API Key'
    required: true
  confluence-token:
    description: 'Confluence API Token (optional)'
    required: false

runs:
  using: 'docker'
  image: 'Dockerfile'

branding:
  icon: 'file-text'
  color: 'blue'
```

**Step 3: autodoc.yml (타겟 레포 예시 워크플로우) 작성**

```yaml
name: AutoDoc Agent

on:
  pull_request:
    types: [closed]

jobs:
  autodoc:
    if: github.event.pull_request.merged == true
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - uses: Veronikapj/autodoc-agent@v1
        with:
          anthropic-api-key: ${{ secrets.ANTHROPIC_API_KEY }}
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          PR_NUMBER: ${{ github.event.pull_request.number }}
          GITHUB_REPOSITORY: ${{ github.repository }}
```

**Step 4: publish.yml 작성**

```yaml
name: Publish to Marketplace

on:
  push:
    tags: ['v*']

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build Docker image
        run: docker build -t autodoc-agent .
      - name: Tag and push
        run: |
          docker tag autodoc-agent ghcr.io/veronikapj/autodoc-agent:${{ github.ref_name }}
          docker push ghcr.io/veronikapj/autodoc-agent:${{ github.ref_name }}
```

**Step 5: Docker 빌드 확인**

```bash
docker build -t autodoc-agent .
docker run -e ANTHROPIC_API_KEY=test -e GITHUB_TOKEN=test autodoc-agent 42
```
Expected: "PR 번호를 인자로 전달해주세요" 또는 API 연결 시도

**Step 6: Commit**

```bash
git add Dockerfile action.yml .github/
git commit -m "feat: Docker + GitHub Marketplace Action 배포 설정"
```

---

## Task 12: ConfluenceTool 구현 (SpecDocAgent 확장)

**Files:**
- Create: `src/main/kotlin/io/github/veronikapj/autodoc/tools/ConfluenceTool.kt`
- Create: `src/test/kotlin/io/github/veronikapj/autodoc/tools/ConfluenceToolTest.kt`

**Step 1: 테스트 작성**

```kotlin
class ConfluenceToolTest {
    @Test
    fun `페이지 ID로 마크다운 변환 요청을 올바르게 구성한다`() {
        val tool = ConfluenceTool(
            baseUrl = "https://test.atlassian.net",
            token = "test-token",
        )
        val url = tool.buildPageUrl(pageId = 123456L)
        assertEquals("https://test.atlassian.net/wiki/rest/api/content/123456?expand=body.storage", url)
    }
}
```

**Step 2: ConfluenceTool 구현**

```kotlin
// ConfluenceTool.kt
class ConfluenceTool(
    private val baseUrl: String,
    private val token: String,
) {
    private val client = HttpClient(CIO) {
        install(Auth) {
            bearer { loadTokens { BearerTokens(token, "") } }
        }
    }

    fun buildPageUrl(pageId: Long) =
        "$baseUrl/wiki/rest/api/content/$pageId?expand=body.storage"

    suspend fun fetchPage(pageId: Long): String {
        val response: String = client.get(buildPageUrl(pageId)).body()
        // HTML → Markdown 변환 (간단 구현)
        return convertHtmlToMarkdown(response)
    }

    private fun convertHtmlToMarkdown(html: String): String {
        return html
            .replace(Regex("<h1[^>]*>"), "# ")
            .replace(Regex("<h2[^>]*>"), "## ")
            .replace(Regex("<h3[^>]*>"), "### ")
            .replace(Regex("<p[^>]*>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .trim()
    }
}
```

**Step 3: 테스트 통과 확인**

```bash
./gradlew test --tests "*.ConfluenceToolTest"
```
Expected: PASS

**Step 4: Commit**

```bash
git add src/
git commit -m "feat: ConfluenceTool 구현 - Confluence API 연동"
```

---

## Task 13: 최종 검증

**Step 1: 전체 테스트 실행**

```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS

**Step 2: 빌드 확인**

```bash
./gradlew shadowJar
ls -la build/libs/
```
Expected: `autodoc-agent-1.0.0-all.jar` 생성

**Step 3: Docker 빌드 최종 확인**

```bash
docker build -t autodoc-agent:latest .
```
Expected: 빌드 성공

**Step 4: 최종 커밋 + 태그**

```bash
git add .
git commit -m "chore: 최종 빌드 및 검증 완료"
git tag v1.0.0
git push origin main --tags
```

---

## 구현 순서 요약

```
Task 1  프로젝트 스캐폴딩
Task 2  ConfigLoader
Task 3  TemplateResolver
Task 4  PlatformConfig
Task 5  핵심 도구 (Tools)
Task 6  A2A 인프라
Task 7  전문 에이전트 7개
Task 8  OrchestratorAgent
Task 9  Main.kt 통합
Task 10 템플릿 파일 배치
Task 11 Dockerfile + action.yml
Task 12 ConfluenceTool
Task 13 최종 검증
```

각 Task는 독립적으로 커밋. Task 1-5는 순서대로, Task 6-9는 순서대로, Task 10-12는 병렬 가능.
