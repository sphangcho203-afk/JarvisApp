# F.R.I.D.A.Y. 1.0 // Awakening Blueprint

This document is the canonical product and engineering specification for F.R.I.D.A.Y. It exists so decisions made with the owner are not lost, diluted, or reinterpreted between releases.

## Product thesis

F.R.I.D.A.Y. is not a chatbot screen. It is an owner-bound, multilingual, voice-first Android operating intelligence that can understand a mission, select a workspace and tools, operate the phone with explicit permissions, see the screen or camera when the owner enables those channels, preserve long-term continuity, and return to a lightweight standby state.

The experience should feel like an intelligence inhabiting the phone, while remaining truthful about permissions, confidence, identity, and capability limits.

## Non-negotiable identity rules

- One persistent F.R.I.D.A.Y. identity across every workspace.
- The HELIX head/core remains the visual anchor.
- Voice-first interaction, with text available when useful.
- Original sound language only. No Google Assistant beeps, stock Android assistant sounds, copied movie assets, or cartoon effects.
- Mission-specific dialogue instead of generic chatbot phrasing.
- Truthful capability reporting. Never claim screen, camera, network, location, or account access that is not actually active.
- Secure Android lock screens and MediaProjection consent must never be bypassed.
- The owner controls memory, diary visibility, retention, and deletion.
- Heavy systems load only when needed and release resources afterward.

## Core lifecycle

1. Dormant
2. Wake-word detected
3. Owner identity estimated
4. Awakening animation and sound
5. Listening
6. Intent and mission classification
7. Permission and risk evaluation
8. Workspace selection
9. Tool execution
10. Result verification
11. Continued conversation or mission handoff
12. Secure return to standby

The wake phrase may be `Wake up Jarvis`. The default acknowledgement is `At your service, Sir.`

The sleep phrase is `Sleep, Jarvis`. It must stop camera analysis, end screen capture, release temporary frames, close overlays, clear temporary visual context, unload heavy tools, and return to wake-word standby.

## HELIX home HUD

Keep:

- F.R.I.D.A.Y. and HELIX identity
- central animated core/head
- listening, thinking, operating, and standby states
- battery, network, latency, microphone, active service, weather, and mission telemetry
- one large readable response chamber
- progressive response text synchronized with voice

Remove from the normal user surface:

- raw developer event streams
- `SYSTEM //`, `VOICE //`, recognizer-ready logs
- provider debug details
- internal traces

Diagnostics remain available in a separate Diagnostics Vault.

## Dynamic workspace router

The interface changes automatically according to the mission. Users should not need to manually navigate a menu tree.

- General questions and small commands -> HELIX home
- Long conversation -> Chat Chamber
- Object, machine, or concept analysis -> Analysis Chamber
- Research -> Research Workspace
- Live and current events -> News Intelligence
- Image creation/editing -> Image Studio
- Routes and travel -> Navigation Workspace
- Gmail -> Gmail Workspace
- WhatsApp -> WhatsApp Workspace
- Camera understanding -> Optical Vision
- Private diary and secure notes -> Private Diary Vault
- Saved personal memory controls -> Memory Vault
- Internal logs -> Diagnostics Vault

## Conversation intelligence

F.R.I.D.A.Y. must understand:

- unfinished sentences
- interruptions and corrections
- pronouns and follow-up references
- casual versus operational speech
- jokes and non-literal phrasing
- mixed-language commands
- the owner's personal vocabulary and aliases
- active app, object, screen, search, and mission context

Dialogue changes by mission:

- Research: evidence, uncertainty, source disagreement
- News: what happened, why, verified versus claimed, possible consequences
- Security: access state, risk, authentication requirement
- Camera: visible evidence, confidence, alternative identification
- Navigation: route, assumptions, delay, constraints
- Failure: direct explanation of what failed and where
- Conversation: natural, context-aware, less mechanical

