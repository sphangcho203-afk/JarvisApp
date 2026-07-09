package com.seongja.jarvis.models

enum class JarvisMode(
    val label: String,
    val primaryColor: Int,
    val secondaryColor: Int,
    val pulseSpeed: Float
) {
    DORMANT("DORMANT", 0xFF395063.toInt(), 0xFF607D8B.toInt(), 0.35f),
    ONLINE("ONLINE", 0xFF00E5FF.toInt(), 0xFF64FFDA.toInt(), 1.0f),
    LISTENING("LISTENING", 0xFF1DE9B6.toInt(), 0xFF00E5FF.toInt(), 1.45f),
    PROCESSING("PROCESSING", 0xFFFFD76A.toInt(), 0xFFFFAB40.toInt(), 1.8f),
    EXECUTING("EXECUTING", 0xFF76FF03.toInt(), 0xFF00E676.toInt(), 1.25f),
    TACTICAL("TACTICAL", 0xFF82B1FF.toInt(), 0xFF536DFE.toInt(), 1.15f),
    STEALTH("STEALTH", 0xFFB388FF.toInt(), 0xFF7C4DFF.toInt(), 0.75f),
    SECURITY("SECURITY", 0xFFFFD740.toInt(), 0xFFFF6D00.toInt(), 1.35f),
    RED_ALERT("RED ALERT", 0xFFFF1744.toInt(), 0xFFFF5252.toInt(), 2.2f)
}
