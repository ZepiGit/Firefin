package zepigit.firefin.app

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import zepigit.firefin.app.ui.LoginActivity

@RunWith(AndroidJUnit4::class)
class Api22FlowTest {
    @Test fun loginScreenStartsWithoutCrashAndExposesServerField() {
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("firefin_session", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        ActivityScenario.launch(LoginActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(activity.findViewById<android.widget.EditText>(R.id.serverUrl).isFocusable)
                assertTrue(activity.findViewById<android.widget.Button>(R.id.loginButton).isFocusable)
            }
        }
    }
}
