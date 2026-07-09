package com.seongja.jarvis.models

data class CommandResult(
    val reply: String,
    val mode: JarvisMode? = null,
    val executed: Boolean = true,
    val shouldSpeak: Boolean = true,
    val visualAlert: Boolean = false
)
