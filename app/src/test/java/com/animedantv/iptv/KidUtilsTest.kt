package com.animedantv.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KidUtilsTest {

    @Test
    fun strippedLeadingZeroIsRepaired() {
        assertEquals(
            "912760c409eb5aff3e060422c502f410",
            KidUtils.normalizeKid("912760c4-9eb-5aff-3e06-0422c502f410"),
        )
    }

    @Test
    fun normalUuidIsFlattened() {
        assertEquals(
            "912760c409eb5aff3e060422c502f410",
            KidUtils.normalizeKid("912760c4-09eb-5aff-3e06-0422c502f410"),
        )
    }

    @Test
    fun shortHexIsLeftPaddedToThirtyTwo() {
        assertEquals(
            "0034567890abcdef0123456789abcdef",
            KidUtils.normalizeKid("34567890abcdef0123456789abcdef"),
        )
    }

    @Test
    fun nonHexReturnsNull() {
        assertNull(KidUtils.normalizeKid("zz-not-hex"))
        assertNull(KidUtils.normalizeKid(""))
        assertNull(KidUtils.normalizeKid(null))
    }
}
