package com.remoteadb.app.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbManagerTest {

    @Test
    fun testPortParsingLogic() {
        val validPortStr = "5555"
        val parsed = validPortStr.trim().toIntOrNull()
        assertEquals(5555, parsed)

        val invalidPortStr = ""
        val parsedInvalid = invalidPortStr.trim().toIntOrNull() ?: -1
        assertEquals(-1, parsedInvalid)
    }

    @Test
    fun testRootStatusDataClass() {
        val status = RootStatus(
            isRooted = true,
            provider = RootProvider.MAGISK,
            versionInfo = "Magisk 27.0"
        )
        assertTrue(status.isRooted)
        assertEquals(RootProvider.MAGISK, status.provider)
        assertEquals("Magisk 27.0", status.versionInfo)
    }

    @Test
    fun testUnrootedStatus() {
        val status = RootStatus(
            isRooted = false,
            provider = RootProvider.NONE
        )
        assertFalse(status.isRooted)
        assertEquals(RootProvider.NONE, status.provider)
    }
}
