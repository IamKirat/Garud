package com.garud.security

import com.garud.security.service.UrlReputationClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlReputationClientTest {
    @Test fun normalizesHttpUrlWithoutOpeningItOrKeepingFragment() {
        assertEquals("https://example.com/login?next=account", UrlReputationClient.normalize(" HTTPS://EXAMPLE.COM/a/../login?next=account#ignored "))
    }

    @Test fun rejectsUnsupportedSchemesAndEmbeddedCredentials() {
        assertNull(UrlReputationClient.normalize("javascript:alert(1)"))
        assertNull(UrlReputationClient.normalize("file:///etc/passwd"))
        assertNull(UrlReputationClient.normalize("https://user:pass@example.com/"))
    }

    @Test fun rejectsMissingHostAndOversizedInput() {
        assertNull(UrlReputationClient.normalize("https:///path"))
        assertNull(UrlReputationClient.normalize("https://example.com/" + "x".repeat(2048)))
    }
}
