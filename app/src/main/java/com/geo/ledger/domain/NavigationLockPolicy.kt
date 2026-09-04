package com.geo.ledger.domain

/**
 * User back / system back is blocked while a save or delete is in flight.
 * Successful completion must still pop, even if that lock is held.
 */
object NavigationLockPolicy {
    fun allowUserBack(locked: Boolean): Boolean = !locked

    fun allowCompletionBack(alreadyConsumed: Boolean): Boolean = !alreadyConsumed
}
