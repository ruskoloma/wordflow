package com.rsln.wordflow.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stores and exposes the currently-active Clerk session JWT plus the
 * email address the user signed in with.
 *
 * The JWT is what matters to the backend — it goes on the
 * Authorization header of every request via AuthInterceptor. The
 * email is cosmetic: we cache it so Settings can show "signed in as
 * ..." without having to decode JWT custom claims or do an extra
 * Clerk lookup.
 *
 * Both values live in EncryptedSharedPreferences so they survive
 * process death without sitting in plain text on disk.
 */
class AuthManager(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // The Compose UI observes these two flows. currentEmail drives
    // the "signed in as ..." label in Settings; currentUserId is the
    // auth gate's "am I signed in?" signal — we keep it around even
    // though nothing displays it anymore so the gate stays a simple
    // null/non-null check.
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId

    private val _currentEmail = MutableStateFlow<String?>(null)
    val currentEmail: StateFlow<String?> = _currentEmail

    init {
        _currentUserId.value = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }
        _currentEmail.value = prefs.getString(KEY_EMAIL, null)?.takeIf { it.isNotBlank() }
    }

    /** Returns the stored JWT, or null if none is set. */
    fun currentToken(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    /**
     * Persist a token alongside the user id + email. Called from the
     * LoginViewModel after /v1/auth/email/verify returns successfully.
     */
    fun setSession(token: String, userId: String, email: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_USER_ID, userId.trim())
            .putString(KEY_EMAIL, email.trim())
            .apply()
        _currentUserId.value = userId
        _currentEmail.value = email
    }

    /** Clear all auth state. Called from the Sign out button. */
    fun signOut() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .apply()
        _currentUserId.value = null
        _currentEmail.value = null
    }

    /** Convenience for the UI to check sign-in state synchronously. */
    fun isSignedIn(): Boolean = currentToken() != null

    companion object {
        private const val PREFS_FILE = "wordflow_auth"
        private const val KEY_TOKEN = "clerk_token"
        private const val KEY_USER_ID = "clerk_user_id"
        private const val KEY_EMAIL = "clerk_email"
    }
}
