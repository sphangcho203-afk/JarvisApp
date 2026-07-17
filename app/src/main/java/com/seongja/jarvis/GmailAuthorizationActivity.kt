package com.seongja.jarvis

import android.accounts.Account
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest

class GmailAuthorizationActivity : Activity() {
    private val authorizationClient by lazy { Identity.getAuthorizationClient(this) }
    private lateinit var store: GmailAuthStore
    private lateinit var status: TextView
    private lateinit var account: TextView
    private lateinit var scopes: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GmailAuthStore(this)
        setContentView(buildUi())
        renderState()
    }

    private fun buildUi(): ScrollView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(30))
            setBackgroundColor(BG)
        }
        root.addView(label("F.R.I.D.A.Y. // GMAIL AUTHORIZATION", 22f, CYAN, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .08f
        }, params(bottom = 6))
        root.addView(label("GOOGLE IDENTITY SERVICES // OWNER CONSENT", 11f, SOFT, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            letterSpacing = .15f
        }, params(bottom = 18))

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = panelBackground()
        }
        status = label("AUTH STATE // CHECKING", 14f, GREEN, true)
        account = label("ACCOUNT // NONE", 12f, SOFT, true)
        scopes = label("SCOPES // MODIFY + SEND", 11f, MUTED, false)
        panel.addView(status, params(bottom = 8))
        panel.addView(account, params(bottom = 8))
        panel.addView(scopes, params(bottom = 12))
        panel.addView(label(
            "F.R.I.D.A.Y. requests Gmail modify and send access so it can read, search, summarize, label, archive, draft, and send only when you command it. Google shows the account and consent screen before access is granted.",
            12f,
            MUTED,
            false
        ), params(bottom = 14))
        panel.addView(button("CONNECT / AUTHORIZE GMAIL", GREEN) { beginAuthorization() }, params(bottom = 8))
        panel.addView(button("VERIFY MAILBOX ACCESS", CYAN) { verifyMailbox() }, params(bottom = 8))
        panel.addView(button("REVOKE GMAIL ACCESS", RED) { revokeAccess() }, params(bottom = 8))
        panel.addView(button("RETURN TO F.R.I.D.A.Y.", BLUE) { finish() })
        root.addView(panel, params())
        return ScrollView(this).apply { addView(root) }
    }

    private fun beginAuthorization() {
        status.text = "AUTH STATE // OPENING GOOGLE CONSENT"
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(GmailScopes.required)
            .setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener(::handleAuthorizationResult)
            .addOnFailureListener { error ->
                store.recordFailure(error.message ?: error.javaClass.simpleName)
                renderState()
            }
    }

    private fun handleAuthorizationResult(result: AuthorizationResult) {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
            if (pendingIntent == null) {
                store.recordFailure("Google authorization resolution was unavailable.")
                renderState()
                return
            }
            runCatching {
                startIntentSenderForResult(
                    pendingIntent.intentSender,
                    REQUEST_GMAIL_AUTH,
                    null,
                    0,
                    0,
                    0
                )
            }.onFailure {
                store.recordFailure(it.message ?: it.javaClass.simpleName)
                renderState()
            }
            return
        }
        acceptAuthorization(result)
    }

    @Deprecated("Google Identity authorization currently returns through Activity results.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_GMAIL_AUTH) return
        if (resultCode != RESULT_OK || data == null) {
            store.recordFailure("Google consent was cancelled or not completed.")
            renderState()
            return
        }
        runCatching { authorizationClient.getAuthorizationResultFromIntent(data) }
            .onSuccess(::acceptAuthorization)
            .onFailure {
                store.recordFailure(it.message ?: it.javaClass.simpleName)
                renderState()
            }
    }

    private fun acceptAuthorization(result: AuthorizationResult) {
        val token = result.accessToken.orEmpty()
        val granted = result.grantedScopes.orEmpty().toSet()
        if (token.isBlank()) {
            store.recordFailure("Google returned the granted scopes but no usable Gmail access token. Try Connect again.")
            renderState()
            return
        }
        if (!GmailScopes.requiredUris.all(granted::contains)) {
            store.recordFailure("Required Gmail permissions were not all granted.")
            renderState()
            return
        }

        status.text = "AUTH STATE // TOKEN GRANTED // RESOLVING MAILBOX"
        Thread {
            val recovered = runCatching { GmailAuthorizationRecovery.profile(token) }
            runOnUiThread {
                recovered.onSuccess { profile ->
                    store.recordAuthorized(profile.emailAddress, granted)
                    showAuthorized(profile)
                }.onFailure { error ->
                    store.recordFailure(error.message ?: error.javaClass.simpleName)
                    renderState()
                }
            }
        }.start()
    }

    private fun verifyMailbox() {
        status.text = "AUTH STATE // VERIFYING GMAIL API"
        Thread {
            val result = runCatching { GmailApiClient(this).profile() }
            runOnUiThread {
                result.onSuccess(::showAuthorized)
                    .onFailure { error ->
                        store.recordFailure(error.message ?: error.javaClass.simpleName)
                        renderState()
                    }
            }
        }.start()
    }

    private fun showAuthorized(profile: GmailProfile) {
        store.recordAuthorized(profile.emailAddress, GmailScopes.requiredUris)
        status.text = "AUTH STATE // AUTHORIZED // HTTP 200"
        account.text = "ACCOUNT // ${profile.emailAddress}"
        scopes.text = "MAILBOX // ${profile.messagesTotal} MESSAGES // ${profile.threadsTotal} THREADS"
    }

    private fun revokeAccess() {
        val state = store.load()
        if (state.accountEmail.isBlank()) {
            store.clear()
            renderState()
            return
        }
        status.text = "AUTH STATE // REVOKING ACCESS"
        val request = RevokeAccessRequest.builder()
            .setAccount(Account(state.accountEmail, "com.google"))
            .setScopes(GmailScopes.required)
            .build()
        authorizationClient.revokeAccess(request)
            .addOnSuccessListener {
                store.clear()
                renderState()
            }
            .addOnFailureListener { error ->
                store.recordFailure(error.message ?: error.javaClass.simpleName)
                renderState()
            }
    }

    private fun renderState() {
        val state = store.load()
        status.text = "AUTH STATE // ${state.statusLabel()}"
        account.text = "ACCOUNT // ${state.accountEmail.ifBlank { "NONE" }}"
        scopes.text = if (state.grantedScopes.isEmpty()) {
            "SCOPES // MODIFY + SEND // CONSENT REQUIRED"
        } else {
            "SCOPES // ${state.grantedScopes.size} GRANTED"
        }
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            setLineSpacing(0f, 1.15f)
        }

    private fun button(text: String, accent: Int, action: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 11f
        letterSpacing = .08f
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.rgb(8, 20, 42))
            setStroke(dp(1), accent)
            cornerRadius = dp(5).toFloat()
        }
        setOnClickListener { action() }
    }

    private fun panelBackground(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.rgb(4, 12, 28))
        setStroke(dp(1), BLUE)
        cornerRadius = dp(7).toFloat()
    }

    private fun params(bottom: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(bottom)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_GMAIL_AUTH = 7301
        private val BG = Color.rgb(1, 5, 14)
        private val CYAN = Color.rgb(110, 224, 255)
        private val BLUE = Color.rgb(54, 132, 255)
        private val GREEN = Color.rgb(81, 220, 155)
        private val RED = Color.rgb(255, 92, 112)
        private val SOFT = Color.rgb(181, 211, 242)
        private val MUTED = Color.rgb(116, 144, 178)

        fun launch(activity: Activity) {
            activity.startActivity(Intent(activity, GmailAuthorizationActivity::class.java))
        }
    }
}
