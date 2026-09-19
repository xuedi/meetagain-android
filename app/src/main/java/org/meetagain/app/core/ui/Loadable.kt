package org.meetagain.app.core.ui

import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.meetagain.app.core.data.Cached
import org.meetagain.app.core.data.isNotFound
import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult

/**
 * Content a screen loads from the server. [Loaded] keeps [Loaded.refreshing] true while the member asked for a
 * refresh, and carries [Loaded.stale] when it is a stored answer the last refresh could not replace.
 */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>

    data class Loaded<T>(val value: T, val refreshing: Boolean = false, val stale: Stale? = null) : Loadable<T>

    data class Failed(val error: ApiError) : Loadable<Nothing>
}

/** Shown content is from [syncedAt]; the refresh after it failed with [error]. */
data class Stale(val syncedAt: Instant, val error: ApiError)

/**
 * One piece of a screen's content, offline-first: what is stored for it, shown at once, and a refresh that replaces
 * it. Starts its first refresh right away.
 */
class StoredContent<T>(
    private val scope: CoroutineScope,
    stored: Flow<Cached<T>?>,
    private val refresh: suspend () -> ApiResult<Unit>
) {
    private val status = MutableStateFlow<Refresh>(Refresh.Running(byMember = false))

    val state: StateFlow<Loadable<T>> = combine(stored, status, ::loadable)
        .stateIn(scope, SharingStarted.Eagerly, Loadable.Loading)

    init {
        launchRefresh()
    }

    /** A refresh the member asked for, by a pull or "Try again"; while one runs, it is shown as theirs. */
    fun reload() {
        val running = status.value is Refresh.Running
        status.value = Refresh.Running(byMember = true)
        if (!running) launchRefresh()
    }

    private fun launchRefresh() {
        scope.launch {
            status.value = when (val result = refresh()) {
                is ApiResult.Success -> Refresh.Done
                is ApiResult.Failure -> Refresh.Failed(result.error)
            }
        }
    }

    private fun loadable(cached: Cached<T>?, status: Refresh): Loadable<T> = when {
        status is Refresh.Failed && status.error.isNotFound -> Loadable.Failed(status.error)

        cached != null -> Loadable.Loaded(
            value = cached.value,
            refreshing = status is Refresh.Running && status.byMember,
            stale = (status as? Refresh.Failed)?.let { Stale(cached.syncedAt, it.error) }
        )

        status is Refresh.Failed -> Loadable.Failed(status.error)

        else -> Loadable.Loading
    }

    private sealed interface Refresh {
        data class Running(val byMember: Boolean) : Refresh

        data object Done : Refresh

        data class Failed(val error: ApiError) : Refresh
    }
}
