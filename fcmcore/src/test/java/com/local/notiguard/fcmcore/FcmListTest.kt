package com.local.notiguard.fcmcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FcmListTest {

    @Test fun noChangeWhenGmsPresent() {
        assertNull(FcmList.merged("com.tencent.mm, com.google.android.gms ,com.android.vending", null))
    }

    @Test fun appendsGmsKeepingOrder() {
        assertEquals(
            "com.tencent.mm,com.android.vending,com.google.android.gms",
            FcmList.merged("com.tencent.mm,com.android.vending", "x.y"),
        )
    }

    @Test fun emptyListRestoredFromLastGood() {
        assertEquals("a.b,c.d,com.google.android.gms", FcmList.merged("", "a.b,c.d"))
        assertEquals("a.b,com.google.android.gms", FcmList.merged("null", "a.b"))
        assertEquals("com.google.android.gms", FcmList.merged(null, null))
    }

    @Test fun prefixIsNotAMatch() {
        assertFalse(FcmList.hasGms("com.google.android.gms.persistent"))
        assertTrue(FcmList.hasGms("com.google.android.gms"))
    }

    @Test fun dropsDuplicatesAndEmptyEntries() {
        assertEquals("a.b,c.d,com.google.android.gms", FcmList.merged(" a.b,,a.b, c.d ", null))
    }

    @Test fun addAppendsOnceKeepingOrder() {
        assertEquals("a.b,com.google.android.gms,x.y", FcmList.withAdded("a.b,com.google.android.gms", null, "x.y"))
        assertNull(FcmList.withAdded("a.b,x.y", null, "x.y"))
    }

    @Test fun addToEmptiedListRebuildsFromLastGood() {
        assertEquals("a.b,c.d,x.y", FcmList.withAdded("", "a.b,c.d", "x.y"))
        assertEquals("a.b,c.d", FcmList.withAdded(null, "a.b,c.d", "c.d"))
    }

    @Test fun removeKeepsOthersAndNeverGms() {
        assertEquals("a.b,com.google.android.gms", FcmList.withRemoved("a.b,x.y,com.google.android.gms", "x.y"))
        assertNull(FcmList.withRemoved("a.b,com.google.android.gms", "com.google.android.gms"))
        assertNull(FcmList.withRemoved("a.b", "x.y"))
        assertNull(FcmList.withRemoved("a.b.c", "a.b"))
    }

    @Test fun shellQuoteNeutralisesQuotes() {
        assertEquals("'a.b'", FcmList.shellQuote("a.b"))
        assertEquals("'x'\\''y'", FcmList.shellQuote("x'y"))
    }
}
