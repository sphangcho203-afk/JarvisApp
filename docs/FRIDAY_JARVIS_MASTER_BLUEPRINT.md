# F.R.I.D.A.Y. + JARVIS Master Blueprint

Owner and creator: **Seongja**

This document is the source of truth for the project. A class, screen, test, or green build is not enough to mark a capability complete.

## Definition of done

A capability is complete only when all of these are true:

1. A natural voice phrase routes to it before generic model dialogue.
2. A real native or web workspace opens when the task needs a screen.
3. The requested operation executes rather than being described or simulated.
4. The result is visible and returns to FRIDAY's conversation/voice context.
5. Errors are truthful, recoverable, and never disguised as success.
6. Privacy, authentication, and temporary-data rules are enforced.
7. Automated tests cover routing and packaging.
8. The capability passes acceptance testing on Seongja's actual phone.

Never claim that the assistant, APK, or feature is finished when any required acceptance gate is missing.

---

# 1. System identity

## F.R.I.D.A.Y.

FRIDAY is the phone-first, fast, always-available operational intelligence.

- natural hands-free conversation
- wake phrase and direct speech without hold-to-talk
- phone actions and live workspaces
- rapid answers, news, search, summaries, reminders, media and communication
- visible HELIX state, operation progress, results and diagnostics
- owner-bound personality, continuity and privacy

## JARVIS

JARVIS is the deeper command centre running across the phone and computer.

- complex reasoning and long-horizon planning
- project management and strategic decomposition
- long-term memory and knowledge organization
- multi-step automations and tool orchestration
- phone/computer/API/database coordination
- security policy, device trust and authenticated privileged actions
- local/offline reasoning and computer execution when available

FRIDAY is the fast operator. JARVIS is the deeper command centre. They must share trusted context without duplicating or contradicting each other.

---

# 2. Personality and communication

- Address Seongja naturally as **Boss** or **Sir**.
- Speak directly to Seongja, never call him “the user” or discuss him in third person.
- Composed, capable, incisive, private-intelligence tone.
- British-Irish voice character when the selected voice route supports it.
- Concise by default, deep when requested.
- No generic customer-service filler.
- No fake omniscience or fake action claims.
- Never say an image, camera result, app, panel or action was displayed unless Android actually opened or executed it.
- Understand natural variations, incomplete sentences, nicknames, accents and speech-recognition errors.
- Keep conversational continuity and refer back to recent context when relevant.
- Permit interruption while speaking and resume listening smoothly.

Preferred premium voice direction:

- Cartesia Sonic family, streamed token-by-token
- voice profile previously selected by Seongja: `62ae83ad-4f6a-430b-af41-a9bede9286ca`
- locale `en-gb`
- speed approximately `1.05-1.10`
- local Android TTS only as an honest fallback, not an overlapping second voice
- suppress recognizer beeps without muting the entire device

---

# 3. Voice and wake system

- No text-entry box in the primary interface.
- No SEND button in the primary experience.
- No hold-to-speak requirement.
- Continuous conversational listening while the foreground experience is active.
- Background wake service where Android permits it.
- Wake phrases include natural forms such as:
  - “Hey FRIDAY”
  - “FRIDAY”
  - “Wake up FRIDAY”
  - legacy JARVIS wake variants during migration
- Clear states: IDLE, LISTENING, PROCESSING, SPEAKING, EXECUTING, ERROR.
- Acoustic owner familiarity may personalize responses but may never authorize protected actions by itself.
- Android biometric/device credential remains the authority for sensitive access.

---

# 4. HELIX operational interface

The interface must feel like a real futuristic operating system, not a decorative chatbot skin.

## Visual language

- mathematically/geometrically engineered panels and spacing
- dark blue-black tactical environment
- responsive central energy core
- concentric rings, particle field, telemetry traces, bloom and controlled glow
- audio-reactive motion during listening and speaking
- state-specific motion and colour behaviour
- high information density without unreadable clutter
- reliable performance on the phone

