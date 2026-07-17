package com.seongja.jarvis

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executor

class PrivateDiaryActivity : Activity() {
    private lateinit var store: PrivateDiaryStore
    private var unlocked = false
    private var authenticationInProgress = false
    private var cancellationSignal: CancellationSignal? = null
    private var requestedSearch = ""
    private var requestedNewEntry = false
    private var secureFinishStarted = false
    private val mainExecutor = Executor { command -> runOnUiThread(command) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store = PrivateDiaryStore(this)
        requestedSearch = intent.getStringExtra(EXTRA_SEARCH).orEmpty().trim()
        requestedNewEntry = intent.getBooleanExtra(EXTRA_NEW_ENTRY, false)
        PrivateDiaryRuntime.attach(this)
        notifySecureSurface(true)
        showLockedSurface()
        authenticateOwner()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra(EXTRA_SECURE_NOW, false) == true) {
            finishSecurely("OWNER COMMAND")
            return
        }
        requestedSearch = intent?.getStringExtra(EXTRA_SEARCH).orEmpty().trim()
        requestedNewEntry = intent?.getBooleanExtra(EXTRA_NEW_ENTRY, false) == true
        if (unlocked) showVaultHome()
    }

    override fun onStop() {
        if (unlocked && !authenticationInProgress && !isChangingConfigurations && !secureFinishStarted) {
            finishSecurely("BACKGROUND AUTO-LOCK")
        }
        super.onStop()
    }

    override fun onDestroy() {
        cancellationSignal?.cancel()
        PrivateDiaryRuntime.detach(this)
        notifySecureSurface(false)
        super.onDestroy()
    }

    fun finishSecurely(reason: String) {
        if (secureFinishStarted) return
        secureFinishStarted = true
        unlocked = false
        authenticationInProgress = false
        requestedSearch = ""
        requestedNewEntry = false
        cancellationSignal?.cancel()
        clearClipboard()
        store.recordAccess(VAULT_EVENT_ID, "VAULT_LOCK", reason)
        notifySecureSurface(false)
        setContentView(buildLockedPanel("DIARY SEALED // $reason"))
        window.decorView.postDelayed({ finishAndRemoveTask() }, 160L)
    }

    private fun showLockedSurface() {
        setContentView(buildLockedPanel("OWNER AUTHENTICATION REQUIRED"))
    }

    private fun buildLockedPanel(status: String): ScrollView {
        val root = pageRoot().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(36), dp(18), dp(30))
        }
        root.addView(title("F.R.I.D.A.Y. // PRIVATE DIARY", 22f), params(bottom = 8))
        root.addView(centerLabel("ENCRYPTED OWNER VAULT", 10f, MUTED, true), params(bottom = 22))
        root.addView(panel().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(centerLabel("VAULT LOCKED", 18f, GREEN, true), params(bottom = 10))
            addView(centerLabel(status, 11f, SOFT, false), params(bottom = 14))
            addView(
                centerLabel(
                    "Diary pages remain encrypted and unavailable to F.R.I.D.A.Y. unless each entry explicitly permits access.",
                    12f,
                    MUTED,
                    false
                ),
                params(bottom = 18)
            )
            addView(button("AUTHENTICATE OWNER", CYAN) { authenticateOwner() }, params(bottom = 8))
            addView(button("RETURN TO F.R.I.D.A.Y.", BLUE) { finishSecurely("OWNER EXIT") })
        }, params())
        return ScrollView(this).apply { addView(root) }
    }

    private fun authenticateOwner() {
        if (authenticationInProgress || unlocked || secureFinishStarted) return
        authenticationInProgress = true
        val signal = CancellationSignal()
        cancellationSignal?.cancel()
        cancellationSignal = signal
        val keyguard = getSystemService(KeyguardManager::class.java)

        val builder = BiometricPrompt.Builder(this)
            .setTitle("Unlock F.R.I.D.A.Y. Private Diary")
            .setSubtitle("Owner authentication")
            .setDescription("Android must verify the device owner before encrypted entries are shown.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && keyguard?.isDeviceSecure == true) {
            builder.setDeviceCredentialAllowed(true)
        } else {
            builder.setNegativeButton("Cancel", mainExecutor) { _, _ ->
                authenticationInProgress = false
                signal.cancel()
            }
        }

        runCatching {
            builder.build().authenticate(
                signal,
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        authenticationInProgress = false
                        unlocked = true
                        secureFinishStarted = false
                        store.recordAccess(VAULT_EVENT_ID, "VAULT_UNLOCK", "ANDROID OWNER AUTHENTICATED")
                        showVaultHome()
                    }

                    override fun onAuthenticationFailed() {
                        Toast.makeText(
                            this@PrivateDiaryActivity,
                            "Owner identity was not verified.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        authenticationInProgress = false
                        if (errorCode !in SILENT_AUTH_ERROR_CODES) {
                            Toast.makeText(
                                this@PrivateDiaryActivity,
                                "Authentication unavailable: $errString",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            )
        }.onFailure { error ->
            authenticationInProgress = false
            val fallback = keyguard?.createConfirmDeviceCredentialIntent(
                "Unlock F.R.I.D.A.Y. Private Diary",
                "Confirm the device owner to open the encrypted vault."
            )
            if (fallback != null) {
                authenticationInProgress = true
                startActivityForResult(fallback, REQUEST_DEVICE_CREDENTIAL)
            } else {
                Toast.makeText(
                    this,
                    "No secure device authentication is configured: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    @Deprecated("Android credential confirmation callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_DEVICE_CREDENTIAL) return
        authenticationInProgress = false
        if (resultCode == RESULT_OK) {
            unlocked = true
            secureFinishStarted = false
            store.recordAccess(VAULT_EVENT_ID, "VAULT_UNLOCK", "DEVICE CREDENTIAL AUTHENTICATED")
            showVaultHome()
        }
    }

    private fun showVaultHome(searchOverride: String = requestedSearch) {
        if (!unlocked || secureFinishStarted) return
        requestedSearch = ""
        if (requestedNewEntry) {
            requestedNewEntry = false
            showEditor(null)
            return
        }

        val root = pageRoot()
        root.addView(title("F.R.I.D.A.Y. // PRIVATE DIARY", 21f), params(bottom = 4))
        root.addView(
            centerLabel("LOCAL ENCRYPTION // OWNER-CONTROLLED AI VISIBILITY", 9f, MUTED, true),
            params(bottom = 14)
        )
        root.addView(panel().apply {
            addView(label("VAULT OPEN // SCREEN CAPTURE BLOCKED", 12f, GREEN, true), params(bottom = 6))
            addView(
                label(
                    "Diary content is separate from long-term memory. Owner Only entries remain unreadable to F.R.I.D.A.Y.",
                    11f,
                    SOFT,
                    false
                )
            )
        }, params(bottom = 10))

        val searchField = input("Search your diary", searchOverride, singleLine = true)
        root.addView(searchField, params(bottom = 7))
        root.addView(button("SEARCH OWNER VIEW", BLUE) {
            showVaultHome(searchField.text.toString())
        }, params(bottom = 7))
        root.addView(button("CREATE NEW ENTRY", GREEN) { showEditor(null) }, params(bottom = 12))

        val query = searchOverride.trim()
        val entries = if (query.isBlank()) store.listEntries() else store.searchOwnerView(query)
        root.addView(
            label(
                if (query.isBlank()) "ENTRIES // ${entries.size}" else "SEARCH RESULTS // ${entries.size}",
                12f,
                CYAN,
                true
            ),
            params(bottom = 7)
        )

        if (entries.isEmpty()) {
            root.addView(panel().apply {
                addView(
                    label(
                        if (query.isBlank()) "No diary entries yet." else "No matching entries were found.",
                        12f,
                        MUTED,
                        false
                    )
                )
            }, params(bottom = 10))
        } else {
            entries.forEach { entry -> root.addView(entryPanel(entry), params(bottom = 8)) }
        }

        root.addView(button("ACCESS HISTORY", CYAN) { showAudit() }, params(bottom = 7))
        root.addView(button("SECURE THE VAULT", RED) { finishSecurely("OWNER COMMAND") }, params(bottom = 7))
        root.addView(button("RETURN TO F.R.I.D.A.Y.", BLUE) { finishSecurely("OWNER EXIT") })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun entryPanel(entry: PrivateDiaryEntry): LinearLayout = panel().apply {
        addView(label(entry.title.ifBlank { "UNTITLED ENTRY" }, 14f, Color.WHITE, true), params(bottom = 5))
        addView(label(formatTime(entry.updatedAtMs), 10f, MUTED, false), params(bottom = 5))
        addView(label(entry.aiAccess.label(), 9f, accessColor(entry.aiAccess), true), params(bottom = 7))
        val preview = entry.body.replace(Regex("\\s+"), " ").trim().take(180)
        addView(label(preview.ifBlank { "No written content." }, 11f, SOFT, false), params(bottom = 8))
        if (entry.tags.isNotEmpty()) {
            addView(label("TAGS // ${entry.tags.joinToString(" // ")}", 9f, MUTED, false), params(bottom = 8))
        }
        addView(button("OPEN ENTRY", BLUE) { showEditor(entry) })
    }

    private fun showEditor(existing: PrivateDiaryEntry?) {
        if (!unlocked || secureFinishStarted) return
        existing?.let { store.recordAccess(it.id, "OWNER_OPEN") }
        val root = pageRoot()
        root.addView(
            title(if (existing == null) "PRIVATE DIARY // NEW ENTRY" else "PRIVATE DIARY // EDIT ENTRY", 20f),
            params(bottom = 12)
        )

        root.addView(label("TITLE", 10f, MUTED, true), params(bottom = 4))
        val titleField = input("Entry title", existing?.title.orEmpty(), singleLine = true)
        root.addView(titleField, params(bottom = 10))

        root.addView(label("ENTRY", 10f, MUTED, true), params(bottom = 4))
        val bodyField = input("Write privately...", existing?.body.orEmpty(), singleLine = false).apply {
            gravity = Gravity.TOP or Gravity.START
            minLines = 12
            maxLines = 28
        }
        root.addView(
            bodyField,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(310)).apply {
                bottomMargin = dp(10)
            }
        )

        root.addView(label("TAGS", 10f, MUTED, true), params(bottom = 4))
        val tagsField = input("project, school, idea", existing?.tags?.joinToString(", ").orEmpty(), true)
        root.addView(tagsField, params(bottom = 10))

        root.addView(label("F.R.I.D.A.Y. ACCESS", 10f, MUTED, true), params(bottom = 4))
        val accessValues = DiaryAiAccess.values().toList()
        val accessSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@PrivateDiaryActivity,
                android.R.layout.simple_spinner_dropdown_item,
                accessValues.map(DiaryAiAccess::label)
            )
            setSelection(accessValues.indexOf(existing?.aiAccess ?: DiaryAiAccess.OWNER_ONLY))
            background = inputBackground()
        }
        root.addView(accessSpinner, params(bottom = 8))
        root.addView(
            label(
                "Owner Only blocks AI reading. Ask Each Time requires temporary approval. Session Readable allows help without memory. Memory Approved still requires a separate owner confirmation before facts are saved.",
                10f,
                MUTED,
                false
            ),
            params(bottom = 12)
        )

        root.addView(button("SAVE ENCRYPTED ENTRY", GREEN) {
            val selectedAccess = accessValues.getOrElse(accessSpinner.selectedItemPosition) {
                DiaryAiAccess.OWNER_ONLY
            }
            val saved = store.upsert(
                PrivateDiaryEntry(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    title = titleField.text.toString().trim().ifBlank { "Untitled entry" },
                    body = bodyField.text.toString(),
                    tags = tagsField.text.toString().split(','),
                    aiAccess = selectedAccess,
                    createdAtMs = existing?.createdAtMs ?: System.currentTimeMillis()
                )
            )
            Toast.makeText(this, "Encrypted diary entry saved.", Toast.LENGTH_SHORT).show()
            showEditor(saved)
        }, params(bottom = 7))

        if (existing != null) {
            root.addView(button("DELETE ENTRY", RED) {
                store.delete(existing.id)
                Toast.makeText(this, "Diary entry deleted.", Toast.LENGTH_SHORT).show()
                showVaultHome()
            }, params(bottom = 7))
        }

        root.addView(button("BACK TO DIARY", BLUE) { showVaultHome() }, params(bottom = 7))
        root.addView(button("SECURE THE VAULT", RED) { finishSecurely("OWNER COMMAND") })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showAudit() {
        if (!unlocked || secureFinishStarted) return
        val events = store.auditFor().take(100)
        val root = pageRoot()
        root.addView(title("PRIVATE DIARY // ACCESS HISTORY", 20f), params(bottom = 12))
        if (events.isEmpty()) {
            root.addView(label("No vault access events recorded.", 12f, MUTED, false), params(bottom = 12))
        } else {
            events.forEach { event ->
                root.addView(panel().apply {
                    addView(label(event.action, 11f, GREEN, true), params(bottom = 4))
                    addView(label(formatTime(event.occurredAtMs), 10f, MUTED, false), params(bottom = 4))
                    if (event.entryId != VAULT_EVENT_ID) {
                        addView(label("ENTRY // ${event.entryId.take(12)}", 9f, SOFT, false), params(bottom = 4))
                    }
                    if (event.detail.isNotBlank()) addView(label(event.detail, 10f, SOFT, false))
                }, params(bottom = 7))
            }
        }
        root.addView(button("BACK TO DIARY", BLUE) { showVaultHome() }, params(bottom = 7))
        root.addView(button("SECURE THE VAULT", RED) { finishSecurely("OWNER COMMAND") })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun notifySecureSurface(open: Boolean) {
        sendBroadcast(
            Intent(if (open) ACTION_SECURE_SURFACE_OPENED else ACTION_SECURE_SURFACE_CLOSED)
                .setPackage(packageName)
        )
    }

    private fun clearClipboard() {
        runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private fun pageRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(18), dp(14), dp(28))
        setBackgroundColor(BG)
    }

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.rgb(4, 12, 28))
            setStroke(dp(1), BLUE)
            cornerRadius = dp(8).toFloat()
        }
    }

    private fun input(hint: String, value: String, singleLine: Boolean): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
        setHintTextColor(MUTED)
        setTextColor(Color.WHITE)
        setSingleLine(singleLine)
        background = inputBackground()
        setPadding(dp(11), dp(10), dp(11), dp(10))
    }

    private fun inputBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.rgb(3, 10, 24))
        setStroke(dp(1), Color.rgb(42, 93, 151))
        cornerRadius = dp(6).toFloat()
    }

    private fun title(text: String, size: Float): TextView = centerLabel(text, size, CYAN, true).apply {
        letterSpacing = .08f
    }

    private fun centerLabel(text: String, size: Float, color: Int, bold: Boolean): TextView =
        label(text, size, color, bold).apply { gravity = Gravity.CENTER_HORIZONTAL }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            setLineSpacing(0f, 1.16f)
        }

    private fun button(text: String, accent: Int, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 10f
        letterSpacing = .07f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.rgb(7, 18, 39))
            setStroke(dp(1), accent)
            cornerRadius = dp(5).toFloat()
        }
        setOnClickListener { action() }
    }

    private fun params(bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(bottom) }

    private fun accessColor(access: DiaryAiAccess): Int = when (access) {
        DiaryAiAccess.OWNER_ONLY -> RED
        DiaryAiAccess.ASK_EVERY_TIME -> GOLD
        DiaryAiAccess.SESSION_READABLE -> CYAN
        DiaryAiAccess.MEMORY_APPROVED -> GREEN
    }

    private fun formatTime(value: Long): String =
        SimpleDateFormat("dd MMM yyyy // hh:mm a", Locale.getDefault()).format(Date(value))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_SECURE_SURFACE_OPENED = "com.seongja.jarvis.PRIVATE_DIARY_OPENED"
        const val ACTION_SECURE_SURFACE_CLOSED = "com.seongja.jarvis.PRIVATE_DIARY_CLOSED"
        private const val EXTRA_NEW_ENTRY = "friday_diary_new_entry"
        private const val EXTRA_SEARCH = "friday_diary_search"
        private const val EXTRA_SECURE_NOW = "friday_diary_secure_now"
        private const val REQUEST_DEVICE_CREDENTIAL = 6102
        private const val VAULT_EVENT_ID = "__VAULT__"
        private const val AUTH_ERROR_NEGATIVE_BUTTON = 13
        private val SILENT_AUTH_ERROR_CODES = setOf(
            BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
            BiometricPrompt.BIOMETRIC_ERROR_CANCELED,
            AUTH_ERROR_NEGATIVE_BUTTON
        )

        private val BG = Color.rgb(1, 5, 14)
        private val CYAN = Color.rgb(110, 224, 255)
        private val BLUE = Color.rgb(54, 132, 255)
        private val GREEN = Color.rgb(81, 220, 155)
        private val RED = Color.rgb(255, 92, 106)
        private val GOLD = Color.rgb(244, 194, 91)
        private val SOFT = Color.rgb(181, 211, 242)
        private val MUTED = Color.rgb(116, 144, 178)

        fun launch(context: Context, newEntry: Boolean = false, search: String = "") {
            context.startActivity(
                Intent(context, PrivateDiaryActivity::class.java)
                    .putExtra(EXTRA_NEW_ENTRY, newEntry)
                    .putExtra(EXTRA_SEARCH, search.trim())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }

        fun secure(context: Context) {
            if (PrivateDiaryRuntime.secureActive("OWNER COMMAND")) return
            context.startActivity(
                Intent(context, PrivateDiaryActivity::class.java)
                    .putExtra(EXTRA_SECURE_NOW, true)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    )
            )
        }
    }
}

object PrivateDiaryRuntime {
    private var active: WeakReference<PrivateDiaryActivity>? = null

    @Synchronized
    fun attach(activity: PrivateDiaryActivity) {
        active = WeakReference(activity)
    }

    @Synchronized
    fun detach(activity: PrivateDiaryActivity) {
        if (active?.get() === activity) active = null
    }

    @Synchronized
    fun secureActive(reason: String): Boolean {
        val activity = active?.get() ?: return false
        activity.runOnUiThread { activity.finishSecurely(reason) }
        return true
    }
}
