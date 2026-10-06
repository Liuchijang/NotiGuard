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

    @Test fun shellQuoteNeutralisesQuotes() {
        assertEquals("'a.b'", FcmList.shellQuote("a.b"))
        assertEquals("'x'\\''y'", FcmList.shellQuote("x'y"))
    }
}
