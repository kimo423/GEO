package com.geo.ledger.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

internal enum class GeoNavAxis {
    Forward,
    Backward,
}

/**
 * Short, clipped, directional slides for [androidx.navigation.compose.NavHost].
 * Pure [axis] decisions are independent of Compose so JVM tests can lock them.
 */
internal object GeoNavTransitions {
    const val DurationMs = 220

    /** Matches `View.EDGE_RIGHT`; left-edge predictive back is treated as Backward. */
    const val PredictiveEdgeRight = 1

    private val topLevelRoutes = listOf(
        GeoDestinations.Home,
        GeoDestinations.Bills,
        GeoDestinations.Settings,
    )

    private val slideSpec = tween<IntOffset>(
        durationMillis = DurationMs,
        easing = FastOutSlowInEasing,
    )

    fun isTopLevel(route: String?): Boolean = topLevelIndex(route) >= 0

    fun topLevelIndex(route: String?): Int {
        if (route == null) return -1
        return topLevelRoutes.indexOf(route)
    }

    fun axis(initialRoute: String?, targetRoute: String?, isPop: Boolean): GeoNavAxis {
        if (isPop) return GeoNavAxis.Backward
        val from = topLevelIndex(initialRoute)
        val to = topLevelIndex(targetRoute)
        if (from >= 0 && to >= 0) {
            return if (to >= from) GeoNavAxis.Forward else GeoNavAxis.Backward
        }
        return GeoNavAxis.Forward
    }

    fun axisForPredictivePop(swipeEdge: Int): GeoNavAxis =
        if (swipeEdge == PredictiveEdgeRight) GeoNavAxis.Forward else GeoNavAxis.Backward

    fun enterTransition(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideEnter(axis(initialState.destination.route, targetState.destination.route, isPop = false))
    }

    fun exitTransition(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideExit(axis(initialState.destination.route, targetState.destination.route, isPop = false))
    }

    fun popEnterTransition(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideEnter(axis(initialState.destination.route, targetState.destination.route, isPop = true))
    }

    fun popExitTransition(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideExit(axis(initialState.destination.route, targetState.destination.route, isPop = true))
    }

    fun sizeTransform(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> SizeTransform? = {
        SizeTransform(clip = true) { _, _ -> snap() }
    }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideEnter(
        axis: GeoNavAxis,
    ): EnterTransition = slideIntoContainer(towards(axis), slideSpec)

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideExit(
        axis: GeoNavAxis,
    ): ExitTransition = slideOutOfContainer(towards(axis), slideSpec)

    private fun towards(axis: GeoNavAxis) = when (axis) {
        GeoNavAxis.Forward -> AnimatedContentTransitionScope.SlideDirection.Start
        GeoNavAxis.Backward -> AnimatedContentTransitionScope.SlideDirection.End
    }
}