F.R.I.D.A.Y. must not claim emotions or consciousness as facts. It may estimate conversational tone with uncertainty.

## Long-term memory architecture

### Active mission memory

Temporary current context such as active app, current screen, camera object, command chain, unresolved clarification, and expected next state.

### Personal preference memory

Stable owner preferences such as language, form of address, response detail, interface rules, favorite services, and confirmation habits.

### Project memory

Separate namespaces for F.R.I.D.A.Y., NovaTopup, tournaments, school, gaming, and future projects. Unrelated project memories must not leak into one another.

### Episodic memory

Important events, decisions, successes, failures, timestamps, and unfinished work.

### Conversation archive

Encrypted searchable history. Relevant sections are retrieved when needed rather than loading all conversations into every request.

### Temporary private memory

Screen frames, camera frames, copied text, temporary tokens, and transient analysis. Deleted after the mission unless the owner explicitly saves permitted material.

Every memory record should carry:

- type
- source
- timestamp
- confidence
- last verification time
- project namespace
- sensitivity
- retention rule
- owner edit/delete state

One occurrence is a possible preference. Repeated evidence is a likely preference. A direct owner statement is a confirmed preference.

Passwords, OTPs, authentication codes, private keys, and payment secrets must never enter conversational memory.

## Owner voice and communication model

Voice intelligence is split into three systems:

1. Speech recognition: what was said
2. Speaker verification: whether the voice likely belongs to the owner
3. Communication interpretation: tone, urgency, language mixing, and conversational meaning

The owner profile may learn encrypted representations of:

- accent and pronunciation
- pitch and cadence patterns
- speaking speed and rhythm
- pauses
- frequently used words and phrases
- language switching
- microphone and environment variation
- quiet, loud, tired, or ill voice variation

The system should update gradually from authenticated samples. One recording must never overwrite the owner profile.

Raw microphone recordings should not be retained by default. Store encrypted voice embeddings, approved calibration samples, and pronunciation models. Sensitive phrases must be excluded from learning.

## Voice security and loyalty

Voice recognition alone is not sufficient for sensitive actions.

Security can combine:

- voiceprint confidence
- liveness and replay checks
- recent fingerprint, face, PIN, or pattern authentication
- device proximity and current unlock state
- behavioral consistency
- unpredictable challenge phrases for high-risk actions

Authority levels:

- Level 0: public information and general questions
- Level 1: verified owner voice for opening apps and simple playback/navigation
- Level 2: owner plus confirmation for calls, messages, email sending, and posting
- Level 3: biometric/device credential for private Gmail, files, diary, account changes, and financial data
- Level 4: blocked actions involving security bypass, unauthorized access, or harmful behavior

Loyalty means protecting owner privacy, obeying confirmed owner preferences, refusing unverified access to private data, reporting failures honestly, and never silently continuing camera or screen capture.

## Controlled autonomy

The owner gives the goal. F.R.I.D.A.Y. may select tools, APIs, workspace, execution order, fallback path, and verification method. This is controlled autonomy, not literal human free will.

Every mission should produce an execution record containing intent, permissions, selected tools, action results, verification, and any unresolved uncertainty.

## Screen awareness and automation

Two complementary channels:

### Accessibility channel

Reads supported text, buttons, labels, lists, text fields, menus, and app structure. Performs allow-listed taps, typing, scrolling, and navigation.

### Visual screen channel

Uses an owner-approved MediaProjection session to understand images, maps, videos, custom-drawn interfaces, diagrams, and controls not exposed through accessibility.

Truthful screen state responses:

- active capture: visual channel active
- accessibility only: partial visibility
- neither: no screen visibility

A MediaProjection session may continue while F.R.I.D.A.Y. opens other apps, but each new capture session requires Android user consent.

## Floating HELIX assistant

Outside the app, a compact HELIX head shows listening, thinking, working, completion, microphone, and screen-capture states. It offers expand and stop controls. It should not cover the full display unless a full workspace is required.

