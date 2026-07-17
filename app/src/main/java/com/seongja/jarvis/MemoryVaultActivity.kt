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
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.UUID

class MemoryVaultActivity : Activity() {
    private lateinit var store: StructuredMemoryStore
    private lateinit var legacy: MemoryVault
    private var unlocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store = StructuredMemoryStore(this)
        legacy = MemoryVault(this)
        showLocked()
        authenticateOwner()
    }

    override fun onStop() {
        if (unlocked && !isChangingConfigurations) {
            unlocked = false
            finish()
        }
        super.onStop()
    }

    @Deprecated("Android credential confirmation callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CREDENTIAL -> if (resultCode == RESULT_OK) {
                unlocked = true
                store.importLegacy(legacy)
                store.clearExpired()
                showHome()
            }
            REQUEST_EXPORT -> if (resultCode == RESULT_OK) {
                data?.data?.let { uri ->
                    runCatching {
                        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                            it.write(store.exportEncryptedPayload())
                        } ?: error("Export destination unavailable")
                    }.onSuccess {
                        Toast.makeText(this, "Encrypted memory export created.", Toast.LENGTH_LONG).show()
                    }.onFailure { error ->
                        Toast.makeText(this, "Export failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            REQUEST_IMPORT -> if (resultCode == RESULT_OK) {
                data?.data?.let { uri ->
                    runCatching {
                        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Import file unavailable")
                    }.onSuccess { payload ->
                        val count = store.importEncryptedPayload(payload)
                        Toast.makeText(this, "Imported $count memory records.", Toast.LENGTH_LONG).show()
                        showHome()
                    }.onFailure { error ->
                        Toast.makeText(this, "Import failed: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun authenticateOwner() {
        val keyguard = getSystemService(KeyguardManager::class.java)
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            "Unlock F.R.I.D.A.Y. Memory Vault",
            "Confirm the device owner to review personal memory."
        )
        if (intent == null) {
            Toast.makeText(this, "Configure a secure device lock first.", Toast.LENGTH_LONG).show()
            return
        }
        startActivityForResult(intent, REQUEST_CREDENTIAL)
    }

    private fun showLocked() {
        val root = page()
        root.addView(title("F.R.I.D.A.Y. // MEMORY VAULT"), params(bottom = 12))
        root.addView(panel().apply {
            addView(center("OWNER AUTHENTICATION REQUIRED", 14f, GREEN, true), params(bottom = 10))
            addView(center("Memory review, correction, deletion, import, and export are owner-controlled.", 11f, SOFT, false), params(bottom = 14))
            addView(button("AUTHENTICATE OWNER", CYAN) { authenticateOwner() }, params(bottom = 7))
            addView(button("RETURN TO F.R.I.D.A.Y.", BLUE) { finish() })
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showHome(query: String = "") {
        if (!unlocked) return
        val root = page()
        val pending = store.pendingReview()
        val records = if (query.isBlank()) store.list() else store.search(query)
        root.addView(title("F.R.I.D.A.Y. // MEMORY VAULT"), params(bottom = 5))
        root.addView(center("STRUCTURED CONTINUITY // OWNER CONTROLLED", 9f, MUTED, true), params(bottom = 12))
        root.addView(panel().apply {
            addView(label("RECORDS // ${records.size}", 11f, GREEN, true), params(bottom = 4))
            addView(label("PENDING REVIEW // ${pending.size}", 11f, GOLD, true), params(bottom = 4))
            addView(label(legacy.summary(), 10f, SOFT, false))
        }, params(bottom = 9))

        val search = input("Search memory", query)
        root.addView(search, params(bottom = 6))
        root.addView(button("SEARCH", BLUE) { showHome(search.text.toString()) }, params(bottom = 6))
        root.addView(button("CREATE MEMORY", GREEN) { showEditor(null) }, params(bottom = 10))

        if (pending.isNotEmpty()) {
            root.addView(label("APPROVAL QUEUE", 12f, GOLD, true), params(bottom = 6))
            pending.forEach { record ->
                root.addView(panel().apply {
                    addView(label(record.value, 11f, Color.WHITE, true), params(bottom = 5))
                    addView(label("SOURCE // ${record.source.name}", 9f, MUTED, false), params(bottom = 7))
                    addView(button("CONFIRM", GREEN) { store.confirm(record.id); showHome() }, params(bottom = 5))
                    addView(button("REJECT", RED) { store.reject(record.id); showHome() })
                }, params(bottom = 7))
            }
        }

        records.filter { it.reviewState != MemoryReviewState.PENDING_OWNER_REVIEW }.forEach { record ->
            root.addView(panel().apply {
                addView(label(record.value, 11f, Color.WHITE, true), params(bottom = 5))
                addView(label("${record.kind.name} // ${record.namespace}", 9f, CYAN, true), params(bottom = 3))
                addView(label("SOURCE // ${record.source.name}", 9f, MUTED, false), params(bottom = 3))
                addView(label("RETENTION // ${record.retention.name}", 9f, MUTED, false), params(bottom = 6))
                addView(button("EDIT", BLUE) { showEditor(record) }, params(bottom = 5))
                addView(button("DELETE", RED) { store.delete(record.id); showHome() })
            }, params(bottom = 7))
        }

        root.addView(button("EXPORT ENCRYPTED MEMORY", CYAN) { beginExport() }, params(bottom = 6))
        root.addView(button("IMPORT ENCRYPTED MEMORY", BLUE) { beginImport() }, params(bottom = 6))
        root.addView(button("RETURN TO F.R.I.D.A.Y.", BLUE) { finish() })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun showEditor(existing: StructuredMemoryRecord?) {
        if (!unlocked) return
        val root = page()
        root.addView(title(if (existing == null) "MEMORY // NEW RECORD" else "MEMORY // EDIT RECORD"), params(bottom = 10))
        val value = input("What should F.R.I.D.A.Y. remember?", existing?.value.orEmpty()).apply {
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
        }
        val namespace = input("Namespace", existing?.namespace ?: "general")
        root.addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)).apply { bottomMargin = dp(8) })
        root.addView(namespace, params(bottom = 8))
        root.addView(button("SAVE CONFIRMED MEMORY", GREEN) {
            val text = value.text.toString().trim()
            if (text.isBlank()) return@button
            store.upsert(
                StructuredMemoryRecord(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    value = text,
                    kind = existing?.kind ?: MemoryKind.EPISODE,
                    source = existing?.source ?: MemorySource.OWNER_CONFIRMED,
                    confidence = 1f,
                    namespace = namespace.text.toString(),
                    sensitivity = existing?.sensitivity ?: MemorySensitivity.NORMAL,
                    retention = existing?.retention ?: MemoryRetention.PERMANENT,
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
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, "FRIDAY-memory.fridaymem")
        }, REQUEST_EXPORT)
    }

    private fun beginImport() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
        }, REQUEST_IMPORT)
    }

    private fun page() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(20), dp(14), dp(28))
        setBackgroundColor(BG)
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.rgb(4, 12, 28)); setStroke(dp(1), BLUE); cornerRadius = dp(8).toFloat()
        }
    }

    private fun input(hint: String, value: String) = EditText(this).apply {
        this.hint = hint; setText(value); setTextColor(Color.WHITE); setHintTextColor(MUTED)
        background = GradientDrawable().apply {
            setColor(Color.rgb(3, 10, 24)); setStroke(dp(1), Color.rgb(42, 93, 151)); cornerRadius = dp(6).toFloat()
        }
        setPadding(dp(10), dp(9), dp(10), dp(9))
    }

    private fun title(text: String) = center(text, 21f, CYAN, true).apply { letterSpacing = .08f }
    private fun center(text: String, size: Float, color: Int, bold: Boolean) = label(text, size, color, bold).apply { gravity = Gravity.CENTER_HORIZONTAL }
    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    private fun button(text: String, accent: Int, action: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 10f; setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.rgb(7, 18, 39)); setStroke(dp(1), accent); cornerRadius = dp(5).toFloat()
        }
        setOnClickListener { action() }
    }
    private fun params(bottom: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(bottom) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_CREDENTIAL = 6201
        private const val REQUEST_EXPORT = 6202
        private const val REQUEST_IMPORT = 6203
        private val BG = Color.rgb(1, 5, 14)
        private val CYAN = Color.rgb(110, 224, 255)
        private val BLUE = Color.rgb(54, 132, 255)
        private val GREEN = Color.rgb(81, 220, 155)
        private val RED = Color.rgb(255, 92, 106)
        private val GOLD = Color.rgb(244, 194, 91)
        private val SOFT = Color.rgb(181, 211, 242)
        private val MUTED = Color.rgb(116, 144, 178)

        fun launch(context: Context) {
            context.startActivity(Intent(context, MemoryVaultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
