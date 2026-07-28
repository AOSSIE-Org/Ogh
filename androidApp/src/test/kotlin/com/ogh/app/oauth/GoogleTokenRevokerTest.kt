package com.ogh.app.oauth

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GoogleTokenRevokerTest {

    private lateinit var server: MockWebServer
    private lateinit var revoker: GoogleTokenRevoker

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        revoker = GoogleTokenRevoker(revocationUrl = server.url("/revoke").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun revoke_postsTokenInFormBodyRatherThanUrl() {
        server.enqueue(MockResponse().setResponseCode(200))

        revoker.revoke("private-refresh-token")

        val request = server.takeRequest()
        assertEquals("/revoke", request.path)
        assertEquals("POST", request.method)
        assertEquals("token=private-refresh-token", request.body.readUtf8())
    }

    @Test
    fun revoke_rejectsUnsuccessfulResponseWithoutResponseBodyDisclosure() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("sensitive response"))

        val error = assertFailsWith<GoogleRevocationException> {
            revoker.revoke("private-refresh-token")
        }

        assertEquals(500, error.statusCode)
        assertEquals(false, error.message.orEmpty().contains("sensitive response"))
    }
}
