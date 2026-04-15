package com.rsln.wordflow.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rsln.wordflow.data.auth.AuthManager
import com.rsln.wordflow.data.remote.BackendClient
import com.rsln.wordflow.data.remote.BackendException
import com.rsln.wordflow.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Drives the passwordless login flow. Two user-visible steps:
 *
 *   Step.Email → user types email, taps "Send code". We POST to
 *                /v1/auth/email/start; on success, we stash the
 *                state_id and advance to Step.Code.
 *
 *   Step.Code  → user types the 6-digit code from their inbox.
 *                We POST to /v1/auth/email/verify with the stored
 *                state_id. On success, the JWT goes into AuthManager
 *                and the MainActivity auth gate flips over to the
 *                main nav host automatically.
 *
 * The backend handles everything Clerk-specific: creating users that
 * don't exist yet, talking to Clerk's Frontend API with the dev
 * browser JWT, minting the session token at the end. Android just
 * sees "email → state_id → code → jwt".
 */
class LoginViewModel(
    private val authManager: AuthManager,
    private val backendClient: BackendClient
) : ViewModel() {

    enum class Step { Email, Code }

    private val _step = MutableStateFlow(Step.Email)
    val step: StateFlow<Step> = _step

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Stashed between /start and /verify. Opaque to the UI.
    private var stateId: String? = null

    fun updateEmail(value: String) {
        _email.value = value
        _error.value = null
    }

    fun updateCode(value: String) {
        // Strip everything that isn't a digit so the field stays
        // clean even if the user pastes "Your code: 123456".
        _code.value = value.filter { it.isDigit() }.take(6)
        _error.value = null
    }

    /** Step 1 → Step 2. Starts the email-code flow on the backend. */
    fun sendCode() {
        if (_isSubmitting.value) return
        val emailValue = _email.value.trim()
        if (emailValue.isBlank()) {
            _error.value = "Email is required"
            return
        }

        _isSubmitting.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val response = backendClient.emailAuthStart(emailValue)
                stateId = response.stateId
                _step.value = Step.Code
            } catch (e: BackendException) {
                _error.value = humanMessageFor(e) ?: "Couldn't send code"
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    /** Step 2. Swaps the code for a session JWT. */
    fun verifyCode() {
        if (_isSubmitting.value) return
        val currentStateId = stateId
        if (currentStateId == null) {
            // Shouldn't happen, but guard anyway: go back to step 1.
            _step.value = Step.Email
            return
        }
        val codeValue = _code.value.trim()
        if (codeValue.length < 4) {
            _error.value = "Enter the code from your email"
            return
        }

        _isSubmitting.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val response = backendClient.emailAuthVerify(currentStateId, codeValue)
                authManager.setSession(
                    token = response.jwt,
                    userId = response.userId,
                    email = response.email
                )
                // The auth gate in MainActivity observes
                // AuthManager.currentUserId and will swap the screen
                // automatically, and the LaunchedEffect there kicks
                // off a background sync as soon as we're signed in.
            } catch (e: BackendException.Unauthorized) {
                // Either the code was wrong or the state expired.
                // Expired states need to restart; wrong codes keep
                // the user on step 2 with a retry.
                val msg = e.message ?: ""
                if (msg.contains("expired", ignoreCase = true)) {
                    _error.value = "Session expired — enter your email again"
                    stateId = null
                    _code.value = ""
                    _step.value = Step.Email
                } else {
                    _error.value = "That code is incorrect"
                    _code.value = ""
                }
            } catch (e: BackendException) {
                _error.value = humanMessageFor(e) ?: "Couldn't verify code"
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    /** "← Change email" link on step 2. */
    fun backToEmail() {
        stateId = null
        _code.value = ""
        _error.value = null
        _step.value = Step.Email
    }

    private fun humanMessageFor(e: BackendException): String? = when (e) {
        is BackendException.Network -> "Can't reach the backend — is the server running?"
        is BackendException.Server -> "Server error (${e.status}). Try again shortly."
        is BackendException.Http -> e.message
        else -> e.message
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(container.authManager, container.backendClient) as T
        }
    }
}
