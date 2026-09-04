package com.geo.ledger.domain

import com.geo.ledger.data.LedgerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionNameValidatorTest {
    @Test
    fun maxLengthMatchesRepositoryRule() {
        assertEquals(40, LedgerRepository.MAX_OPTION_NAME_LENGTH)
        assertEquals(LedgerRepository.MAX_OPTION_NAME_LENGTH, OptionNameValidator.DEFAULT_MAX_LENGTH)
    }

    @Test
    fun blankAndWhitespaceOnlyAreRejectedAfterTrim() {
        assertEquals(
            OptionNameValidator.Reason.Blank,
            (OptionNameValidator.validate("") as OptionNameValidator.Result.Invalid).reason,
        )
        assertEquals(
            OptionNameValidator.Reason.Blank,
            (OptionNameValidator.validate("   ") as OptionNameValidator.Result.Invalid).reason,
        )
        assertEquals(
            OptionNameValidator.Reason.Blank,
            (OptionNameValidator.validate("\t\n") as OptionNameValidator.Result.Invalid).reason,
        )
    }

    @Test
    fun trimsSurroundingWhitespaceWithoutChangingInnerText() {
        val result = OptionNameValidator.validate(" 张三 ")
        assertTrue(result is OptionNameValidator.Result.Valid)
        assertEquals("张三", (result as OptionNameValidator.Result.Valid).normalized)
    }

    @Test
    fun rejectsDuplicateActiveNamesAndAllowsRenameToSameName() {
        val active = listOf(1L to "张三", 2L to "实验室")
        val duplicate = OptionNameValidator.validate(" 张三 ", activeItems = active)
        assertEquals(
            OptionNameValidator.Reason.Duplicate,
            (duplicate as OptionNameValidator.Result.Invalid).reason,
        )

        val renameSame = OptionNameValidator.validate(" 张三 ", activeItems = active, excludeId = 1L)
        assertTrue(renameSame is OptionNameValidator.Result.Valid)
        assertEquals("张三", (renameSame as OptionNameValidator.Result.Valid).normalized)

        val renameTaken = OptionNameValidator.validate("实验室", activeItems = active, excludeId = 1L)
        assertEquals(
            OptionNameValidator.Reason.Duplicate,
            (renameTaken as OptionNameValidator.Result.Invalid).reason,
        )
    }

    @Test
    fun inactiveNamesAreNotTreatedAsDuplicatesSoAddCreatesANewIdentity() {
        val active = emptyList<Pair<Long, String>>()
        val result = OptionNameValidator.validate("张三", activeItems = active)
        assertTrue(result is OptionNameValidator.Result.Valid)
        assertEquals("张三", (result as OptionNameValidator.Result.Valid).normalized)
    }

    @Test
    fun enforcesMaxFortyCharactersInclusive() {
        val max = "测".repeat(LedgerRepository.MAX_OPTION_NAME_LENGTH)
        val over = "测".repeat(LedgerRepository.MAX_OPTION_NAME_LENGTH + 1)
        val valid = OptionNameValidator.validate(max)
        assertTrue(valid is OptionNameValidator.Result.Valid)
        assertEquals(max, (valid as OptionNameValidator.Result.Valid).normalized)
        assertEquals(
            OptionNameValidator.Reason.TooLong,
            (OptionNameValidator.validate(over) as OptionNameValidator.Result.Invalid).reason,
        )
    }

    @Test
    fun acceptsEmojiCjkAndMixedTextWithinLimit() {
        val mixed = "Lab 实验室 😀"
        val result = OptionNameValidator.validate("  $mixed  ")
        assertTrue(result is OptionNameValidator.Result.Valid)
        assertEquals(mixed, (result as OptionNameValidator.Result.Valid).normalized)

        val twentyEmoji = "😀".repeat(20)
        assertTrue(OptionNameValidator.validate(twentyEmoji) is OptionNameValidator.Result.Valid)
        assertEquals(
            OptionNameValidator.Reason.TooLong,
            (OptionNameValidator.validate("😀".repeat(21)) as OptionNameValidator.Result.Invalid).reason,
        )
    }
}
