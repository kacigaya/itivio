package app.itivio

import android.app.Application
import app.itivio.data.dataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ItivioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ItivioApp)
            modules(dataModule, uiModule)
        }
    }
}