## Live data

- clock and uptime
- battery
- network type and quality
- latency, jitter and loss
- memory/heap
- voice route and key health
- model/cortex/search route health
- weather and alerts
- operation stage and progress
- live countdowns and mission timers
- verified result history

## Workspaces

Ordinary conversation remains in the main HELIX chamber. Tasks needing heavier rendering or interaction open dedicated workspaces automatically:

- X-Camera / visual perception
- Image and 3D visual synthesis
- Private Diary
- Memory Vault
- Research and world briefing
- Gmail
- WhatsApp/WaAPI
- Maps and navigation
- Device Control
- Permissions and diagnostics
- API/Cortex configuration
- Owner Voice Lab
- files and project operations

Every workspace control must perform a real native action. No ornamental dead buttons.

---

# 5. X-Camera and sensory vision

Exact owner command:

> “Open your eyes.”

Required behaviour:

1. Open the visible X-Camera workspace.
2. Show a real rear-camera preview by default.
3. Support front-camera commands such as “look at me.”
4. Capture only when the owner triggers or explicitly commands a scan.
5. Analyze the temporary frame with an available multimodal vision route.
6. Describe what is actually visible, including important objects and readable text.
7. Do not identify people by name or invent hidden/private traits.
8. Display the visual answer in X-Camera.
9. Return the result to FRIDAY's terminal and voice context.
10. Delete temporary camera captures after analysis.
11. Keep camera use obvious and owner-visible.
12. “Close your eyes” must close the active camera workspace.

Future sensory expansion:

- selectable continuous visual sessions with a clear camera indicator
- object/text tracking
- document scanning and structured extraction
- screen awareness through explicit Accessibility/screen-capture permission
- “look at my screen” analysis
- visual comparison and troubleshooting
- optional computer-camera handoff through the JARVIS node

---

# 6. Image and visual synthesis

Natural commands must include:

- create/generate/make/draw/design/render an image
- visualize or visualise an idea
- show me a visual
- create a poster, wallpaper, logo, cover, diagram or concept art
- create/show a 3D model or digital representation

Required behaviour:

- intercept before generic cloud dialogue
- open the Visual Lab automatically
- show generation status and model route
- display the actual generated image
- offer regenerate, save and share
- save only after successful decoding
- truthful provider/key errors
- model/key failover
- infer portrait, landscape, wallpaper and poster aspect ratios
- return the generated-result state to HELIX

Future expansion:

- image editing and reference-image workflows
- transparent-background generation
- multi-variation gallery
- visual prompt refinement
- 3D asset generation when a genuine supported provider is connected
- model viewer for supported GLB/GLTF assets

Never reply “I will display it” without opening the real workspace and producing a result.

---

# 7. Private Diary

Owner phrase:

> “Open my private diary.”

Required behaviour:

- local encrypted diary
- biometric/device-credential gate
- screenshots and recent-app previews blocked
- create, edit, delete, search and tag entries
- time-based searches such as today, yesterday, last week or last month
- access history/audit trail
- automatic lock when backgrounded
- clipboard cleared when sealing the vault

Per-entry FRIDAY access:

- **OWNER ONLY**: FRIDAY cannot read it
- **ASK EACH TIME**: temporary explicit approval required
- **SESSION READABLE**: may help in the current session, no memory storage
- **MEMORY APPROVED**: eligible for a separate owner-approved memory transfer

Diary and general long-term memory must remain separate.

---

# 8. Memory Vault and learning

FRIDAY/JARVIS should remember useful context while keeping owner control.

Rememberable categories include:

- identity and callsign
- preferences
- projects and goals
- recurring routines
- communication style
- important decisions
- device and service configuration
- trusted contacts and relationships when explicitly approved
- corrections and stable facts
- recent conversation context

Memory requirements:

- encrypted local storage
- conversation archive
- stable-fact extraction
- conflict detection before replacing stable memory
- owner review and correction
- deletion and retention controls
- approval queue for diary-to-memory transfer
- safe export/import with authentication
- no raw secret/API-key learning
- no silent cloud upload of the full memory database
- no pretending the model weights changed

