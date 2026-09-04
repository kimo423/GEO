package com.geo.ledger.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geo.ledger.data.local.ExpenseCategoryEntity
import com.geo.ledger.data.local.GeoDatabase
import com.geo.ledger.data.local.PersonOptionEntity
import com.geo.ledger.data.local.TransactionType
import com.geo.ledger.domain.LedgerCalculator
import com.geo.ledger.domain.TransactionDraft
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerRepositoryInstrumentedTest {
    private lateinit var database: GeoDatabase
    private lateinit var repository: LedgerRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GeoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LedgerRepository(database) { 1_000L }
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun personAndCategorySnapshotsSurviveRenameAndSoftDelete() = runTest {
        val personId = repository.addPerson(" 张三 ")
        val categoryId = repository.addCategory("耗材")
        val transactionId = repository.saveTransaction(
            id = null,
            draft = TransactionDraft(
                type = TransactionType.EXPENSE,
                amountCents = 12_800,
                date = LocalDate.of(2026, 9, 3),
                expensePersonId = personId,
                expenseCategoryId = categoryId,
                note = "打印纸",
            ),
        )

        repository.renamePerson(personId, "张三（新）")
        repository.renameCategory(categoryId, "实验耗材")
        repository.deactivatePerson(personId)
        repository.deactivateCategory(categoryId)

        assertEquals(emptyList<Any>(), repository.activePersons.first())
        assertEquals(emptyList<Any>(), repository.activeCategories.first())
        val stored = repository.getTransaction(transactionId)!!
        assertEquals("张三", stored.expensePersonSnapshot)
        assertEquals("耗材", stored.expenseCategorySnapshot)
    }

    @Test
    fun incomeNeverPersistsExpenseFieldsAndExpenseNeverPersistsSource() = runTest {
        val personId = repository.addPerson("实验室")
        val expenseId = repository.saveTransaction(
            null,
            TransactionDraft(
                type = TransactionType.EXPENSE,
                amountCents = 100,
                date = LocalDate.of(2026, 9, 3),
                expensePersonId = personId,
                incomeSource = "must be ignored",
            ),
        )
        val incomeId = repository.saveTransaction(
            null,
            TransactionDraft(
                type = TransactionType.INCOME,
                amountCents = 200,
                date = LocalDate.of(2026, 9, 3),
                expensePersonId = personId,
                incomeSource = "项目款",
            ),
        )

        assertNull(repository.getTransaction(expenseId)!!.incomeSource)
        val income = repository.getTransaction(incomeId)!!
        assertNull(income.expensePersonId)
        assertNull(income.expensePersonSnapshot)
        assertEquals("项目款", income.incomeSource)
    }

    @Test
    fun fileDatabasePersistsAcrossCloseAndReopen() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "geo-persistence-instrumented-test.db"
        context.deleteDatabase(name)
        val first = Room.databaseBuilder(context, GeoDatabase::class.java, name).build()
        val transactionId = LedgerRepository(first).saveTransaction(
            null,
            TransactionDraft(
                type = TransactionType.INCOME,
                amountCents = 500_000,
                date = LocalDate.of(2026, 1, 1),
                incomeSource = "持久化测试",
            ),
        )
        first.close()

        val reopened = Room.databaseBuilder(context, GeoDatabase::class.java, name).build()
        try {
            val stored = LedgerRepository(reopened).getTransaction(transactionId)
            assertEquals(500_000L, stored?.amountCents)
            assertEquals("持久化测试", stored?.incomeSource)
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun deactivateThenReAddUsesNewIdAndAllowsRepeatedCycles() = runTest {
        val firstPerson = repository.addPerson("张三")
        val firstCategory = repository.addCategory("耗材")
        repository.deactivatePerson(firstPerson)
        repository.deactivateCategory(firstCategory)

        val secondPerson = repository.addPerson("张三")
        val secondCategory = repository.addCategory("耗材")
        assertNotEquals(firstPerson, secondPerson)
        assertNotEquals(firstCategory, secondCategory)
        assertEquals("张三", repository.activePersons.first().single().name)
        assertEquals(secondPerson, repository.activePersons.first().single().id)
        assertEquals("耗材", repository.activeCategories.first().single().name)

        repository.deactivatePerson(secondPerson)
        repository.deactivateCategory(secondCategory)
        val thirdPerson = repository.addPerson("张三")
        val thirdCategory = repository.addCategory("耗材")
        assertNotEquals(secondPerson, thirdPerson)
        assertNotEquals(firstPerson, thirdPerson)
        assertNotEquals(secondCategory, thirdCategory)
        assertEquals(listOf(thirdPerson), repository.activePersons.first().map { it.id })
        assertEquals(listOf(thirdCategory), repository.activeCategories.first().map { it.id })
    }

    @Test
    fun uniqueActiveNameIndexAllowsInactiveDuplicatesAndRejectsSecondActive() = runTest {
        val personDao = database.personOptionDao()
        val categoryDao = database.expenseCategoryDao()
        personDao.insert(
            PersonOptionEntity(
                name = "张三",
                isActive = false,
                activeNameKey = null,
                sortOrder = 0,
                createdAtMillis = 1,
                updatedAtMillis = 1,
            ),
        )
        personDao.insert(
            PersonOptionEntity(
                name = "张三",
                isActive = false,
                activeNameKey = null,
                sortOrder = 1,
                createdAtMillis = 2,
                updatedAtMillis = 2,
            ),
        )
        personDao.insert(
            PersonOptionEntity(
                name = "张三",
                isActive = true,
                activeNameKey = "张三",
                sortOrder = 2,
                createdAtMillis = 3,
                updatedAtMillis = 3,
            ),
        )
        try {
            personDao.insert(
                PersonOptionEntity(
                    name = "张三",
                    isActive = true,
                    activeNameKey = "张三",
                    sortOrder = 3,
                    createdAtMillis = 4,
                    updatedAtMillis = 4,
                ),
            )
            fail("expected unique constraint on person active_name_key")
        } catch (_: SQLiteConstraintException) {
        }

        categoryDao.insert(
            ExpenseCategoryEntity(
                name = "耗材",
                isActive = true,
                activeNameKey = "耗材",
                sortOrder = 0,
                createdAtMillis = 1,
                updatedAtMillis = 1,
            ),
        )
        try {
            categoryDao.insert(
                ExpenseCategoryEntity(
                    name = "耗材",
                    isActive = true,
                    activeNameKey = "耗材",
                    sortOrder = 1,
                    createdAtMillis = 2,
                    updatedAtMillis = 2,
                ),
            )
            fail("expected unique constraint on category active_name_key")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test
    fun fileDatabaseSeedsFiveDefaultCategoriesAndZeroPersons() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "geo-default-seed-instrumented-test.db"
        context.deleteDatabase(name)
        val seeded = GeoDatabase.openFileDatabase(context, name)
        try {
            val repo = LedgerRepository(seeded)
            assertEquals(emptyList<Any>(), repo.activePersons.first())
            assertEquals(
                listOf("设备", "耗材", "交通", "餐饮", "其他"),
                repo.activeCategories.first().map { it.name },
            )
            seeded.close()
            val reopened = GeoDatabase.openFileDatabase(context, name)
            try {
                assertEquals(
                    listOf("设备", "耗材", "交通", "餐饮", "其他"),
                    LedgerRepository(reopened).activeCategories.first().map { it.name },
                )
                assertEquals(emptyList<Any>(), LedgerRepository(reopened).activePersons.first())
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun clientOpKeyReplayIsIdempotent() = runTest {
        val draft = TransactionDraft(
            type = TransactionType.INCOME,
            amountCents = 1_000,
            date = LocalDate.of(2026, 9, 4),
            incomeSource = "idempotent",
        )
        val first = repository.saveTransaction(id = null, draft = draft, clientOpKey = "op-1")
        val second = repository.saveTransaction(id = null, draft = draft, clientOpKey = "op-1")
        assertEquals(first, second)
        assertEquals(1, repository.ledgerEntries.first().size)
        assertEquals(first, repository.getTransaction(first)!!.id)
        assertEquals("op-1", repository.getTransaction(first)!!.clientOpKey)
    }

    @Test
    fun overflowingEditDoesNotMutateExistingRow() = runTest {
        val maxMinusOne = Long.MAX_VALUE - 1
        val first = repository.saveTransaction(
            null,
            TransactionDraft(TransactionType.INCOME, maxMinusOne, LocalDate.of(2026, 9, 1)),
        )
        val second = repository.saveTransaction(
            null,
            TransactionDraft(TransactionType.EXPENSE, maxMinusOne, LocalDate.of(2026, 9, 2)),
        )
        try {
            repository.saveTransaction(
                second,
                TransactionDraft(TransactionType.INCOME, maxMinusOne, LocalDate.of(2026, 9, 2)),
            )
            fail("expected ArithmeticException for overflowing edit")
        } catch (_: ArithmeticException) {
        }
        val stored = repository.getTransaction(second)!!
        assertEquals(TransactionType.EXPENSE, stored.type)
        assertEquals(maxMinusOne, stored.amountCents)
        assertEquals(2, repository.ledgerEntries.first().size)
        assertEquals(first, repository.getTransaction(first)!!.id)
    }

    @Test
    fun deleteRemovesOnlyTargetRow() = runTest {
        val first = repository.saveTransaction(
            null,
            TransactionDraft(TransactionType.INCOME, 100, LocalDate.of(2026, 9, 1), incomeSource = "a"),
        )
        val second = repository.saveTransaction(
            null,
            TransactionDraft(TransactionType.EXPENSE, 40, LocalDate.of(2026, 9, 2)),
        )
        val third = repository.saveTransaction(
            null,
            TransactionDraft(TransactionType.INCOME, 10, LocalDate.of(2026, 9, 3), incomeSource = "b"),
        )
        repository.deleteTransaction(second)
        assertNull(repository.getTransaction(second))
        assertEquals(listOf(first, third), repository.ledgerEntries.first().map { it.transaction.id })
        assertEquals(110L, repository.ledgerEntries.first().last().balanceAfterCents)
    }

    @Test
    fun saveRejectsHistoryWhoseIncomeTotalsOverflowLongEvenWhenRunningBalanceFits() = runTest {
        val maxMinusOne = Long.MAX_VALUE - 1
        val first = repository.saveTransaction(
            null,
            TransactionDraft(TransactionType.INCOME, maxMinusOne, LocalDate.of(2026, 9, 1)),
        )
        val second = repository.saveTransaction(
            null,
            TransactionDraft(TransactionType.EXPENSE, maxMinusOne, LocalDate.of(2026, 9, 2)),
        )
        assertTrue(first > 0 && second > 0)
        try {
            repository.saveTransaction(
                null,
                TransactionDraft(TransactionType.INCOME, maxMinusOne, LocalDate.of(2026, 9, 3)),
            )
            fail("expected ArithmeticException for overflowing income total")
        } catch (_: ArithmeticException) {
        }
        assertEquals(2, repository.ledgerEntries.first().size)
        val dateMoved = TransactionDraft(TransactionType.INCOME, maxMinusOne, LocalDate.of(2026, 9, 3))
        repository.saveTransaction(first, dateMoved)
        assertEquals(LocalDate.of(2026, 9, 3).toEpochDay(), repository.getTransaction(first)!!.transactionDate)
        try {
            LedgerCalculator.validateCandidateHistory(
                listOf(
                    repository.getTransaction(first)!!,
                    repository.getTransaction(second)!!,
                    com.geo.ledger.data.local.TransactionEntity(
                        id = Long.MAX_VALUE,
                        type = TransactionType.INCOME,
                        amountCents = maxMinusOne,
                        transactionDate = LocalDate.of(2026, 9, 4).toEpochDay(),
                        createdAtMillis = 9,
                        updatedAtMillis = 9,
                    ),
                ),
            )
            fail("expected ArithmeticException for candidate third MAX-1 income")
        } catch (_: ArithmeticException) {
        }
    }
}
