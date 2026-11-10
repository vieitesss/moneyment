package eu.vieitesss.moneyment.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DomainTest {
    private val food = Category(1, Kind.EXPENSE, "groceries", null, "🛒", 1, false, 0)
    private val fun_ = Category(2, Kind.EXPENSE, null, "Fun, \"games\"", "🎲", 2, true, 1)
    private val salary = Category(3, Kind.INCOME, "salary", null, "💼", 3, false, 0)
    private val cash = Account(1, "cash", null, false)
    private val card = Account(2, null, "Visa", false)

    private fun tx(id: Long, kind: Kind, cents: Long, category: Long, date: String, account: Long = 1, note: String = "") =
        Transaction(id, kind, cents, category, account, LocalDate.parse(date), note)

    private val all = listOf(
        tx(1, Kind.EXPENSE, 1000, 1, "2026-11-03"),
        tx(2, Kind.EXPENSE, 250, 2, "2026-11-03", account = 2, note = "line1\nline2"),
        tx(3, Kind.INCOME, 150000, 3, "2026-11-01"),
        tx(4, Kind.EXPENSE, 750, 1, "2026-11-30"),
        tx(5, Kind.EXPENSE, 9999, 1, "2026-10-31"),
        tx(6, Kind.EXPENSE, 4000, 1, "2025-12-01"),
        tx(7, Kind.EXPENSE, 1, 1, "2025-11-30"),
    )
    private val snapshot = Snapshot(listOf(food, fun_, salary), listOf(cash, card), all)
    private val november = YearMonth.of(2026, 11)

    @Test
    fun monthListIsNewestFirstAndLastLoggedFirstWithinADay() {
        assertEquals(listOf(4L, 2L, 1L, 3L), monthTransactions(all, november).map { it.id })
    }

    @Test
    fun monthListFiltersByCategoryAndAccount() {
        assertEquals(listOf(4L, 1L), monthTransactions(all, november, categoryId = 1).map { it.id })
        assertEquals(listOf(2L), monthTransactions(all, november, accountId = 2).map { it.id })
        assertTrue(monthTransactions(all, november, categoryId = 1, accountId = 2).isEmpty())
    }

    @Test
    fun totalsSeparateIncomeFromExpenses() {
        val t = totals(monthTransactions(all, november))
        assertEquals(150000L, t.income)
        assertEquals(2000L, t.expenses)
        assertEquals(148000L, t.difference)
    }

    @Test
    fun groupByDayKeepsOrder() {
        val days = groupByDay(monthTransactions(all, november))
        assertEquals(listOf("2026-11-30", "2026-11-03", "2026-11-01"), days.map { it.first.toString() })
        assertEquals(listOf(2L, 1L), days[1].second.map { it.id })
    }

    @Test
    fun expensesByCategoryRanksAndIgnoresIncome() {
        val shares = expensesByCategory(all, november)
        assertEquals(listOf(1L, 2L), shares.map { it.categoryId })
        assertEquals(listOf(1750L, 250L), shares.map { it.cents })
        assertEquals(0.875f, shares[0].fraction, 0.0001f)
        assertTrue(expensesByCategory(all, YearMonth.of(2020, 1)).isEmpty())
    }

    @Test
    fun lastTwelveMonthsCoversExactlyTwelveIncludingEmptyOnes() {
        val bars = lastTwelveMonths(all, november)
        assertEquals(12, bars.size)
        assertEquals(YearMonth.of(2025, 12), bars.first().month)
        assertEquals(4000L, bars.first().expenses)
        assertEquals(MonthBar(YearMonth.of(2026, 1), 0, 0), bars[1])
        assertEquals(MonthBar(YearMonth.of(2026, 10), 0, 9999), bars[10])
        assertEquals(MonthBar(november, 150000, 2000), bars.last())
    }

    @Test
    fun parseAmountAcceptsCommaAndDot() {
        assertEquals(2350L, parseAmount("23,5"))
        assertEquals(2350L, parseAmount("23.50"))
        assertEquals(2300L, parseAmount("23"))
        assertEquals(2300L, parseAmount("23,"))
        assertEquals(5L, parseAmount(",05"))
        assertNull(parseAmount(""))
        assertNull(parseAmount("0,00"))
        assertNull(parseAmount("1,234"))
        assertNull(parseAmount("-5"))
        assertNull(parseAmount("1.2.3"))
    }

    @Test
    fun centsToTextPadsCents() {
        assertEquals("23.05", centsToText(2305))
        assertEquals("0,50", centsToText(50, ','))
    }

    @Test
    fun defaultAccountIsTheLastUsedOneWhenStillActive() {
        assertEquals(2L, defaultAccountId(listOf(cash, card), all, lastUsed = 2))
    }

    @Test
    fun defaultAccountFallsBackToTheNewestTransactionThenTheFirstActive() {
        assertEquals(1L, defaultAccountId(listOf(cash, card), all, lastUsed = null))
        assertEquals(1L, defaultAccountId(listOf(cash, card), all, lastUsed = 9))
        val archivedCard = card.copy(archived = true)
        assertEquals(1L, defaultAccountId(listOf(cash, archivedCard), all, lastUsed = 2))
        assertEquals(3L, defaultAccountId(listOf(archivedCard, Account(3, null, "Bank", false)), emptyList(), null))
    }

    @Test
    fun backupRoundTripRestoresEverything() {
        assertEquals(snapshot, decodeBackup(encodeBackup(snapshot)))
    }

    @Test
    fun backupRejectsGarbageAndBrokenReferences() {
        assertThrows(IllegalArgumentException::class.java) { decodeBackup("hello") }
        assertThrows(IllegalArgumentException::class.java) { decodeBackup("{}") }
        val orphan = snapshot.copy(transactions = listOf(tx(1, Kind.EXPENSE, 100, 99, "2026-01-01")))
        assertThrows(IllegalArgumentException::class.java) { decodeBackup(encodeBackup(orphan)) }
        val wrongKind = snapshot.copy(transactions = listOf(tx(1, Kind.INCOME, 100, 1, "2026-01-01")))
        assertThrows(IllegalArgumentException::class.java) { decodeBackup(encodeBackup(wrongKind)) }
        val noAccount = snapshot.copy(accounts = emptyList(), transactions = emptyList())
        assertThrows(IllegalArgumentException::class.java) { decodeBackup(encodeBackup(noAccount)) }
    }

    @Test
    fun backupRejectsNonPositiveIds() {
        val zeroCategory = snapshot.copy(categories = listOf(food.copy(id = 0), fun_, salary))
        assertThrows(IllegalArgumentException::class.java) { decodeBackup(encodeBackup(zeroCategory)) }
        val zeroAccount = snapshot.copy(accounts = listOf(cash.copy(id = 0), card))
        assertThrows(IllegalArgumentException::class.java) { decodeBackup(encodeBackup(zeroAccount)) }
        val zeroTransaction = snapshot.copy(transactions = listOf(tx(0, Kind.EXPENSE, 1000, 1, "2026-11-03")))
        assertThrows(IllegalArgumentException::class.java) { decodeBackup(encodeBackup(zeroTransaction)) }
        val negative = snapshot.copy(accounts = listOf(cash.copy(id = -2), card))
        assertThrows(IllegalArgumentException::class.java) { decodeBackup(encodeBackup(negative)) }
    }

    @Test
    fun csvIsOldestFirstAndQuotesSpecialCharacters() {
        val csv = encodeCsv(snapshot, { it.name ?: it.builtin!! }, { it.name ?: it.builtin!! })
        val lines = csv.split("\r\n")
        assertEquals("date,kind,amount,category,account,note", lines[0])
        assertEquals("2025-11-30,expense,0.01,groceries,cash,", lines[1])
        assertTrue(csv.contains("2026-11-03,expense,2.50,\"Fun, \"\"games\"\"\",Visa,\"line1\nline2\"\r\n"))
        assertTrue(csv.endsWith("2026-11-30,expense,7.50,groceries,cash,\r\n"))
    }
}
