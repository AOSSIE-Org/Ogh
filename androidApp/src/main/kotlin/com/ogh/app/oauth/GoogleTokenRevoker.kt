package com.ogh.app.oauth

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** Revokes a Google OAuth grant without exposing the token in URLs or logs. */
class GoogleTokenRevoker(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val revocationUrl: String = GOOGLE_REVOCATION_URL,
) {
    fun revoke(token: String) {
        require(token.isNotBlank()) { "Cannot revoke a blank token" }

        val request = Request.Builder()
            .url(revocationUrl)
            .post(FormBody.Builder().add("token", token).build())
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GoogleRevocationException(response.code)
            }
        }
    }

    private companion object {
        const val GOOGLE_REVOCATION_URL = "https://oauth2.googleapis.com/revoke"
    }
}

class GoogleRevocationException(val statusCode: Int) :
    Exception("Google authorization could not be revoked (HTTP $statusCode)")
