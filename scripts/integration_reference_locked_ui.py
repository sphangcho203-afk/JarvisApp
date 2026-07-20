from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/seongja/jarvis/MainActivity.kt"
HUD = ROOT / "app/src/main/java/com/seongja/jarvis/HelixHudView.kt"


def patch(path: Path, old: str, new: str, marker: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if marker in text:
        return
    if old not in text:
        raise RuntimeError(f"Reference UI {label} anchor missing: {old[:260]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


patch(
    MAIN,
    "    private val designModeStore by lazy { FridayDesignModeStore(applicationContext) }\n",
    "    private val designModeStore by lazy { FridayDesignModeStore(applicationContext) }\n"
    "    private lateinit var locationRuntime: FridayLocationRuntime\n"
    "    private var pendingLocationCommand = false\n",
    "private lateinit var locationRuntime: FridayLocationRuntime",
    "location fields",
)

patch(
    MAIN,
    "        soundEngine = JarvisSoundEngine()\n"
    "        countdown = JarvisCountdownController(\n",
    "        soundEngine = JarvisSoundEngine()\n"
    "        locationRuntime = FridayLocationRuntime(applicationContext)\n"
    "        countdown = JarvisCountdownController(\n",
    "locationRuntime = FridayLocationRuntime",
    "location initialization",
)

patch(
    MAIN,
    "        FridayDesignCommandParser.parse(clean)?.let { designCommand ->\n"
    "            handleDesignCommand(designCommand)\n"
    "            return\n"
    "        }\n\n"
    "        CountdownCommandParser.parse(clean)?.let { timerCommand ->\n",
    "        FridayDesignCommandParser.parse(clean)?.let { designCommand ->\n"
    "            handleDesignCommand(designCommand)\n"
    "            return\n"
    "        }\n\n"
    "        if (isCurrentLocationCommand(clean)) {\n"
    "            handleCurrentLocationCommand()\n"
    "            return\n"
    "        }\n\n"
    "        CountdownCommandParser.parse(clean)?.let { timerCommand ->\n",
    "handleCurrentLocationCommand()",
    "location command interception",
)

patch(
    MAIN,
    "    private fun handleDesignCommand(command: FridayDesignCommand) {\n",
    '''    private fun isCurrentLocationCommand(input: String): Boolean {
        val normalized = input
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return normalized in setOf(
            "show me where i am",
            "where am i",
            "show my location",
            "locate me",
            "find my location",
            "what is my current location",
            "open my location"
        ) || (normalized.contains("my location") && listOf("show", "find", "open", "locate").any(normalized::contains))
    }

    private fun handleCurrentLocationCommand() {
        hud.pushEvent("LOCATION CORE -> ACQUIRING DEVICE POSITION")
        hud.setLocation(FridayLocationSnapshot(acquiring = true))
        if (!hasLocationPermission()) {
            pendingLocationCommand = true
            requestLocationPermission()
            return
        }
        locationRuntime.locate { snapshot ->
            runOnUiThread {
                hud.setLocation(snapshot)
                if (snapshot.available) {
                    val place = snapshot.placeName.ifBlank { "your verified device position" }
                    finishLocalCommand(
                        spoken = "You're here, Sir. $place.",
                        display = "YOU'RE HERE, SIR.\n$place\n${String.format(Locale.US, "%.5f° N  %.5f° E", snapshot.latitude, snapshot.longitude)}",
                        intent = "location/current",
                        mode = BrainMode.ONLINE,
                        trace = listOf(
                            "android_location_manager",
                            "permission_verified",
                            "reverse_geocode_privacy_limited",
                            "accuracy=${snapshot.accuracyM.toInt()}m"
                        )
                    )
                } else {
                    finishLocalCommand(
                        spoken = "I couldn't verify the device location, Sir.",
                        display = snapshot.error.ifBlank { "LOCATION CORE UNAVAILABLE" },
                        intent = "location/error",
                        mode = BrainMode.ALERT,
                        trace = listOf("android_location_manager", "position_unavailable"),
                        playSuccess = false
                    )
                }
            }
        }
    }

    private fun handleDesignCommand(command: FridayDesignCommand) {
''',
    "private fun isCurrentLocationCommand",
    "location handlers",
)

patch(
    MAIN,
    "    private fun requestMicPermission() {\n"
    "        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)\n"
    "    }\n\n"
    "    override fun onRequestPermissionsResult(\n",
    '''    private fun requestMicPermission() {
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQ_LOCATION
        )
    }

    override fun onRequestPermissionsResult(
''',
    "private fun hasLocationPermission()",
    "location permission helpers",
)

patch(
    MAIN,
    '''        if (
            requestCode == REQ_RECORD_AUDIO &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
''',
    '''        if (requestCode == REQ_LOCATION) {
            val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            if (granted && pendingLocationCommand) {
                pendingLocationCommand = false
                hud.pushEvent("AUTH -> LOCATION GRANTED")
                handleCurrentLocationCommand()
            } else if (!granted) {
                pendingLocationCommand = false
                hud.setLocation(FridayLocationSnapshot(error = "LOCATION PERMISSION DENIED"))
                finishLocalCommand(
                    spoken = "Location access was denied, Sir.",
                    display = "LOCATION PERMISSION REQUIRED",
                    intent = "location/permission_denied",
                    mode = BrainMode.ALERT,
                    trace = listOf("android_runtime_permission", "location_denied"),
                    playSuccess = false
                )
            }
            return
        }
        if (
            requestCode == REQ_RECORD_AUDIO &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
''',
    "requestCode == REQ_LOCATION",
    "location permission result",
)

patch(
    MAIN,
    "        if (::soundEngine.isInitialized) soundEngine.release()\n"
    "        if (::hud.isInitialized) hud.release()\n",
    "        if (::soundEngine.isInitialized) soundEngine.release()\n"
    "        if (::locationRuntime.isInitialized) locationRuntime.close()\n"
    "        if (::hud.isInitialized) hud.release()\n",
    "locationRuntime.close()",
    "location cleanup",
)

patch(
    MAIN,
    "        private const val REQ_RECORD_AUDIO = 101\n",
    "        private const val REQ_RECORD_AUDIO = 101\n"
    "        private const val REQ_LOCATION = 102\n",
    "private const val REQ_LOCATION",
    "location request code",
)

patch(
    HUD,
    "    internal fun setCountdown(snapshot: CountdownSnapshot) {\n",
    '''    fun setLocation(snapshot: FridayLocationSnapshot) {
        dispatch(
            JSONObject()
                .put("type", "location")
                .put(
                    "location",
                    JSONObject()
                        .put("available", snapshot.available)
                        .put("acquiring", snapshot.acquiring)
                        .put("latitude", snapshot.latitude)
                        .put("longitude", snapshot.longitude)
                        .put("accuracyM", snapshot.accuracyM.toDouble())
                        .put("altitudeM", snapshot.altitudeM)
                        .put("provider", snapshot.provider)
                        .put("placeName", snapshot.placeName)
                        .put("updatedAtMs", snapshot.updatedAtMs)
                        .put("error", snapshot.error)
                )
        )
    }

    internal fun setCountdown(snapshot: CountdownSnapshot) {
''',
    "fun setLocation(snapshot: FridayLocationSnapshot)",
    "HUD location payload",
)

print("FRIDAY reference-locked location runtime integrated")
