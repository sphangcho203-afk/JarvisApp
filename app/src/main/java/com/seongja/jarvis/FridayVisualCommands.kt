package com.seongja.jarvis

import android.content.Context
import java.util.Locale

sealed class FridayVisualCommand {
    data object OpenOpticalVision : FridayVisualCommand()
    data object OpenSystemCamera : FridayVisualCommand()
    data object OpenScreenShare : FridayVisualCommand()
    data object ScreenVisibilityStatus : FridayVisualCommand()
    data object StopVisualChannels : FridayVisualCommand()
    data object Sleep : FridayVisualCommand()
    data object ShowFloatingHelix : FridayVisualCommand()
}

object FridayVisualCommandParser {
    fun parse(raw: String): FridayVisualCommand? {
        val clean = raw.lowercase(Locale.getDefault())
            .replace(Regex("[^\\p{L}\\p{N} ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isBlank()) return null

        if (Regex("\\b(sleep jarvis|sleep friday|return to standby|go to sleep)\\b").containsMatchIn(clean)) {
            return FridayVisualCommand.Sleep
        }
        if (Regex("\\b(can you see my screen|are you able to see my screen|screen visibility|visual channel status)\\b").containsMatchIn(clean)) {
            return FridayVisualCommand.ScreenVisibilityStatus
        }
        if (Regex("\\b(stop screen sharing|stop sharing my screen|close visual channel|stop visual channel)\\b").containsMatchIn(clean)) {
            return FridayVisualCommand.StopVisualChannels
        }
        if (Regex("\\b(share my screen|start screen sharing|open screen vision|see my screen)\\b").containsMatchIn(clean)) {
            return FridayVisualCommand.OpenScreenShare
        }
        if (Regex("\\b(show floating helix|open floating assistant|show floating assistant)\\b").containsMatchIn(clean)) {
            return FridayVisualCommand.ShowFloatingHelix
        }
        if (Regex("\\b(open system camera|open normal camera|launch camera app)\\b").containsMatchIn(clean)) {
            return FridayVisualCommand.OpenSystemCamera
        }
        if (Regex("\\b(open camera|open optical vision|bring optical systems online|bring the eye online)\\b").containsMatchIn(clean)) {
            return FridayVisualCommand.OpenOpticalVision
        }
        return null
    }
}

object FridaySessionController {
    fun stopVisualChannels(context: Context, reason: String) {
        runCatching { ScreenCaptureService.stop(context.applicationContext) }
        runCatching { FloatingHelixService.hide(context.applicationContext) }
        runCatching { OpticalVisionRuntime.closeActive(reason) }
        ScreenVisionRuntime.clear(context.applicationContext)
    }

    fun sleep(context: Context, reason: String) {
        stopVisualChannels(context, reason)
        runCatching { PrivateDiaryRuntime.secureActive("SLEEP COMMAND") }
        runCatching { JarvisWakeService.resume(context.applicationContext) }
    }
}
