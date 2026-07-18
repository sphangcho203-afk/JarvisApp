from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PATH = ROOT / "app/src/main/java/com/seongja/jarvis/MainActivity.kt"
text = PATH.read_text(encoding="utf-8")

import_line = "import com.jarvis.core.device.FridayWorkspaceReservation\n"
if import_line not in text:
    anchor = "import com.jarvis.core.device.DeviceCommandRouter\n"
    if anchor not in text:
        raise RuntimeError("MainActivity device-router import anchor missing")
    text = text.replace(anchor, anchor + import_line, 1)

old = "        val deviceResult = deviceCommandRouter.executeDetailed(clean)\n"
new = (
    "        val deviceResult = if (\n"
    "            FridayWorkspaceReservation.shouldBypassGenericDeviceRouter(clean)\n"
    "        ) {\n"
    "            hud.pushEvent(\"NATIVE WORKSPACE -> RESERVED ROUTE\")\n"
    "            null\n"
    "        } else {\n"
    "            deviceCommandRouter.executeDetailed(clean)\n"
    "        }\n"
)
if new not in text:
    if old not in text:
        raise RuntimeError("MainActivity device routing anchor missing")
    text = text.replace(old, new, 1)

PATH.write_text(text, encoding="utf-8")
