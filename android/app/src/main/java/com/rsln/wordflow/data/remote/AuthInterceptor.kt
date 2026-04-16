package com.rsln.wordflow.data.remote

import com.rsln.wordflow.data.auth.AuthManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that attaches the stored WordFlow app JWT as a
 * Bearer token on every outgoing request. If no token is stored,
 * the request goes out without the Authorization header and the
 * server returns 401 — which the BackendClient surfaces as a
 * typed Unauthorized exception, and the UI shows "please sign in".
 *
 * Safe to install on the BackendClient's OkHttpClient
 * unconditionally; doesn't do anything on requests where the token
 * isn't relevant.
 */
class AuthInterceptor(private val authManager: AuthManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authManager.currentToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
