package com.seongja.jarvis

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeSmokeTest {
    @get:Rule
    val microphonePermission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test
    fun mainActivityCreatesARealAndroidWindow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(JarvisApplication.EXTRA_SKIP_ONBOARDING_FOR_TESTS, true)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertNotNull(activity.window)
                assertNotNull(activity.window.decorView)
                assertTrue(activity.window.decorView.isAttachedToWindow)
            }
        }
    }

    @Test
    fun protectedConsolesAreNotExported() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val packageManager = context.packageManager
        listOf(
            CloudConfigActivity::class.java,
            GmailAuthorizationActivity::class.java,
            WaApiConfigActivity::class.java,
            WeatherSetupActivity::class.java,
            PermissionCenterActivity::class.java,
            OwnerAccessGateActivity::class.java,
            XCameraActivity::class.java,
            ImageGenerationActivity::class.java,
            PrivateDiaryActivity::class.java,
            MemoryVaultActivity::class.java,
            OwnerVoiceEnrollmentActivity::class.java
        ).forEach { activityClass ->
            val info = packageManager.getActivityInfo(
                ComponentName(context, activityClass),
                0
            )
            assertFalse("${activityClass.simpleName} must not be exported", info.exported)
        }
    }
}
