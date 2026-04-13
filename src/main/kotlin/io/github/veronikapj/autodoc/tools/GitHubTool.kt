package io.github.veronikapj.autodoc.tools

import org.kohsuke.github.GitHubBuilder
import java.util.Base64

data class PRInfo(
    val number: Int,
    val title: String,
    val body: String,
    val labels: List<String>,
    val baseBranch: String,
)

fun extractPRType(title: String): String? =
    Regex("^(feat|fix|refactor|docs|chore|style|test):").find(title)?.groupValues?.get(1)

class GitHubTool(private val token: String) {
    private val github = GitHubBuilder().withOAuthToken(token).build()

    fun fetchChangedFiles(repoName: String, prNumber: Int): List<String> {
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
            baseBranch = pr.base.ref,
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

        // 브랜치 생성
        val baseRef = repo.getRef("heads/$baseBranch")
        try {
            repo.createRef("refs/heads/$branchName", baseRef.`object`.sha)
        } catch (e: Exception) {
            println("브랜치 이미 존재: $branchName")
        }

        // 각 문서 파일 커밋
        changedDocs.forEach { (path, content) ->
            val encodedContent = Base64.getEncoder().encodeToString(content.toByteArray())
            try {
                val existing = repo.getFileContent(path, branchName)
                repo.createContent()
                    .path(path)
                    .content(encodedContent)
                    .message("docs: $path 업데이트 (PR #$prNumber)")
                    .sha(existing.sha)
                    .branch(branchName)
                    .commit()
            } catch (e: Exception) {
                repo.createContent()
                    .path(path)
                    .content(encodedContent)
                    .message("docs: $path 생성 (PR #$prNumber)")
                    .branch(branchName)
                    .commit()
            }
        }

        // PR 생성
        try {
            repo.createPullRequest(
                "docs: PR #$prNumber 변경사항 문서 반영",
                branchName,
                baseBranch,
                summary,
            )
            println("✅ 문서 PR 생성 완료")
        } catch (e: Exception) {
            println("⚠️ PR 생성 실패: ${e.message}")
        }
    }
}
