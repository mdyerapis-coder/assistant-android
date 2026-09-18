package com.mdyerapis.sable.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthCompleteLinksTest {
    @Test
    fun primarySchemeMatchesBackendDeeplink() {
        assertEquals("assistantapp", OAuthCompleteLinks.SCHEME)
        assertEquals("oauth-complete", OAuthCompleteLinks.HOST)
        assertEquals("assistantapp://oauth-complete", OAuthCompleteLinks.URI)
    }

    @Test
    fun acceptsBackendAndLegacySchemes() {
        assertTrue(OAuthCompleteLinks.matches("assistantapp", "oauth-complete"))
        assertTrue(OAuthCompleteLinks.matches("sableapp", "oauth-complete"))
        assertTrue(OAuthCompleteLinks.matches("ASSISTANTAPP", "OAuth-Complete"))
    }

    @Test
    fun rejectsOtherHostsAndSchemes() {
        assertFalse(OAuthCompleteLinks.matches("assistantapp", "session"))
        assertFalse(OAuthCompleteLinks.matches("https", "oauth-complete"))
        assertFalse(OAuthCompleteLinks.matches(null, "oauth-complete"))
        assertFalse(OAuthCompleteLinks.matches("assistantapp", null))
    }
}
