package com.geo.ledger.domain

import com.geo.ledger.data.local.TransactionType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Round2PoliciesTest {
    @Test
    fun historicalChipShowsUntilClearedOrLiveNameReselected() {
        val renamed = OptionSnapshotPolicy.chips(
            active = listOf(7L to "实验耗材"),
            selectedId = 7L,
            historicalSnapshot = "耗材",
        )
        assertEquals(2, renamed.size)
        assertTrue(renamed[0].historical && renamed[0].selected)
        assertEquals("耗材", renamed[0].label)
        assertFalse(renamed[1].historical)
        assertFalse(renamed[1].selected)
        assertEquals("实验耗材", renamed[1].label)

        val inactive = OptionSnapshotPolicy.chips(
            active = emptyList(),
            selectedId = 3L,
            historicalSnapshot = "张三",
        )
        assertEquals(1, inactive.size)
        assertTrue(inactive.single().historical && inactive.single().selected)

        val cleared = OptionSnapshotPolicy.chips(
            active = listOf(7L to "实验耗材"),
            selectedId = null,
            historicalSnapshot = "耗材",
        )
        assertEquals(1, cleared.size)
        assertFalse(cleared.single().selected)
        assertFalse(cleared.single().historical)

        val reselected = OptionSnapshotPolicy.chips(
            active = listOf(7L to "实验耗材"),
            selectedId = 7L,
            historicalSnapshot = "实验耗材",
        )
        assertEquals(1, reselected.size)
        assertTrue(reselected.single().selected)
        assertFalse(reselected.single().historical)
    }

    @Test
    fun snapshotForSaveKeepsOldNameUntilExplicitReselect() {
        val option = NamedOption(7L, "实验耗材", isActive = true)
        assertEquals(
            "耗材",
            OptionSnapshotPolicy.snapshotForSave(
                selectedId = 7L,
                existingId = 7L,
                existingSnapshot = "耗材",
                selectionEdited = false,
                currentOption = option,
                missingMessage = "missing",
                inactiveMessage = "inactive",
            ),
        )
        assertEquals(
            "实验耗材",
            OptionSnapshotPolicy.snapshotForSave(
                selectedId = 7L,
                existingId = 7L,
                existingSnapshot = "耗材",
                selectionEdited = true,
                currentOption = option,
                missingMessage = "missing",
                inactiveMessage = "inactive",
            ),
        )
        assertNull(
            OptionSnapshotPolicy.snapshotForSave(
                selectedId = null,
                existingId = 7L,
                existingSnapshot = "耗材",
                selectionEdited = true,
                currentOption = option,
                missingMessage = "missing",
                inactiveMessage = "inactive",
            ),
        )
    }

    @Test
    fun saveIdempotencyPrefersExistingIdThenClientOpKey() {
        assertEquals(42L, SaveIdempotency.resolveExistingId(42L, "key", 99L))
        assertEquals(99L, SaveIdempotency.resolveExistingId(null, "key", 99L))
        assertEquals(99L, SaveIdempotency.resolveExistingId(-1L, "key", 99L))
        assertNull(SaveIdempotency.resolveExistingId(null, "key", null))
        assertNull(SaveIdempotency.resolveExistingId(null, null, 99L))
    }

    @Test
    fun editorNavigationGuardBlocksDoubleTapAndExistingEditor() {
        assertFalse(
            EditorNavigationGuard.shouldNavigate(
                nowMs = 100L,
                lastNavMs = 0L,
                lastTargetId = -1L,
                targetId = -1L,
                currentIsSameEditor = true,
                editorAlreadyOnBackStackForTarget = false,
            ),
        )
        assertFalse(
            EditorNavigationGuard.shouldNavigate(
                nowMs = 100L,
                lastNavMs = 0L,
                lastTargetId = 5L,
                targetId = 5L,
                currentIsSameEditor = false,
                editorAlreadyOnBackStackForTarget = true,
            ),
        )
        assertFalse(
            EditorNavigationGuard.shouldNavigate(
                nowMs = 500L,
                lastNavMs = 0L,
                lastTargetId = -1L,
                targetId = -1L,
                currentIsSameEditor = false,
                editorAlreadyOnBackStackForTarget = false,
            ),
        )
        assertTrue(
            EditorNavigationGuard.shouldNavigate(
                nowMs = 700L,
                lastNavMs = 0L,
                lastTargetId = -1L,
                targetId = -1L,
                currentIsSameEditor = false,
                editorAlreadyOnBackStackForTarget = false,
            ),
        )
    }

    @Test
    fun invertedCustomRangeIsRejectedNotSwapped() {
        val start = LocalDate.of(2026, 9, 3)
        val end = LocalDate.of(2026, 9, 1)
        assertNull(CustomRangeSelection.confirmRange(start, end))
        val ok = CustomRangeSelection.confirmRange(end, start)
        assertEquals(end, ok?.start)
        assertEquals(start, ok?.endInclusive)
    }

    @Test
    fun ledgerObserverSurfacesInvalidInsteadOfThrowing() {
        val bad = ledgerTx(1, "2026-09-01", 0L, TransactionType.EXPENSE)
        val observed = LedgerObserver.observe(listOf(bad))
        assertTrue(observed is LedgerObservation.Invalid)
        val corruptDate = ledgerTx(2, "2026-09-01", 100L, TransactionType.INCOME).copy(transactionDate = Long.MAX_VALUE)
        val invalidDate = LedgerObserver.observe(listOf(corruptDate))
        assertTrue(invalidDate is LedgerObservation.Invalid)
    }
}
