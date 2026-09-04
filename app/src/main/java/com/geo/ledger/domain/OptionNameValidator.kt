package com.geo.ledger.domain

object OptionNameValidator {
    const val DEFAULT_MAX_LENGTH = 40

    sealed interface Result {
        data class Valid(val normalized: String) : Result
        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason {
        Blank,
        TooLong,
        Duplicate,
    }

    fun validate(
        raw: String,
        activeItems: Iterable<Pair<Long, String>> = emptyList(),
        excludeId: Long? = null,
        maxLength: Int = DEFAULT_MAX_LENGTH,
    ): Result {
        val normalized = raw.trim()
        if (normalized.isEmpty()) return Result.Invalid(Reason.Blank)
        if (normalized.length > maxLength) return Result.Invalid(Reason.TooLong)
        val duplicate = activeItems.any { (id, name) ->
            name == normalized && (excludeId == null || id != excludeId)
        }
        if (duplicate) return Result.Invalid(Reason.Duplicate)
        return Result.Valid(normalized)
    }
}
