package androidx.navigation.compose
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
@Composable fun rememberNavController(): NavHostController = NavHostController()
@Composable fun NavHost(navController: NavHostController, startDestination: String, modifier: Modifier = Modifier, builder: NavGraphBuilder.() -> Unit) {}
fun NavGraphBuilder.composable(route: String, content: @Composable (NavBackStackEntry) -> Unit) {}
