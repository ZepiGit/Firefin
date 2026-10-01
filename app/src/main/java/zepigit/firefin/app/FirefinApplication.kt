package zepigit.firefin.app

import android.app.Application

class FirefinApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
