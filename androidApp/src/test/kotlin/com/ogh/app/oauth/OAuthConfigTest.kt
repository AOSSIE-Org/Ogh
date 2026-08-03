package org.aossie.ogh.oauth

import com.ogh.shared.domain.StreamingProvider
import org.junit.Test
import kotlin.test.assertEquals

class OAuthConfigTest {

    @Test
    fun youtube_requestsOnlyReviewedScope() {
        assertEquals(
            listOf("https://www.googleapis.com/auth/youtube"),
            OAuthConfig.getScopes(StreamingProvider.YOUTUBE),
        )
    }

    @Test
    fun youtube_redirectMatchesDocumentedAndroidCustomSchemeForm() {
        assertEquals("org.aossie.ogh:/oauth2callback", OAuthConfig.REDIRECT_URI)
    }
}
