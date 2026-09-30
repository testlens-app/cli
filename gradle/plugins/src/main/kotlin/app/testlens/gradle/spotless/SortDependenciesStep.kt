package app.testlens.gradle.spotless

import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep
import java.util.Locale

class SortDependenciesStep : java.io.Serializable {
    companion object {
        fun create(): FormatterStep {
            return FormatterStep.create(
                "SortDependenciesStep",
                SortDependenciesStep(),
                SortDependenciesStep::toFormatter,
            )
        }
    }

    fun toFormatter(): FormatterFunc {
        return FormatterFunc { unixStr ->
            val lines = unixStr.split('\n')
            val blockStartIndex = lines.indexOfFirst {
                it.startsWith("dependencies {") || it.startsWith("dependencies.constraints {")
            }
            if (blockStartIndex == -1 || lines[blockStartIndex].contains("}")) {
                unixStr // no 'dependencies {} block' or only one line
            } else {
                val blockEndIndex = blockStartIndex + lines.subList(blockStartIndex, lines.size).indexOf("}")
                val declarations =
                    lines.subList(blockStartIndex + 1, blockEndIndex).filter { it.contains("(") }.map { parse(it) }
                val comparator =
                    Comparator<DependencyDeclaration> { a, b ->
                        when {
                            a.sourceSet.compareTo(b.sourceSet) != 0 -> a.sourceSet.compareTo(b.sourceSet)
                            a.scope.ordinal != b.scope.ordinal -> a.scope.ordinal.compareTo(b.scope.ordinal)
                            a.isProject && !b.isProject -> -1
                            !a.isProject && b.isProject -> 1
                            else -> a.line.compareTo(b.line)
                        }
                    }

                val sorted = declarations.sortedWith(comparator)
                val blockStart = lines.subList(0, blockStartIndex + 1)
                val blockEnd = lines.subList(blockEndIndex, lines.size)

                val block = buildList {
                    var previousSourceSet: String? = sorted.firstOrNull()?.sourceSet
                    sorted.forEach { declaration ->
                        if (previousSourceSet != declaration.sourceSet) add("")
                        add(declaration.line)
                        previousSourceSet = declaration.sourceSet
                    }
                }
                (blockStart + block + blockEnd).joinToString("\n")
            }
        }
    }

    private fun parse(line: String): DependencyDeclaration {
        val fullScope = line.trim().substring(0, line.trim().indexOf("("))
        var scope = Scope.Api
        var sourceSet = ""
        val isProject = line.contains("(projects.") || line.contains("project(path)")
        val containsPlainString = line.substringBefore("{").contains(""""""")

        if (containsPlainString) {
            throw RuntimeException("Discouraged dependency notation: ${line.trim()}")
        }

        Scope.entries.forEach { scopeCandidate ->
            if (fullScope == scopeCandidate.name.replaceFirstChar { it.lowercase(Locale.getDefault()) }) {
                scope = scopeCandidate
                sourceSet = ""
            } else if (fullScope.endsWith(scopeCandidate.name)) {
                scope = scopeCandidate
                sourceSet = fullScope.dropLast(scopeCandidate.name.length)
            }
        }
        return DependencyDeclaration(sourceSet, scope, isProject, line)
    }

    private enum class Scope {
        Api,
        Implementation,
        CompileOnlyApi,
        CompileOnly,
        RuntimeOnly,
        AnnotationProcessor,
        ProvidedCompile,
        ProvidedRuntime,
        InstrumentationJar,
    }

    private data class DependencyDeclaration(
        val sourceSet: String,
        val scope: Scope,
        val isProject: Boolean,
        val line: String,
    )
}
