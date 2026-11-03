package `in`.s123dive.oneremote

import android.app.Application

class OneRemoteApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
