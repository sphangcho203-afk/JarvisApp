package com.seongja.jarvis

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MemoryVaultActivity : Activity() {
    private lateinit var store: StructuredMemoryStore
    private lateinit var legacy: MemoryVault
    private var unlocked = false
    private var externalFlow = false
    private var initialQuery = ""
    private var initialScreen = SCREEN_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store = StructuredMemoryStore(this)
        legacy = MemoryVault(this)
        initialQuery = intent.getStringExtra(EXTRA_QUERY).orEmpty().trim()
        initialScreen = intent.getStringExtra(EXTRA_SCREEN).orEmpty().ifBlank { SCREEN_HOME }
        showLocked()
        authenticateOwner()
    }

    override fun onStop() {
        if (unlocked && !externalFlow && !isChangingConfigurations) {
            unlocked = false
            store.clearSession()
            finish()
        }
        super.onStop()
    }

    @Deprecated("Android credential confirmation callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        externalFlow = false
        when (requestCode) {
            REQUEST_CREDENTIAL -> {
                if (resultCode == RESULT_OK) {
                    unlocked = true
                    store.importLegacy(legacy)
                    store.clearExpired()
                    when (initialScreen) {
                        SCREEN_PENDING -> showPending()
                        SCREEN_ARCHIVE -> showArchive()
                        else -> showHome(initialQuery)
                    }
                    initialQuery = ""
                    initialScreen = SCREEN_HOME
                } else {
                    Toast.makeText(this, "Memory Vault remains locked.", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_EXPORT -> if (resultCode == RESULT_OK) exportTo(data)
            REQUEST_IMPORT -> if (resultCode == RESULT_OK) importFrom(data)
        }
    }

    private fun authenticateOwner() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        val authIntent = keyguard?.createConfirmDeviceCredentialIntent(
            "Unlock F.R.I.D.A.Y. Memory Vault",
            "Confirm the device owner to review personal memory."
        )
        if (authIntent == null) {
            Toast.makeText(this, "Configure a secure screen lock first.", Toast.LENGTH_LONG).show()
            return
        }
        externalFlow = true
        startActivityForResult(authIntent, REQUEST_CREDENTIAL)
    }

    private fun showLocked() {
        val root = page()
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(dp(18), dp(40), dp(18), dp(28))
        root.addView(title("F.R.I.D.A.Y. // MEMORY VAULT"), params(bottom = 12))
        root.addView(panel().apply {
            addView(center("OWNER AUTHENTICATION REQUIRED", 14f, GREEN, true), params(bottom = 10))
            addView(center("Review, correction, deletion, retention, and transfer remain under your control.", 11f, SOFT, false), params(bottom = 14))
            addView(button("AUTHENTICATE OWNER", CYAN) { authenticateOwner() }, params(bottom = 7))
            addView(button("RETURN TO F.R.I.D.A.Y.", BLUE) { finish() })
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showHome(query: String = "") {
        if (!unlocked) return
        val root = page()
        val records = if (query.isBlank()) store.list() else store.search(query)
        val pendingCount = store.pendingReview().size
        root.addView(title("F.R.I.D.A.Y. // MEMORY VAULT"), params(bottom = 4))
        root.addView(center("STRUCTURED CONTINUITY // OWNER CONTROLLED", 9f, MUTED, true), params(bottom = 12))
        root.addView(panel().apply {
            addView(label("STRUCTURED RECORDS // ${store.list().size}", 11f, GREEN, true), params(bottom = 4))
            addView(label("PENDING REVIEW // $pendingCount", 11f, GOLD, true), params(bottom = 4))
            addView(label(legacy.summary(), 10f, SOFT, false))
        }, params(bottom = 9))

        val search = input("Search memories", query, true)
        root.addView(search, params(bottom = 6))
        root.addView(button("SEARCH MEMORY", BLUE) { showHome(search.text.toString()) }, params(bottom = 6))
        root.addView(button("CREATE CONFIRMED MEMORY", GREEN) { showEditor(null) }, params(bottom = 6))
        root.addView(button("REVIEW APPROVAL QUEUE", GOLD) { showPending() }, params(bottom = 6))
        root.addView(button("CONVERSATION ARCHIVE", CYAN) { showArchive() }, params(bottom = 11))

        root.addView(label(if (query.isBlank()) "MEMORY RECORDS" else "SEARCH RESULTS // ${records.size}", 12f, CYAN, true), params(bottom = 7))
        if (records.isEmpty()) {
            root.addView(panel().apply { addView(label("No matching records.", 11f, MUTED, false)) }, params(bottom = 9))
        } else {
            records.filter { it.reviewState != MemoryReviewState.PENDING_OWNER_REVIEW }
                .forEach { root.addView(recordPanel(it), params(bottom = 7)) }
        }

        root.addView(button("EXPORT ENCRYPTED MEMORY", CYAN) { beginExport() }, params(bottom = 6))
        root.addView(button("IMPORT ENCRYPTED MEMORY", BLUE) { beginImport() }, params(bottom = 6))
        root.addView(button("CLEAR EXPIRED RECORDS", GOLD) {
            val removed = store.clearExpired()
            Toast.makeText(this, "Removed $removed expired records.", Toast.LENGTH_SHORT).show()
            showHome()
        }, params(bottom = 6))
        root.addView(button("RETURN TO F.R.I.D.A.Y.", BLUE) { finish() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showPending() {
        if (!unlocked) return
        val root = page()
        val pending = store.pendingReview()
        root.addView(title("MEMORY VAULT // APPROVAL QUEUE"), params(bottom = 10))
        root.addView(label("Nothing enters confirmed memory from the diary until you approve it here.", 11f, SOFT, false), params(bottom = 10))
        if (pending.isEmpty()) {
            root.addView(panel().apply { addView(label("No memories await review.", 11f, MUTED, false)) }, params(bottom = 9))
        } else {
            pending.forEach { record ->
                root.addView(panel().apply {
                    addView(label(record.value, 12f, Color.WHITE, true), params(bottom = 5))
                    addView(label("SOURCE // ${record.source.name}", 9f, GOLD, true), params(bottom = 3))
                    addView(label("REFERENCE // ${record.sourceReference.ifBlank { "NONE" }}", 9f, MUTED, false), params(bottom = 7))
                    addView(button("CONFIRM MEMORY", GREEN) { store.confirm(record.id); showPending() }, params(bottom = 5))
                    addView(button("REJECT", RED) { store.reject(record.id); showPending() })
                }, params(bottom = 7))
            }
        }
        root.addView(button("BACK TO MEMORY", BLUE) { showHome() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showArchive() {
        if (!unlocked) return
        val root = page()
        val history = legacy.history().take(120)
        root.addView(title("MEMORY VAULT // CONVERSATION ARCHIVE"), params(bottom = 10))
        root.addView(label("Archived turns remain encrypted locally. Relevant portions are retrieved when needed.", 11f, SOFT, false), params(bottom = 10))
        if (history.isEmpty()) {
            root.addView(panel().apply { addView(label("No archived conversation turns.", 11f, MUTED, false)) }, params(bottom = 9))
        } else {
            history.forEach { turn ->
                root.addView(panel().apply { addView(label(turn.take(1_400), 10f, SOFT, false)) }, params(bottom = 6))
            }
        }
        root.addView(button("BACK TO MEMORY", BLUE) { showHome() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun recordPanel(record: StructuredMemoryRecord): LinearLayout = panel().apply {
        addView(label(record.value, 11f, Color.WHITE, true), params(bottom = 5))
        addView(label("${record.kind.name} // ${record.namespace}", 9f, CYAN, true), params(bottom = 3))
        addView(label("SOURCE // ${record.source.name}", 9f, MUTED, false), params(bottom = 3))
        addView(label("CONFIDENCE // ${(record.confidence * 100).toInt()}%", 9f, MUTED, false), params(bottom = 3))
        addView(label("RETENTION // ${record.retention.name}", 9f, MUTED, false), params(bottom = 3))
        addView(label("SENSITIVITY // ${record.sensitivity.name}", 9f, sensitivityColor(record.sensitivity), true), params(bottom = 3))
        addView(label("LAST VERIFIED // ${formatTime(record.lastVerifiedAtMs)}", 9f, MUTED, false), params(bottom = 7))
        addView(button("EDIT OR CORRECT", BLUE) { showEditor(record) }, params(bottom = 5))
        addView(button("DELETE", RED) { store.delete(record.id); showHome() })
    }

    private fun showEditor(existing: StructuredMemoryRecord?) {
        if (!unlocked) return
        val root = page()
        root.addView(title(if (existing == null) "MEMORY // NEW RECORD" else "MEMORY // EDIT RECORD"), params(bottom = 10))
        val value = input("What should F.R.I.D.A.Y. remember?", existing?.value.orEmpty(), false).apply {
            minLines = 5
            maxLines = 12
            gravity = Gravity.TOP or Gravity.START
        }
        val namespace = input("Namespace, such as friday or school", existing?.namespace ?: "general", true)
        val kinds = MemoryKind.values().toList()
        val sensitivities = MemorySensitivity.values().toList()
        val retentions = MemoryRetention.values().toList()
        val kind = spinner(kinds.map { it.name }, kinds.indexOf(existing?.kind ?: MemoryKind.EPISODE))
        val sensitivity = spinner(sensitivities.map { it.name }, sensitivities.indexOf(existing?.sensitivity ?: MemorySensitivity.NORMAL))
        val retention = spinner(retentions.map { it.name }, retentions.indexOf(existing?.retention ?: MemoryRetention.PERMANENT))

        root.addView(label("MEMORY", 9f, MUTED, true), params(bottom = 3))
        root.addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190)).apply { bottomMargin = dp(8) })
        root.addView(label("NAMESPACE", 9f, MUTED, true), params(bottom = 3)); root.addView(namespace, params(bottom = 8))
        root.addView(label("TYPE", 9f, MUTED, true), params(bottom = 3)); root.addView(kind, params(bottom = 8))
        root.addView(label("SENSITIVITY", 9f, MUTED, true), params(bottom = 3)); root.addView(sensitivity, params(bottom = 8))
        root.addView(label("RETENTION", 9f, MUTED, true), params(bottom = 3)); root.addView(retention, params(bottom = 10))
        root.addView(button("SAVE CONFIRMED MEMORY", GREEN) {
            val text = value.text.toString().trim()
            if (text.isBlank()) {
                Toast.makeText(this, "Memory text cannot be empty.", Toast.LENGTH_SHORT).show()
                return@button
            }
            store.upsert(
                StructuredMemoryRecord(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    value = text,
                    kind = kinds[kind.selectedItemPosition],
                    source = if (existing == null) MemorySource.OWNER_CONFIRMED else existing.source,
                    confidence = 1f,
                    namespace = namespace.text.toString(),
                    sensitivity = sensitivities[sensitivity.selectedItemPosition],
                    retention = retentions[retention.selectedItemPosition],
                    reviewState = MemoryReviewState.CONFIRMED,
                    sourceReference = existing?.sourceReference.orEmpty(),
                    createdAtMs = existing?.createdAtMs ?: System.currentTimeMillis(),
                    lastVerifiedAtMs = System.currentTimeMillis()
                )
            )
            showHome()
        }, params(bottom = 6))
        root.addView(button("CANCEL", BLUE) { showHome() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun beginExport() {
        externalFlow = true
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "FRIDAY-memory-${System.currentTimeMillis()}.fridaymem")
        }, REQUEST_EXPORT)
    }

    private fun beginImport() {
        externalFlow = true
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
        }, REQUEST_IMPORT)
    }

    private fun exportTo(data: Intent?) {
        data?.data?.let { uri ->
            runCatching {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(store.exportEncryptedPayload()) }
                    ?: error("Export destination unavailable")
            }.onSuccess { Toast.makeText(this, "Encrypted memory export created.", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(this, "Export failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun importFrom(data: Intent?) {
        data?.data?.let { uri ->
            runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Import file unavailable")
            }.onSuccess { payload ->
                val count = store.importEncryptedPayload(payload)
                Toast.makeText(this, "Imported $count records.", Toast.LENGTH_LONG).show()
                showHome()
            }.onFailure { Toast.makeText(this, "Import failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    private fun page() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(20), dp(14), dp(28))
        setBackgroundColor(BG)
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply { setColor(Color.rgb(4, 12, 28)); setStroke(dp(1), BLUE); cornerRadius = dp(8).toFloat() }
    }

    private fun input(hint: String, value: String, singleLine: Boolean) = EditText(this).apply {
        this.hint = hint; setText(value); setSingleLine(singleLine); setTextColor(Color.WHITE); setHintTextColor(MUTED)
        background = fieldBackground(); setPadding(dp(10), dp(9), dp(10), dp(9))
    }

    private fun spinner(values: List<String>, selection: Int) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MemoryVaultActivity, android.R.layout.simple_spinner_dropdown_item, values)
        setSelection(selection.coerceAtLeast(0)); background = fieldBackground()
    }

    private fun fieldBackground() = GradientDrawable().apply {
        setColor(Color.rgb(3, 10, 24)); setStroke(dp(1), Color.rgb(42, 93, 151)); cornerRadius = dp(6).toFloat()
    }

    private fun title(text: String) = center(text, 21f, CYAN, true).apply { letterSpacing = .08f }
    private fun center(text: String, size: Float, color: Int, bold: Boolean) = label(text, size, color, bold).apply { gravity = Gravity.CENTER_HORIZONTAL }
    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        setLineSpacing(0f, 1.15f)
    }
    private fun button(text: String, accent: Int, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 10f; letterSpacing = .06f; setTextColor(Color.WHITE)
        background = GradientDrawable().apply { setColor(Color.rgb(7, 18, 39)); setStroke(dp(1), accent); cornerRadius = dp(5).toFloat() }
        setOnClickListener { action() }
    }
    private fun params(bottom: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun formatTime(value: Long) = if (value <= 0L) "NOT VERIFIED" else SimpleDateFormat("dd MMM yyyy // hh:mm a", Locale.getDefault()).format(Date(value))
    private fun sensitivityColor(value: MemorySensitivity) = when (value) {
        MemorySensitivity.NORMAL -> GREEN
        MemorySensitivity.PRIVATE -> GOLD
        MemorySensitivity.HIGHLY_SENSITIVE -> RED
    }

    companion object {
        private const val REQUEST_CREDENTIAL = 6201
        private const val REQUEST_EXPORT = 6202
        private const val REQUEST_IMPORT = 6203
        private const val EXTRA_QUERY = "friday_memory_query"
        private const val EXTRA_SCREEN = "friday_memory_screen"
        private const val SCREEN_HOME = "home"
        private const val SCREEN_PENDING = "pending"
        private const val SCREEN_ARCHIVE = "archive"
        private val BG = Color.rgb(1, 5, 14)
        private val CYAN = Color.rgb(110, 224, 255)
        private val BLUE = Color.rgb(54, 132, 255)
        private val GREEN = Color.rgb(81, 220, 155)
        private val RED = Color.rgb(255, 92, 106)
        private val GOLD = Color.rgb(244, 194, 91)
        private val SOFT = Color.rgb(181, 211, 242)
        private val MUTED = Color.rgb(116, 144, 178)

        fun launch(context: Context, query: String = "", screen: String = SCREEN_HOME) {
            context.startActivity(Intent(context, MemoryVaultActivity::class.java)
                .putExtra(EXTRA_QUERY, query.trim())
                .putExtra(EXTRA_SCREEN, screen)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        fun launchPending(context: Context) = launch(context, screen = SCREEN_PENDING)
        fun launchArchive(context: Context) = launch(context, screen = SCREEN_ARCHIVE)
    }
}
