// Gate timing record — change `speed-up-build-test-gate`, spec `build-test-gate`
// ("Every gate run records its phase timings").
//
// Applied only for gate runs:
//     ./gradlew -I tools/gate-timings.init.gradle.kts <tasks…>
// and written to <invocation directory>/build/gate-timings.json. Read it with
// tools/gate-timing-report.sh; the report and this record are covered by
// tools/gate-timings-selftest.sh (fixtures, no Gradle, no device).
//
// Threading: `onFinish` is called on the thread that finishes the task — Gradle may finish
// tasks from several threads in a parallel build — so entries are collected in a concurrent
// queue. `close()` runs once when the build ends, on the build thread, and writes the file.
//
// Why a build service rather than a task-graph listener: in Gradle 9.6.1 both
// `TaskExecutionGraph.beforeTask` and `Gradle.buildFinished` are deprecated (the Kotlin DSL
// rejects them as errors), `TaskState` no longer reports a duration, and this script must not
// add an action to a task — a task action would change the task's action class, and with it
// the build-cache key, so every gate run under this script would miss the cache and
// re-execute everything the record exists to measure.

import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFailureResult
import org.gradle.tooling.events.task.TaskOperationDescriptor
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.events.task.TaskSuccessResult

interface GateTimingsParameters : BuildServiceParameters {
    val outputDir: Property<String>
    val requestedTasks: ListProperty<String>
}

abstract class GateTimingsService :
    BuildService<GateTimingsParameters>,
    OperationCompletionListener,
    AutoCloseable {

    private val startedAt: Instant = Instant.now()
    private val entries = ConcurrentLinkedQueue<String>()
    private var taskFailures = 0

    private fun jsonString(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch < ' ') out.append('?') else out.append(ch)
            }
        }
        out.append('"')
        return out.toString()
    }

    override fun onFinish(event: FinishEvent) {
        val descriptor = event.descriptor
        val taskPath = (descriptor as? TaskOperationDescriptor)?.taskPath ?: descriptor.name
        val result = event.result
        val outcome = when {
            result is TaskFailureResult -> "failed"
            result is TaskSkippedResult -> "skipped"
            result is TaskSuccessResult -> when {
                result.isFromCache -> "from-cache"
                result.isUpToDate -> "up-to-date"
                else -> "executed"
            }
            else -> "unknown"
        }
        if (outcome == "failed") taskFailures++
        val startMs = result.startTime
        val endMs = result.endTime
        entries.add(
            "{\"path\":${jsonString(taskPath)},\"outcome\":\"$outcome\"," +
                "\"startMs\":$startMs,\"endMs\":$endMs,\"durationMs\":${endMs - startMs}}"
        )
    }

    override fun close() {
        val finishedAt = Instant.now()
        val recordDir = File(parameters.outputDir.get())
        val recordFile = File(recordDir, "gate-timings.json")
        recordDir.mkdirs()
        val failureCount = taskFailures
        val text = buildString {
            append("{\n")
            append("  \"schemaVersion\": 2,\n")
            append("  \"invocation\": [")
                .append(parameters.requestedTasks.get().joinToString(", ") { jsonString(it) })
                .append("],\n")
            append("  \"startedAt\": \"").append(startedAt.toString()).append("\",\n")
            append("  \"finishedAt\": \"").append(finishedAt.toString()).append("\",\n")
            append("  \"durationMs\": ")
                .append(finishedAt.toEpochMilli() - startedAt.toEpochMilli()).append(",\n")
            append("  \"taskFailures\": ").append(failureCount).append(",\n")
            append("  \"tasks\": [\n")
            val snapshot = entries.toList()
            snapshot.forEachIndexed { index, entry ->
                append("    ").append(entry)
                if (index < snapshot.size - 1) append(",")
                append("\n")
            }
            append("  ]\n")
            append("}\n")
        }
        recordFile.writeText(text)
        println("Gate timing record: $recordFile (${entries.size} tasks, $failureCount failed)")
    }
}

// The registry cannot be looked up from a script any more: Gradle 9 removed
// `Project.getServices`/`Gradle.getServices`, and `BuildEventsListenerRegistry` is only
// obtainable by injection (javadoc, Gradle 9.6.1). So the wiring lives in a plugin whose
// constructor Gradle injects — no deprecated API, and the service is registered in the
// build-scoped registry, the form `onTaskCompletion` requires for a reusable provider.
abstract class GateTimingsPlugin @Inject constructor(
    private val events: BuildEventsListenerRegistry
) : Plugin<Project> {

    override fun apply(target: Project) {
        // buildSrc and included builds are nested builds: the record belongs to the invocation
        // the operator ran, not to a build Gradle started for itself.
        if (target.gradle.parent != null) return
        val invocationDir = runCatching { target.gradle.startParameter.currentDir }
            .getOrNull()
            ?: target.rootProject.projectDir
        val provider = target.gradle.sharedServices.registerIfAbsent(
            "naviveylinGateTimings",
            GateTimingsService::class.java
        ) {
            parameters.outputDir.set(File(invocationDir, "build").absolutePath)
            parameters.requestedTasks.set(target.gradle.startParameter.taskNames)
        }
        events.onTaskCompletion(provider)
    }
}

gradle.rootProject {
    pluginManager.apply(GateTimingsPlugin::class.java)
}
