package com.geo.ledger.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLockPolicyTest {
    @Test
    fun userBackIsBlockedOnlyWhileLocked() {
        assertTrue(NavigationLockPolicy.allowUserBack(locked = false))
        assertFalse(NavigationLockPolicy.allowUserBack(locked = true))
    }

    @Test
    fun completionBackIgnoresUserLockAndIsIdempotent() {
        assertTrue(NavigationLockPolicy.allowCompletionBack(alreadyConsumed = false))
        assertFalse(NavigationLockPolicy.allowCompletionBack(alreadyConsumed = true))
    }
}
