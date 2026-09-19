package org.meetagain.app.core.ui

import org.meetagain.app.core.network.ApiError
import org.meetagain.app.core.network.ApiResult

/** Content a screen loads from the server. [Loaded] keeps [refreshing] true while the member pulls to refresh. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>

    data class Loaded<T>(val value: T, val refreshing: Boolean = false) : Loadable<T>

    data class Failed(val error: ApiError) : Loadable<Nothing>
}

fun <T> ApiResult<T>.toLoadable(): Loadable<T> = when (this) {
    is ApiResult.Success -> Loadable.Loaded(value)
    is ApiResult.Failure -> Loadable.Failed(error)
}

/** The state while reloading: a pull on loaded content keeps it on screen, anything else starts over. */
fun <T> Loadable<T>.reloading(): Loadable<T> =
    if (this is Loadable.Loaded) copy(refreshing = true) else Loadable.Loading
