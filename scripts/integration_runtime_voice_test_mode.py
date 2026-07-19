from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/seongja/jarvis/MainActivity.kt"
TEST = ROOT / "app/src/androidTest/java/com/seongja/jarvis/RuntimeSmokeTest.kt"

text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str) -> None:
    global text
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"MainActivity runtime-test anchor missing: {old[:140]!r}")
    text = text.replace(old, new, 1)


replace_once(
    "    private var resumed = false\n",
    "    private val runtimeTestMode: Boolean\n"
    "        get() = intent?.getBooleanExtra(\n"
    "            JarvisApplication.EXTRA_SKIP_ONBOARDING_FOR_TESTS,\n"
    "            false\n"
    "        ) == true\n\n"
    "    private var resumed = false\n",
)

voice_block = '''        voiceLoop = VoiceLoop(
            activity = this,
            onSpeech = ::handleSpeech,
            onPartial = ::handlePartialSpeech,
            onRms = hud::setVoiceAmplitude,
            onState = ::handleVoiceState,
            onDiagnostic = { message -> runOnUiThread { hud.pushEvent(message) } }
        )
'''
voice_replacement = '''        if (!runtimeTestMode) {
            voiceLoop = VoiceLoop(
                activity = this,
                onSpeech = ::handleSpeech,
                onPartial = ::handlePartialSpeech,
                onRms = hud::setVoiceAmplitude,
                onState = ::handleVoiceState,
                onDiagnostic = { message -> runOnUiThread { hud.pushEvent(message) } }
            )
        }
'''
replace_once(voice_block, voice_replacement)

replace_once(
    '''        hud.setCoreTapListener {
            when {
                brainBusy.get() -> abortActiveRequest("USER CANCELLED ACTIVE REQUEST")
                hasMicPermission() -> {
                    hud.pushEvent("USER -> VOICE ARRAY RECALIBRATION")
                    voiceLoop.manualRestart()
                }
                else -> {
                    hud.pushEvent("AUTH -> REQUESTING MICROPHONE")
                    requestMicPermission()
                }
            }
        }
''',
    '''        hud.setCoreTapListener {
            when {
                runtimeTestMode -> hud.pushEvent("INSTRUMENTATION -> VOICE ARRAY DISABLED")
                brainBusy.get() -> abortActiveRequest("USER CANCELLED ACTIVE REQUEST")
                hasMicPermission() && ::voiceLoop.isInitialized -> {
                    hud.pushEvent("USER -> VOICE ARRAY RECALIBRATION")
                    voiceLoop.manualRestart()
                }
                else -> {
                    hud.pushEvent("AUTH -> REQUESTING MICROPHONE")
                    requestMicPermission()
                }
            }
        }
''',
)

replace_once(
    "        hud.setWorkspaceListener(::openWorkspace)\n"
    "        hud.isLongClickable = false\n"
    "        if (!hasMicPermission()) requestMicPermission()\n",
    "        hud.setWorkspaceListener(::openWorkspace)\n"
    "        hud.isLongClickable = false\n"
    "        if (runtimeTestMode) {\n"
    "            hud.pushEvent(\"INSTRUMENTATION -> REAL UI / VOICE + WAKE DISABLED\")\n"
    "            hud.setVoiceState(VoiceLoop.State.READY)\n"
    "        } else if (!hasMicPermission()) {\n"
    "            requestMicPermission()\n"
    "        }\n",
)

replace_once(
    "        JarvisWakeService.pause(this)\n",
    "        if (!runtimeTestMode) JarvisWakeService.pause(this)\n",
)

replace_once(
    "        if (hasMicPermission() && !brainBusy.get()) {\n"
    "            voiceLoop.resume()\n"
    "            hud.postDelayed({ openCloudSetupIfRequired() }, 450L)\n"
    "        }\n",
    "        if (\n"
    "            !runtimeTestMode &&\n"
    "            ::voiceLoop.isInitialized &&\n"
    "            hasMicPermission() &&\n"
    "            !brainBusy.get()\n"
    "        ) {\n"
    "            voiceLoop.resume()\n"
    "            hud.postDelayed({ openCloudSetupIfRequired() }, 450L)\n"
    "        }\n",
)

replace_once(
    "        if (JarvisWakeService.isEnabled(this)) JarvisWakeService.resume(this)\n",
    "        if (!runtimeTestMode && JarvisWakeService.isEnabled(this)) {\n"
    "            JarvisWakeService.resume(this)\n"
    "        }\n",
)

replace_once(
    "            hud.pushEvent(\"AUTH -> MICROPHONE GRANTED\")\n"
    "            voiceLoop.resume()\n"
    "            hud.postDelayed({ openCloudSetupIfRequired() }, 450L)\n",
    "            hud.pushEvent(\"AUTH -> MICROPHONE GRANTED\")\n"
    "            if (!runtimeTestMode && ::voiceLoop.isInitialized) {\n"
    "                voiceLoop.resume()\n"
    "                hud.postDelayed({ openCloudSetupIfRequired() }, 450L)\n"
    "            }\n",
)

MAIN.write_text(text, encoding="utf-8")

TEST.write_text(
    '''package com.seongja.jarvis

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeSmokeTest {
    @Test
    fun mainActivityCreatesARealAndroidWindowWithoutStartingVoiceHardware() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(JarvisApplication.EXTRA_SKIP_ONBOARDING_FOR_TESTS, true)

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertNotNull(activity.window)
                assertNotNull(activity.window.decorView)
                assertTrue(activity.window.decorView.isAttachedToWindow)
                assertTrue(activity.window.decorView.hasWindowFocus() || activity.hasWindowFocus())
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
''',
    encoding="utf-8",
)
