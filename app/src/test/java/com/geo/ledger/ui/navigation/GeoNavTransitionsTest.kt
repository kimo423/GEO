package com.geo.ledger.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoNavTransitionsTest {
    @Test
    fun topLevelIndicesMatchBottomBarOrder() {
        assertEquals(0, GeoNavTransitions.topLevelIndex(GeoDestinations.Home))
        assertEquals(1, GeoNavTransitions.topLevelIndex(GeoDestinations.Bills))
        assertEquals(2, GeoNavTransitions.topLevelIndex(GeoDestinations.Settings))
        assertTrue(GeoNavTransitions.isTopLevel(GeoDestinations.Home))
        assertFalse(GeoNavTransitions.isTopLevel(GeoDestinations.Detail))
        assertFalse(GeoNavTransitions.isTopLevel(GeoDestinations.Editor))
        assertFalse(GeoNavTransitions.isTopLevel("editor?transactionId=-1"))
        assertFalse(GeoNavTransitions.isTopLevel(null))
        assertEquals(-1, GeoNavTransitions.topLevelIndex(null))
    }

    @Test
    fun topLevelForwardUsesStartAxis() {
        assertEquals(GeoNavAxis.Forward, axis(GeoDestinations.Home, GeoDestinations.Bills))
        assertEquals(GeoNavAxis.Forward, axis(GeoDestinations.Bills, GeoDestinations.Settings))
        assertEquals(GeoNavAxis.Forward, axis(GeoDestinations.Home, GeoDestinations.Settings))
    }

    @Test
    fun topLevelBackwardUsesEndAxisEvenWhenNavigateNotPop() {
        assertEquals(GeoNavAxis.Backward, axis(GeoDestinations.Settings, GeoDestinations.Bills))
        assertEquals(GeoNavAxis.Backward, axis(GeoDestinations.Bills, GeoDestinations.Home))
        assertEquals(GeoNavAxis.Backward, axis(GeoDestinations.Settings, GeoDestinations.Home))
    }

    @Test
    fun hierarchicalPushIsForward() {
        assertEquals(GeoNavAxis.Forward, axis(GeoDestinations.Home, GeoDestinations.Detail))
        assertEquals(GeoNavAxis.Forward, axis(GeoDestinations.Bills, GeoDestinations.Detail))
        assertEquals(GeoNavAxis.Forward, axis(GeoDestinations.Settings, GeoDestinations.Persons))
        assertEquals(GeoNavAxis.Forward, axis(GeoDestinations.Settings, GeoDestinations.Categories))
        assertEquals(GeoNavAxis.Forward, axis(GeoDestinations.Home, GeoDestinations.Editor))
        assertEquals(GeoNavAxis.Forward, axis(GeoDestinations.Detail, GeoDestinations.Editor))
        assertEquals(GeoNavAxis.Forward, axis(null, GeoDestinations.Home))
    }

    @Test
    fun popIsAlwaysBackward() {
        assertEquals(GeoNavAxis.Backward, axis(GeoDestinations.Detail, GeoDestinations.Home, isPop = true))
        assertEquals(GeoNavAxis.Backward, axis(GeoDestinations.Editor, GeoDestinations.Home, isPop = true))
        assertEquals(GeoNavAxis.Backward, axis(GeoDestinations.Editor, GeoDestinations.Detail, isPop = true))
        assertEquals(GeoNavAxis.Backward, axis(GeoDestinations.Persons, GeoDestinations.Settings, isPop = true))
        assertEquals(GeoNavAxis.Backward, axis(GeoDestinations.Bills, GeoDestinations.Home, isPop = true))
        assertEquals(GeoNavAxis.Backward, axis(GeoDestinations.Home, GeoDestinations.Settings, isPop = true))
    }

    @Test
    fun predictivePopUsesSwipeEdge() {
        assertEquals(GeoNavAxis.Backward, GeoNavTransitions.axisForPredictivePop(0))
        assertEquals(GeoNavAxis.Forward, GeoNavTransitions.axisForPredictivePop(GeoNavTransitions.PredictiveEdgeRight))
        assertEquals(GeoNavAxis.Backward, GeoNavTransitions.axisForPredictivePop(-1))
    }

    @Test
    fun durationIsShortAndPredictable() {
        assertEquals(220, GeoNavTransitions.DurationMs)
    }

    private fun axis(from: String?, to: String?, isPop: Boolean = false): GeoNavAxis =
        GeoNavTransitions.axis(from, to, isPop)
}
