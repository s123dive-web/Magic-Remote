package androidx.lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
abstract class ViewModel { protected open fun onCleared() {} }
val ViewModel.viewModelScope: CoroutineScope get() = CoroutineScope(SupervisorJob() + Dispatchers.Main)
abstract class AndroidViewModel(val application: android.app.Application) : ViewModel()
