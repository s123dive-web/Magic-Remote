package androidx.activity.compose
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
fun ComponentActivity.setContent(content: @Composable () -> Unit) {}
class ManagedActivityResultLauncher<I, O> { fun launch(input: I) {} }
@Composable
fun <I, O> rememberLauncherForActivityResult(contract: ActivityResultContract<I, O>, onResult: (O) -> Unit): ManagedActivityResultLauncher<I, O> = ManagedActivityResultLauncher()
@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {}
