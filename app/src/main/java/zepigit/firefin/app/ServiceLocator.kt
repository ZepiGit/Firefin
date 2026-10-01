package zepigit.firefin.app

import android.content.Context
import zepigit.firefin.app.data.JellyfinClient
import zepigit.firefin.app.data.SessionStore
import zepigit.firefin.app.images.ImageLoader

/** Simple constructor-injection composition root; no reflection framework. */
object ServiceLocator {
    lateinit var session: SessionStore
        private set
    lateinit var client: JellyfinClient
        private set
    lateinit var images: ImageLoader
        private set

    fun init(context: Context) {
        session = SessionStore(context.applicationContext)
        client = JellyfinClient(session)
        images = ImageLoader(context.applicationContext)
    }
}
