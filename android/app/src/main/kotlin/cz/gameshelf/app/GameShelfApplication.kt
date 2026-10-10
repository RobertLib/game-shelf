package cz.gameshelf.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import cz.gameshelf.app.di.AppContainer

class GameShelfApplication : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** The container's, so that wiping the local data also empties its caches. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = container.imageLoader
}
