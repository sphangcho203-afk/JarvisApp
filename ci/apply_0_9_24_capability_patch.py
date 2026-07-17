from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    if old not in text:
        if new in text:
            return
        raise SystemExit(f"Required anchor missing in {path}: {old[:100]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_all(path: str, replacements: list[tuple[str, str]]) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    for old, new in replacements:
        text = text.replace(old, new)
    file.write_text(text, encoding="utf-8")


# Wire the new router into the synchronous brain path without disturbing any
# existing dialogue, memory, YouTube, web research, or device command routes.
replace_once(
    "app/src/main/java/com/seongja/jarvis/JarvisBrain.kt",
    "    private val youtube = YouTubeDataClient(integrationStore)\n",
    "    private val youtube = YouTubeDataClient(integrationStore)\n"
    "    private val capabilityRouter = FridayCapabilityRouter(appContext)\n",
)
replace_once(
    "app/src/main/java/com/seongja/jarvis/JarvisBrain.kt",
    "            append(\" // YouTube ${if (youtube.isConfigured()) \"ready\" else \"offline\"}\")\n",
    "            append(\" // YouTube ${if (youtube.isConfigured()) \"ready\" else \"offline\"}\")\n"
    "            append(\" // ${capabilityRouter.statusLabel()}\")\n",
)
replace_once(
    "app/src/main/java/com/seongja/jarvis/JarvisBrain.kt",
    "        localMeshCommand(input)?.let { return it }\n\n        YouTubeDataClient.commandFor(input)?.let { command ->\n",
    "        localMeshCommand(input)?.let { return it }\n"
    "        capabilityRouter.intercept(input, memory.summary(), onCortexToken)?.let { return it }\n\n"
    "        YouTubeDataClient.commandFor(input)?.let { command ->\n",
)
replace_all(
    "app/src/main/java/com/seongja/jarvis/JarvisBrain.kt",
    [
        ("OPERATIONS CORE // 0.9.22", "OPERATIONS CORE // 0.9.24"),
        ("PHASE 11", "PHASE 12"),
    ],
)

# Put a visible OAuth control beside the existing encrypted Gmail settings.
replace_once(
    "app/src/main/java/com/seongja/jarvis/CloudConfigActivity.kt",
    "import android.app.Activity\n",
    "import android.app.Activity\nimport android.content.Intent\n",
)
replace_once(
    "app/src/main/java/com/seongja/jarvis/CloudConfigActivity.kt",
    "        body.addView(gmailEnabled)\n        body.addView(gmailStatus)\n        return body\n",
    "        body.addView(gmailEnabled)\n"
    "        body.addView(gmailStatus, matchWidth(bottom = 6))\n"
    "        body.addView(actionButton(\"CONNECT / MANAGE GMAIL OAUTH\") {\n"
    "            if (saveAll(false)) {\n"
    "                startActivity(Intent(this, GmailAuthorizationActivity::class.java))\n"
    "            }\n"
    "        }, matchWidth())\n"
    "        return body\n",
)

# Advance the visible HELIX build identity without changing the locked design.
replace_all(
    "helix-ui/src/Hud.tsx",
    [("BUILD 0.9.23", "BUILD 0.9.24")],
)

# Convert the restored production factory to the new version and add capability
# assertions so OAuth/image code cannot silently disappear in a later build.
workflow = Path(".github/workflows/android-apk.yml")
workflow_text = workflow.read_text(encoding="utf-8")
workflow_text = workflow_text.replace("Validate FRIDAY design-lock APK", "Validate FRIDAY Gmail OAuth + image APK")
workflow_text = workflow_text.replace("Build FRIDAY Helix design-lock interface", "Build FRIDAY Helix locked interface")
workflow_text = workflow_text.replace("BUILD 0.9.23", "BUILD 0.9.24")
workflow_text = workflow_text.replace("0.9.23-design-lock", "0.9.24-gmail-image")
workflow_text = workflow_text.replace("0.9.23-Design-Lock", "0.9.24-Gmail-Image")
workflow_text = workflow_text.replace(
    "F.R.I.D.A.Y. 0.9.23 // Design Lock",
    "F.R.I.D.A.Y. 0.9.24 // Gmail + Image Intelligence",
)
workflow_text = workflow_text.replace(
    "Verified Android build restoring the locked premium HELIX composition: central orb hero, disciplined telemetry rails, simplified status dock, concise operational terminal, and all 0.9.22 network, research, voice failover, DeepSeek, YouTube, Gmail readiness, and Android systems preserved.",
    "Verified Android build preserving the locked premium HELIX composition while adding Google Identity Services Gmail authorization, private mailbox commands, and Gemini image synthesis with gallery preview and sharing. Existing network, research, voice failover, DeepSeek, YouTube, weather, and Android systems remain intact.",
)
checks_anchor = "          grep -q 'streamGenerateContent?alt=sse' app/src/main/java/com/seongja/jarvis/CortexStreamingTransport.kt\n"
checks = checks_anchor + """
          grep -q 'play-services-auth:21.6.0' app/build.gradle.kts
          grep -q 'class GmailAuthorizationActivity' app/src/main/java/com/seongja/jarvis/GmailAuthorizationActivity.kt
          grep -q 'AuthorizationRequest.builder' app/src/main/java/com/seongja/jarvis/GmailAuthorizationActivity.kt
          grep -q 'gmail.modify' app/src/main/java/com/seongja/jarvis/GmailAuthStore.kt
          grep -q 'users/me/messages/send' app/src/main/java/com/seongja/jarvis/GmailApiClient.kt
          grep -q 'token_not_persisted' app/src/main/java/com/seongja/jarvis/FridayCapabilityRouter.kt
          grep -q 'class GeminiImageClient' app/src/main/java/com/seongja/jarvis/GeminiImageClient.kt
          grep -q 'gemini-3.1-flash-lite-image' app/src/main/java/com/seongja/jarvis/GeminiImageClient.kt
          grep -q 'class ImageGenerationActivity' app/src/main/java/com/seongja/jarvis/ImageGenerationActivity.kt
          grep -q 'capabilityRouter.intercept' app/src/main/java/com/seongja/jarvis/JarvisBrain.kt
          grep -q 'GmailAuthorizationActivity' app/src/main/AndroidManifest.xml
          grep -q 'ImageGenerationActivity' app/src/main/AndroidManifest.xml
"""
if "class GmailAuthorizationActivity" not in workflow_text:
    if checks_anchor not in workflow_text:
        raise SystemExit("Workflow capability-check anchor missing")
    workflow_text = workflow_text.replace(checks_anchor, checks, 1)
workflow.write_text(workflow_text, encoding="utf-8")

replace_all(
    ".github/workflows/ci-status-beacon.yml",
    [
        ("0.9.23-design-lock", "0.9.24-gmail-image"),
        ("0.9.23-Design-Lock", "0.9.24-Gmail-Image"),
    ],
)

# The patcher must not remain in production source.
Path(__file__).unlink()
