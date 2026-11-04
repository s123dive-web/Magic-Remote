package androidx.navigation
class NavBackStackEntry { val arguments: android.os.Bundle? = null }
class NavGraphBuilder
open class NavController {
    fun navigate(route: String) {}
    fun navigate(route: String, builder: NavOptionsBuilder.() -> Unit) {}
    fun popBackStack(): Boolean = true
    fun popBackStack(route: String, inclusive: Boolean): Boolean = true
}
class NavHostController : NavController()
class NavOptionsBuilder { var launchSingleTop = false; fun popUpTo(route: String, builder: PopUpToBuilder.() -> Unit = {}) {} }
class PopUpToBuilder { var inclusive = false }