Future learning goals:

- tone and response-preference adaptation
- speech/accent recognition improvement
- routine prediction with transparent confidence
- project-state summaries
- cross-device memory synchronization through encrypted owner-approved channels

---

# 9. Android action and automation fabric

Use official Android intents/APIs first. Accessibility is a controlled fallback for tasks Android does not expose normally.

Core actions:

- launch any installed app with aliases and typo tolerance
- camera, screenshot and screen context where permitted
- flashlight
- volume and brightness
- rotation
- Wi-Fi/mobile-data/Bluetooth/settings panels
- media play, pause, stop and next
- Spotify and YouTube search/play routing
- timers, alarms, countdowns and reminders
- notification reading and prioritization
- calls and messages with confirmation where required
- contacts and calendar
- files and document operations
- battery/network/storage/device diagnostics
- Gmail operations through explicit OAuth
- WhatsApp/WaAPI communication through configured routes
- maps, location and navigation
- weather and alerts

Multi-step actions must be planned, validated, executed, checked and reported. FRIDAY must never claim success before the executor confirms it.

---

# 10. Research and intelligence

- live web research with source grounding
- world-news briefing
- public YouTube metadata search
- current weather and alerts
- source comparison and confidence
- detailed answer displayed, concise spoken summary
- automatically open a research workspace for long investigations
- preserve citations/source cards in the interface
- distinguish internal knowledge from current verified information

---

# 11. Phone and computer system

The final system is not phone-only.

## Phone node

- voice and wake
- sensors
- notifications and personal context
- Android actions
- immediate workspaces
- secure local memory

## Computer JARVIS node

- heavier local models and tools
- project/repository operations
- long-running research
- file processing
- browser automation with owner controls
- development/build workflows
- backups and local knowledge services

## Synchronization

- encrypted authenticated device pairing
- command handoff
- result return
- shared task state
- owner-approved memory synchronization
- online/offline detection and failover
- no open unauthenticated command ports

---

# 12. Security model

- owner-bound private intelligence
- Android biometric/device credential for protected actions
- encrypted API credentials and memories
- no secrets in logs, prompts, screenshots or source control
- persistent production signing key for update continuity
- non-exported internal activities
- clear permission centre
- backup/device-transfer exclusions for private stores
- visible camera/microphone states
- voice familiarity cannot unlock vaults
- confirmation for destructive, payment, communication or privacy-sensitive actions
- audit trails for vault access and privileged operations

Earlier fingerprint/unknown-touch concepts must be implemented only where Android provides a real supported signal. The application must not pretend it can identify every finger touching the display when ordinary Android APIs do not expose that identity.

---

# 13. Updates and release engineering

- permanent release keystore
- every public APK signed with the same certificate
- install new versions over the existing app
- no uninstall/reinstall cycle for routine updates
- debug builds never renamed as releases
- versioned changelog
- reproducible dependencies
- unit, lint, browser and Android runtime tests
- feature-route acceptance tests
- package/permission/signature inspection
- staged rollout and rollback plan
- no production APK published while a release gate is unresolved

---

# 14. Current recovery sequence

1. Restore real X-Camera and “open your eyes.”
2. Repair image/3D visual command interception and Visual Lab visibility.
3. Expose Diary, Memory and operational workspaces directly in HELIX.
4. Verify each workspace opens from both voice and touch.
5. Return workspace results to the central conversation.
6. Repair FRIDAY communication and remove generic fake promises.
7. Add research/maps/Gmail/WhatsApp dedicated views.
8. Complete Android 16 runtime validation.
9. Configure permanent release signing.
10. Install and test on Seongja's real phone.
11. Continue phone-computer JARVIS synchronization and advanced automation.

This roadmap is living, but no previously approved feature may silently disappear. Any change to scope must be stated explicitly and approved by Seongja.
