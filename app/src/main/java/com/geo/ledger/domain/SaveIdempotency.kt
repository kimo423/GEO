package com.geo.ledger.domain

object SaveIdempotency {
    fun resolveExistingId(
        requestedId: Long?,
        clientOpKey: String?,
        idForClientOpKey: Long?,
    ): Long? {
        if (requestedId != null && requestedId > 0) return requestedId
        if (!clientOpKey.isNullOrBlank()) return idForClientOpKey
        return null
    }
}

object EditorNavigationGuard {
    const val DEBOUNCE_MS = 600L

    fun shouldNavigate(
        nowMs: Long,
        lastNavMs: Long,
        lastTargetId: Long,
        targetId: Long,
        currentIsSameEditor: Boolean,
        editorAlreadyOnBackStackForTarget: Boolean,
    ): Boolean {
        if (currentIsSameEditor) return false
        if (editorAlreadyOnBackStackForTarget) return false
        if (targetId == lastTargetId && nowMs - lastNavMs in 0 until DEBOUNCE_MS) return false
        return true
    }
}

object CustomRangeSelection {
    fun confirmRange(start: java.time.LocalDate, endInclusive: java.time.LocalDate): DateRange? =
        BillsQuery.customRange(start, endInclusive)
}
