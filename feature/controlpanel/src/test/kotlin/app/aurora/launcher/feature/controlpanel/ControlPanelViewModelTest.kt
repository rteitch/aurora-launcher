package app.aurora.launcher.feature.controlpanel

import app.aurora.launcher.core.common.SystemResult
import app.aurora.launcher.core.platform.ControlPanelGateway
import app.aurora.launcher.core.platform.ControlPanelSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControlPanelViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class TestControlPanelGateway(
        initialSnapshot: ControlPanelSnapshot = ControlPanelSnapshot(
            isTorchAvailable = true,
            isTorchOn = false,
            mediaVolume = 5,
            maxMediaVolume = 15,
            brightness = 120,
            canWriteSettings = true,
        ),
    ) : ControlPanelGateway() {
        private val _mockState = MutableStateFlow(initialSnapshot)
        override val state: StateFlow<ControlPanelSnapshot> = _mockState.asStateFlow()

        var lastMediaKeyCode: Int? = null

        override fun toggleTorch(): SystemResult<Boolean> {
            val next = !_mockState.value.isTorchOn
            _mockState.update { it.copy(isTorchOn = next) }
            return SystemResult.Success(next)
        }

        override fun setMediaVolume(volume: Int): SystemResult<Unit> {
            val clamped = volume.coerceIn(0, _mockState.value.maxMediaVolume)
            _mockState.update { it.copy(mediaVolume = clamped) }
            return SystemResult.Success(Unit)
        }

        override fun setScreenBrightness(value: Int): SystemResult<Unit> {
            val clamped = value.coerceIn(10, 255)
            _mockState.update { it.copy(brightness = clamped) }
            return SystemResult.Success(Unit)
        }

        override fun dispatchMediaKey(keyCode: Int): SystemResult<Unit> {
            lastMediaKeyCode = keyCode
            return SystemResult.Success(Unit)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state reflects initial snapshot and updates on volume and torch actions`() = runTest(testDispatcher) {
        val gateway = TestControlPanelGateway()
        val viewModel = ControlPanelViewModel(gateway)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect()
        }
        advanceUntilIdle()

        assertEquals(5, viewModel.state.value.mediaVolume)
        assertFalse(viewModel.state.value.isTorchOn)

        viewModel.setVolume(10)
        advanceUntilIdle()
        assertEquals(10, viewModel.state.value.mediaVolume)

        viewModel.toggleTorch()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isTorchOn)
    }

    @Test
    fun `media controls dispatch correct key codes`() = runTest(testDispatcher) {
        val gateway = TestControlPanelGateway()
        val viewModel = ControlPanelViewModel(gateway)

        viewModel.mediaPlayPause()
        assertEquals(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, gateway.lastMediaKeyCode)

        viewModel.mediaNext()
        assertEquals(android.view.KeyEvent.KEYCODE_MEDIA_NEXT, gateway.lastMediaKeyCode)

        viewModel.mediaPrev()
        assertEquals(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS, gateway.lastMediaKeyCode)
    }

    @Test
    fun `brightness adjustment clamps and updates state`() = runTest(testDispatcher) {
        val gateway = TestControlPanelGateway()
        val viewModel = ControlPanelViewModel(gateway)

        viewModel.setBrightness(200)
        advanceUntilIdle()
        assertEquals(200, viewModel.state.value.brightness)
    }
}