## Optical Vision

The dedicated CameraX-based F.R.I.D.A.Y. camera should support:

- live preview
- continuous frame analysis
- high-resolution capture for deeper analysis
- object tracking
- voice follow-ups
- labels and confidence overlays
- visible-versus-inferred separation
- optional multi-view reconstruction

Targets include plants, animals, food, electronics, machines, buildings, posters, rooms, landscapes, vehicles, text, packaging, and household objects.

For people, describe visible clothing, posture, action, surroundings, and non-sensitive visual facts. Do not identify real people from an image.

Single-view 3D reconstruction is estimated. Multiple guided views improve geometry. Inferred surfaces must be clearly marked.

## Research and News Intelligence

Research must discover, compare, and synthesize sources. It should distinguish:

- verified fact
- official claim
- unverified report
- conflicting evidence
- unresolved question

News mode supports India, global, country, region, event, topic, live broadcast, live blog, official briefing, timeline, map, key actors, human impact, political/economic context, and possible next developments.

Never fake a livestream. When no legitimate live video exists, use verified live reporting or official updates and say that no verified stream is available.

## Multilingual intelligence

Initial language targets include English, Assamese, Hindi, Bengali, Japanese, Korean, Mandarin, Russian, Arabic, and Spanish.

F.R.I.D.A.Y. should detect and understand mixed-language speech, then answer in English unless the owner requests another language. Translation should preserve names, local terminology, political/cultural context, uncertainty, and speaker meaning rather than only replacing words.

## Specialist workspaces

### Chat Chamber

Long-form conversation, voice/text input, attachments, persistent context, interruption, and return-to-core behavior.

### Analysis Chamber

3D or diagrammatic object analysis, exploded view, layers, components, materials, energy/control flows, strengths, weaknesses, feasibility, and comparison with real technology. Separate fictional, theoretically possible, existing, estimated, and unknown elements.

### Research Workspace

Search phases, evidence cards, confidence, timeline, source disagreement, key findings, unresolved questions, and citations.

### News Intelligence

Live sources, broadcaster comparison, event timeline, map, verified facts, claims, disputed points, human impact, and follow-up discussion.

### Image Studio

Prompt understanding, generation progress, preview, variations, regeneration, editing, aspect ratios, save/share, and mission history. Follow-ups edit the current image instead of restarting from zero.

### Navigation Workspace

Route, distance, time, transport modes, major stops, border/visa notes when researched, weather, and route assumptions.

### Gmail Workspace

Authorized account, inbox, search, unread, full message, draft, reply, send, archive, labels, confirmations, and private-data authentication.

### WhatsApp Workspace

WaAPI status, connected instance, recipient resolution, message preview, explicit confirmation, pacing, result, and Android UI fallback.

### Diagnostics Vault

Internal events, providers, latency, permissions, failures, resource use, and test data. Hidden from the normal HUD.

## Private Diary Vault

The diary is separate from F.R.I.D.A.Y. memory. Writing an entry must never automatically teach it to the assistant.

Each entry has one AI access mode:

- Owner Only: F.R.I.D.A.Y. cannot read, search, summarize, quote, or learn from it
- Ask Every Time: temporary access requires explicit permission for the current request
- Session Readable: F.R.I.D.A.Y. may help during the current unlocked session but may not save facts to memory
- Memory Approved: selected facts may enter long-term memory only after a visible owner confirmation

Diary features:

- fingerprint/face/device-credential gate
- local encrypted storage using Android Keystore
- per-entry access controls
- title, body, timestamps, tags, optional mood label, checklist, images, voice notes, drawings, and attachments
- calendar and timeline views
- secure search limited to entries the assistant is allowed to inspect
- access audit log
- auto-lock on device lock, background, timeout, sleep command, or owner request
- `FLAG_SECURE` protection against screenshots and recent-app previews
- screen sharing paused by default while the vault is open
- clipboard and temporary context clearing
- encrypted export and backup

