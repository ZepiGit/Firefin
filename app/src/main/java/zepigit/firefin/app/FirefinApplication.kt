package zepigit.firefin.app

import android.app.Application
import org.conscrypt.Conscrypt
import java.security.Security

class FirefinApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
        ServiceLocator.init(this)
    }
}
