package io.github.veronikapj.autodoc

fun main(args: Array<String>) {
    val prNumber = args.firstOrNull()?.toIntOrNull()
        ?: error("PR 번호를 인자로 전달해주세요. 예: ./gradlew run --args='42'")
    println("AutoDoc Agent 시작 - PR #$prNumber")
}
