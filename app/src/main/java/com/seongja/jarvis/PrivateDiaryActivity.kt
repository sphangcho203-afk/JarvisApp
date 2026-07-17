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
import android.view.View
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
import java.util.concurrent.Executor

class PrivateDiaryActivity : Activity() {
    private lateinit var store: PrivateDiaryStore
    private var unlocked = false
    private var authenticationInProgress = false
    private var cancellationSignal: CancellationSignal? = null
    private var requestedSearch: String = ""
    private var requestedNewEntry = false
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
        }
    }

    override fun onStop() {
        super.onStop()
        if (unlocked && !authenticationInProgress && !isChangingConfigurations) {
            finishSecurely("BACKGROUND AUTO-LOCK")
        }
    }

    override fun onDestroy() {
        cancellationSignal?.cancel()
        PrivateDiaryRuntime.detach(this)
        notifySecureSurface(false)
        super.onDestroy()
    }

    fun finishSecurely(reason: String) {
        unlocked = false
        requestedSearch = ""
        clearClipboard()
        store.recordAccess(VAULT_EVENT_ID, "VAULT_LOCK", reason)
        notifySecureSurface(false)
        setContentView(buildLockedPanel("DIARY SEALED // $reason"))
        window.decorView.postDelayed({ finish() }, 120L)
    }

    private fun showLockedSurface() {
        setContentView(buildLockedPanel("OWNER AUTHENTICATION REQUIRED"))
    }

    private fun buildLockedPanel(status: String): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(36), dp(18), dp(30))
            setBackgroundColor(BG)
        }
        root.addView(label("F.R.I.D.A.Y. // PRIVATE DIARY", 22f, CYAN, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .08f
        }, params(bottom = 8))
        root.addView(label("ENCRYPTED OWNER VAULT", 10f, MUTED, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .15f
        }, params(bottom = 22))

        val panel = panel().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(label("VAULT LOCKED", 18f, GREEN, true).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }, params(bottom = 10))
            addView(label(status, 11f, SOFT, false).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }, params(bottom = 14))
            addView(label(
                "Diary pages remain encrypted and are not available to F.R.I.D.A.Y. unless each entry permits access.",
                12f,
                MUTED,
                false
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }, params(bottom = 18))
            addView(button("AUTHENTICATE OWNER", CYAN) { authenticateOwner() }, params(bottom = 8))
            addView(button("RETURN TO F.R.I.D.A.Y.", BLUE) { finishSecurely("OWNER EXIT") })
        }
        root.addView(panel, params())
        return ScrollView(this).apply { addView(root) }
    }

    private fun authenticateOwner() {
        if (authenticationInProgress || unlocked) return
        authenticationInProgress = true
        cancellationSignal?.cancel()
        cancellationSignal = CancellationSignal()

        val keyguard = getSystemService(KeyguardManager::class.java)
        val builder = BiometricPrompt.Builder(this)
            .setTitle("Unlock F.R.I.D.A.Y. Private Diary")
            .setSubtitle("Owner authentication")
            .setDescription("Protected entries remain encrypted until Android verifies the device owner.")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && keyguard?.isDeviceSecure == true) {
            builder.setDeviceCredentialAllowed(true)
        } else {
            builder.setNegativeButton("Cancel", mainExecutor) { _, _ ->
                authenticationInProgress = false
                cancellationSignal?.cancel()
            }
        }

        runCatching {
            builder.build().authenticate(
                cancellationSignal,
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        authenticationInProgress = false
                        unlocked = true
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
                        if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                            errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED &&
                            errorCode != BiometricPrompt.BIOMETRIC_ERROR_NEGATIVE_BUTTON
                        ) {
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
        if (requestCode == REQUEST_DEVICE_CREDENTIAL) {
            authenticationInProgress = false
            if (resultCode == RESULT_OK) {
                unlocked = true
                store.recordAccess(VAULT_EVENT_ID, "VAULT_UNLOCK", "DEVICE CREDENTIAL AUTHENTICATED")
                showVaultHome()
            }
        }
    }

    private fun showVaultHome(searchOverride: String = requestedSearch) {
        if (!unlocked) return
        requestedSearch = ""
        if (requestedNewEntry) {
            requestedNewEntry = false
            showEditor(null)
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(18), dp(14), dp(28))
            setBackgroundColor(BG)
        }
        root.addView(label("F.R.I.D.A.Y. // PRIVATE DIARY", 21f, CYAN, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .08f
        }, params(bottom = 4))
        root.addView(label("LOCAL ENCRYPTION // OWNER CONTROLLED AI VISIBILITY", 9f, MUTED, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .11f
        }, params(bottom = 14))

        val statusPanel = panel()
        statusPanel.addView(label("VAULT OPEN // SCREEN CAPTURE BLOCKED", 12f, GREEN, true), params(bottom = 6))
        statusPanel.addView(label(
            "Diary content is separate from long-term memory. Entries marked Owner Only remain unreadable to F.R.I.D.A.Y.",
            11f,
            SOFT,
            false
        ))
        root.addView(statusPanel, params(bottom = 10))

        val searchField = EditText(this).apply {
            hint = "Search your diary"
            setHintTextColor(MUTED)
            setTextColor(Color.WHITE)
            setText(searchOverride)
            setSingleLine(true)
            background = inputBackground()
            setPadding(dp(11), dp(10), dp(11), dp(10))
        }
        root.addView(searchField, params(bottom = 7))
        root.addView(button("SEARCH OWNER VIEW", BLUE) {
            showVaultHome(searchField.text.toString())
        }, params(bottom = 7))
        root.addView(button("CREATE NEW ENTRY", GREEN) { showEditor(null) }, params(bottom = 12))

        val query = searchOverride.trim()
        val entries = if (query.isBlank()) store.listEntries() else store.searchOwnerView(query)
        root.addView(label(
            if (query.isBlank()) "ENTRIES // ${entries.size}" else "SEARCH RESULTS // ${entries.size}",
            12f,
            CYAN,
            true
        ), params(bottom = 7))

        if (entries.isEmpty()) {
            root.addView(panel().apply {
                addView(label(
                    if (query.isBlank()) "No diary entries yet." else "No matching entries were found.",
                    12f,
                    MUTED,
                    false
                ))
            }, params(bottom = 10))
        } else {
            entries.forEach { entry ->
                root.addView(entryPanel(entry), params(bottom = 8))
            }
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
        if (!unlocked) return
        existing?.let { store.recordAccess(it.id, "OWNER_OPEN") }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(18), dp(14), dp(28))
            setBackgroundColor(BG)
        }
        root.addView(label(
            if (existing == null) "PRIVATE DIARY // NEW ENTRY" else "PRIVATE DIARY // EDIT ENTRY",
            20f,
            CYAN,
            true
        ).apply { gravity = Gravity.CENTER_HORIZONTAL }, params(bottom = 12))

        root.addView(label("TITLE", 10f, MUTED, true), params(bottom = 4))
        val titleField = EditText(this).apply {
            setText(existing?.title.orEmpty())
            hint = "Entry title"
            setTextColor(Color.WHITE)
            setHintTextColor(MUTED)
            setSingleLine(true)
            background = inputBackground()
            setPadding(dp(11), dp(10), dp(11), dp(10))
        }
        root.addView(titleField, params(bottom = 10))

        root.addView(label("ENTRY", 10f, MUTED, true), params(bottom = 4))
        val bodyField = EditText(this).apply {
            setText(existing?.body.orEmpty())
            hint = "Write privately..."
            setTextColor(Color.WHITE)
            setHintTextColor(MUTED)
            gravity = Gravity.TOP or Gravity.START
            minLines = 12
            maxLines = 28
            background = inputBackground()
            setPadding(dp(11), dp(11), dp(11), dp(11))
        }
        root.addView(bodyField, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(310)
        ).apply { bottomMargin = dp(10) })

        root.addView(label("TAGS", 10f, MUTED, true), params(bottom = 4))
        val tagsField = EditText(this).apply {
            setText(existing?.tags?.joinToString(", ").orEmpty())
            hint = "project, school, idea"
            setTextColor(Color.WHITE)
            setHintTextColor(MUTED)
            setSingleLine(true)
            background = inputBackground()
            setPadding(dp(11), dp(10), dp(11), dp(10))
        }
        root.addView(tagsField, params(bottom = 10))

        root.addView(label("F.R.I.D.A.Y. ACCESS", 10f, MUTED, true), params(bottom = 4))
        val accessValues = DiaryAiAccess.entries
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
        root.addView(label(
            "Owner Only blocks AI reading. Ask Each Time requires temporary approval. Session Readable permits help without memory. Memory Approved still requires confirmation before facts are saved.",
            10f,
            MUTED,
            false
        ), params(bottom = 12))

        root.addView(button("SAVE ENCRYPTED ENTRY", GREEN) {
            val title = titleField.text.toString().trim().ifBlank { "Untitled entry" }
            val body = bodyField.text.toString()
            val tags = tagsField.text.toString().split(',')
            val selectedAccess = accessValues.getOrElse(accessSpinner.selectedItemPosition) {
                DiaryAiAccess.OWNER_ONLY
            }
            val saved = store.upsert(
                PrivateDiaryEntry(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                    title = title,
                    body = body,
                    tags = tags,
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
        if (!unlocked) return
        val events = store.auditFor().take(100)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(18), dp(14), dp(28))
            setBackgroundColor(BG)
        }
        root.addView(label("PRIVATE DIARY // ACCESS HISTORY", 20f, CYAN, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }, params(bottom = 12))
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
                    if (event.detail.isNotBlank()) {
                        addView(label(event.detail, 10f, SOFT, false))
                    }
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

    private fun accessColor(access: DiaryAiAccess): Int = when (access) {
        DiaryAiAccess.OWNER_ONLY -> RED
        DiaryAiAccess.ASK_EVERY_TIME -> GOLD
        DiaryAiAccess.SESSION_READABLE -> CYAN
        DiaryAiAccess.MEMORY_APPROVED -> GREEN
    }

    private fun formatTime(value: Long): String =
        SimpleDateFormat("dd MMM yyyy // hh:mm a", Locale.getDefault()).format(Date(value))

    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.rgb(4, 12, 28))
            setStroke(dp(1), BLUE)
            cornerRadius = dp(8).toFloat()
        }
    }

    private fun inputBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.rgb(3, 10, 24))
        setStroke(dp(1), Color.rgb(42, 93, 151))
        cornerRadius = dp(6).toFloat()
    }

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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_SECURE_SURFACE_OPENED = "com.seongja.jarvis.PRIVATE_DIARY_OPENED"
        const val ACTION_SECURE_SURFACE_CLOSED = "com.seongja.jarvis.PRIVATE_DIARY_CLOSED"
        private const val EXTRA_NEW_ENTRY = "friday_diary_new_entry"
        private const val EXTRA_SEARCH = "friday_diary_search"
        private const val EXTRA_SECURE_NOW = "friday_diary_secure_now"
        private const val REQUEST_DEVICE_CREDENTIAL = 6102
        private const val VAULT_EVENT_ID = "__VAULT__"

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
            PrivateDiaryRuntime.secureActive("OWNER COMMAND")
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
