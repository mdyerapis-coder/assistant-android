package com.mdyerapis.sable.automations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAutomationWorkTest {
    @Test
    fun uniqueNameIsStablePerId() {
        assertEquals("local-automation-abc", LocalAutomationWork.uniqueName("abc"))
        assertEquals(
            LocalAutomationWork.uniqueName("abc"),
            LocalAutomationWork.uniqueName("abc"),
        )
        assertTrue(LocalAutomationWork.uniqueName("a") != LocalAutomationWork.uniqueName("b"))
    }

    @Test
    fun scheduleAndCancelShareUniqueName() {
        val id = "auto-1"
        assertEquals("local-automation-$id", LocalAutomationWork.uniqueName(id))
        assertEquals("automation_id", LocalAutomationWork.KEY_ID)
        assertEquals("com.mdyerapis.sable.automations.ACTION_DUE", LocalAutomationWork.ACTION_DUE)
    }
}
