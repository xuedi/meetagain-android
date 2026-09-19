import javax.inject.Inject
import org.gradle.build.event.BuildEventsListenerRegistry
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

// Writes build/check-times.log after every build: how long each logical step took. Tasks run in parallel, so a step
// is the span from its first task's start to its last task's end, and steps overlap.
abstract class StepTimer :
    BuildService<StepTimer.Params>,
    OperationCompletionListener,
    AutoCloseable {
    interface Params : BuildServiceParameters {
        val logFile: RegularFileProperty
    }

    private val spans = java.util.concurrent.ConcurrentHashMap<String, LongArray>()

    override fun onFinish(event: FinishEvent) {
        if (event !is TaskFinishEvent) return
        val step = stepOf(event.descriptor.taskPath.substringAfterLast(':')) ?: return
        val result = event.result
        spans.merge(step, longArrayOf(result.startTime, result.endTime)) { a, b ->
            longArrayOf(minOf(a[0], b[0]), maxOf(a[1], b[1]))
        }
    }

    private fun stepOf(task: String): String? = when {
        task.startsWith("spotless") -> "format"
        task.startsWith("lintVital") -> "lint (release build gate)"
        task.startsWith("lint") -> "lint"
        task.contains("UnitTest") || task.contains("Roborazzi") -> "tests and screenshots"
        task.contains("Debug") -> "compile debug"
        task.contains("Release") -> "release build"
        else -> null
    }

    override fun close() {
        if (spans.isEmpty()) return
        val started = spans.values.minOf { it[0] }
        fun seconds(millis: Long) = "%7.1fs".format(millis / 1000.0)
        val lines = spans.entries.sortedBy { it.value[0] }.map { (step, span) ->
            "%-28s".format(step) + seconds(span[0] - started) + seconds(span[1] - span[0])
        }
        val end = spans.values.maxOf { it[1] }
        val header = "%-28s%8s%8s".format("step", "start", "took")
        val total = "%-28s%8s".format("gradle total", "") + seconds(end - started)
        parameters.logFile.get().asFile.apply { parentFile.mkdirs() }
            .writeText((listOf(header) + lines + total).joinToString("\n", postfix = "\n"))
    }
}

abstract class StepTimerPlugin @Inject constructor(private val events: BuildEventsListenerRegistry) :
    Plugin<Project> {
    override fun apply(project: Project) {
        val timer = project.gradle.sharedServices.registerIfAbsent("stepTimer", StepTimer::class) {
            parameters.logFile.set(project.layout.buildDirectory.file("check-times.log"))
        }
        events.onTaskCompletion(timer)
    }
}

apply<StepTimerPlugin>()
