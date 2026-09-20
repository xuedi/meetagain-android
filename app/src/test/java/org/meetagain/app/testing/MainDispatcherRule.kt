package org.meetagain.app.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * The main dispatcher a ViewModel works on, and the end of the view models a test made: they are cleared while that
 * dispatcher is still there, because their coroutines finish on it.
 */
class MainDispatcherRule : TestWatcher() {
    private val store = ViewModelStore()
    private var made = 0

    /** Hands the view model to the test and takes care of stopping it afterwards. */
    fun <T : ViewModel> keep(viewModel: T): T {
        store.put("view-model-${made++}", viewModel)
        return viewModel
    }

    override fun starting(description: Description) = Dispatchers.setMain(StandardTestDispatcher())

    override fun finished(description: Description) {
        store.clear()
        // A call that was already on its way comes back on this dispatcher, so it gets a moment before it goes.
        Thread.sleep(SETTLE_MILLIS)
        Dispatchers.resetMain()
    }

    private companion object {
        const val SETTLE_MILLIS = 50L
    }
}
