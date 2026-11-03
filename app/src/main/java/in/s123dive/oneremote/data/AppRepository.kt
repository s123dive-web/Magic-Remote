package `in`.s123dive.oneremote.data

import androidx.datastore.core.DataStore
import `in`.s123dive.oneremote.core.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Single source of truth for rooms, devices and macros. [state] always has a value
 * (empty until the first disk read completes) so the Router never blocks on IO.
 */
class AppRepository(
    private val store: DataStore<AppState>,
    scope: CoroutineScope,
) {
    val state: StateFlow<AppState> = store.data.stateIn(scope, SharingStarted.Eagerly, AppState())

    fun current(): AppState = state.value

    suspend fun update(transform: (AppState) -> AppState): AppState = store.updateData(transform)
}
