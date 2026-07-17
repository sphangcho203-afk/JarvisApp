from pathlib import Path
import re


def patch_text(path: str, transform) -> None:
    file = Path(path)
    original = file.read_text(encoding="utf-8")
    updated = transform(original)
    if updated == original:
        return
    file.write_text(updated, encoding="utf-8")


def require_replace(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"Required source anchor missing: {label}")
    return text.replace(old, new, 1)


def patch_brain(text: str) -> str:
    text = require_replace(
        text,
        "    private val youtube = YouTubeDataClient(integrationStore)\n",
        "    private val youtube = YouTubeDataClient(integrationStore)\n"
        "    private val capabilityRouter = FridayCapabilityRouter(appContext)\n",
        "JarvisBrain capability property",
    )
    text = require_replace(
        text,
        "            append(\" // YouTube ${if (youtube.isConfigured()) \"ready\" else \"offline\"}\")\n",
        "            append(\" // YouTube ${if (youtube.isConfigured()) \"ready\" else \"offline\"}\")\n"
        "            append(\" // ${capabilityRouter.statusLabel()}\")\n",
        "JarvisBrain capability status",
    )
    if "capabilityRouter.intercept(input, memory.summary(), onCortexToken)" not in text:
        pattern = re.compile(
            r"(\s*localMemoryCommand\(input\)\?\.let \{ return it \}\s*\n"
            r"\s*localMeshCommand\(input\)\?\.let \{ return it \}\s*\n)"
        )
        match = pattern.search(text)
        if not match:
            raise SystemExit("Required source anchor missing: JarvisBrain response routing")
        insertion = (
            match.group(1)
            + "        capabilityRouter.intercept(input, memory.summary(), onCortexToken)?.let { return it }\n"
        )
        text = text[:match.start()] + insertion + text[match.end():]
    return text.replace("OPERATIONS CORE // 0.9.22", "OPERATIONS CORE // 0.9.24")


def patch_cloud_config(text: str) -> str:
    if "import android.content.Intent" not in text:
        text = require_replace(
            text,
            "import android.app.Activity\n",
            "import android.app.Activity\nimport android.content.Intent\n",
            "CloudConfigActivity Intent import",
        )
    if "CONNECT / MANAGE GMAIL OAUTH" not in text:
        pattern = re.compile(
            r"(\s*body\.addView\(gmailKey, matchWidth\(bottom = 6\)\)\s*\n"
            r"\s*body\.addView\(gmailOAuthClient, matchWidth\(bottom = 4\)\)\s*\n"
            r"\s*body\.addView\(gmailEnabled\)\s*\n)"
            r"\s*body\.addView\(gmailStatus\)\s*\n"
            r"\s*return body"
        )
        match = pattern.search(text)
        if not match:
            raise SystemExit("Required source anchor missing: CloudConfigActivity Gmail panel")
        replacement = (
            match.group(1)
            + "        body.addView(gmailStatus, matchWidth(bottom = 6))\n"
            + "        body.addView(actionButton(\"CONNECT / MANAGE GMAIL OAUTH\") {\n"
            + "            if (saveAll(false)) {\n"
            + "                startActivity(Intent(this, GmailAuthorizationActivity::class.java))\n"
            + "            }\n"
            + "        }, matchWidth())\n"
            + "        return body"
        )
        text = text[:match.start()] + replacement + text[match.end():]
    return text


patch_text("app/src/main/java/com/seongja/jarvis/JarvisBrain.kt", patch_brain)
patch_text("app/src/main/java/com/seongja/jarvis/CloudConfigActivity.kt", patch_cloud_config)
patch_text("helix-ui/src/Hud.tsx", lambda text: text.replace("BUILD 0.9.23", "BUILD 0.9.24"))

# Never leave build machinery inside the shipped source tree.
Path(__file__).unlink()
