package com.seongja.jarvis

/**
 * Silent state-cue compatibility layer.
 *
 * Earlier builds used Android ToneGenerator beeps for every state transition.
 * Those cues sounded like generic Google/Android assistant feedback and became
 * exhausting during continuous voice use. The call surface remains intact so
 * command routing does not change, but FRIDAY now communicates state through
 * HELIX animation and haptics rather than system tones.
 */
internal class JarvisSoundEngine {
    fun boot() = Unit
    fun processing() = Unit
    fun research() = Unit
    fun speaking() = Unit
    fun success() = Unit
    fun warning() = Unit
    fun error() = Unit
    fun standby() = Unit
    fun timerComplete() = Unit
    fun release() = Unit
}
