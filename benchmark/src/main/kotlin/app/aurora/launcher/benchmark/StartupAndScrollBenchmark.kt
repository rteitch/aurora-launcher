package app.aurora.launcher.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupAndScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCold() = benchmarkRule.measureRepeated(
        packageName = "app.aurora.launcher",
        metrics = listOf(StartupTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.COLD,
        compilationMode = CompilationMode.Partial(),
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun workspacePagingFrameTiming() = benchmarkRule.measureRepeated(
        packageName = "app.aurora.launcher",
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.Partial(),
    ) {
        startActivityAndWait()
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe((w * 0.82).toInt(), h / 2, (w * 0.18).toInt(), h / 2, 18)
        device.waitForIdle()
        device.swipe((w * 0.18).toInt(), h / 2, (w * 0.82).toInt(), h / 2, 18)
        device.waitForIdle()
    }
}