Commands include:

- Open my private diary
- Create a diary entry
- Show what I wrote last Friday
- Find my notes about a project
- Do not remember anything from this page
- Allow access for this session
- Hide this entry from F.R.I.D.A.Y.
- What do you remember about me?
- Correct or delete that memory
- Secure the vault

`Secure the vault` closes the diary, clears temporary diary context, stops sensitive screen exposure, and returns to standby.

## Performance model

Only the active workspace is fully loaded.

- Maps load only in navigation
- CameraX loads only in optical vision
- 3D assets load only in analysis
- livestreams load only in news
- image previews load only in image studio
- private diary content decrypts only during an authenticated vault session

When leaving a workspace, save essential state, release camera and screen frames, stop inactive streams, free GPU memory, and unload heavy assets. Visual quality may reduce under thermal pressure and recover later.

## Visual and audio language

Visuals should use physically believable metal, glass, depth, parallax, reflections, energy, holographic callouts, mechanical movement, and disciplined typography. Not every button requires heavy 3D geometry. Prefer one active real-time scene plus lightweight depth-styled UI layers.

Audio states include original wake, listening, thinking, screen connection, workspace transformation, analysis scanning, failure, completion, and return-to-standby signatures. Animation, lighting, and sound must be synchronized.

## Android architecture direction

- Compile and target Android 16/API 36 when the build environment is ready
- Handle edge-to-edge layouts and system insets correctly
- Use VoiceInteractionService where device/role support permits
- Use CameraX for optical vision
- Use MediaProjection for owner-approved screen capture
- Use AccessibilityService for allow-listed UI automation
- Use foreground services for active microphone, camera, or screen operations when required
- Use Android Keystore for encrypted secrets and private data
- Use system biometric/device credential authentication for sensitive workspaces
- Preserve visible Android privacy indicators and permission boundaries

## Delivery roadmap

### Milestone A: foundation

- Canonical blueprint and tracked roadmap
- Dynamic workspace routing contract
- Private Diary Vault MVP
- encrypted entry store and per-entry AI visibility
- biometric/device-credential gate
- secure-screen and auto-lock behavior
- command routing for opening and securing the vault
- tests and CI validation

### Milestone B: memory core

- structured memory records and namespaces
- confidence/source/retention metadata
- Memory Vault UI
- owner review, correction, delete, export, and retention controls
- conversation archive retrieval
- explicit diary-to-memory approval queue

### Milestone C: voice identity

- wake-word lifecycle refinement
- speaker verification abstraction
- encrypted voice embedding store
- gradual owner-profile updates
- liveness/replay defense
- command risk levels and re-authentication

### Milestone D: adaptive workspaces

- remove visible debug terminal from HELIX home
- workspace router and shared HELIX shell
- Chat Chamber
- Research and News workspaces
- Gmail and WhatsApp workspaces
- image and navigation refinements

### Milestone E: vision and overlays

- CameraX Optical Vision
- MediaProjection session manager
- truthful visibility state
- floating HELIX overlay
- accessibility plus visual fusion
- guided multi-view reconstruction

### Milestone F: Android 16 operating intelligence

- API 36 migration
- VoiceInteractionService role integration
- lifecycle and thermal/resource management
- original synchronized sound language
- final privacy/security review
- owner acceptance testing

## Definition of done

F.R.I.D.A.Y. 1.0 is complete only when:

- every milestone has automated tests and documented manual tests
- security-sensitive actions require the designed authentication level
- no private diary content enters memory without explicit owner approval
- screen and camera state is always reported truthfully
- wake, sleep, screen share, camera, memory, diary, app control, Gmail, WhatsApp, research, news, image, and navigation missions work through one consistent identity
- heavy resources unload correctly
- release APK passes lint, unit tests, build, signing, alignment, and device smoke checks
- the owner receives a completion report and final APK/release reference
