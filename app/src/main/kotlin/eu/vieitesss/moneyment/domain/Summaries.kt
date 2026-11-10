package eu.vieitesss.moneyment.domain

import java.time.LocalDate
import java.time.YearMonth

data class Totals(val income: Long, val expenses: Long) {
    val difference: Long get() = income - expenses
}

data class CategoryShare(val categoryId: Long, val cents: Long, val fraction: Float)

data class MonthBar(val month: YearMonth, val income: Long, val expenses: Long)

/** Transactions of one month, newest first; within a day, the last logged comes first. */
fun monthTransactions(
    all: List<Transaction>,
    month: YearMonth,
    categoryId: Long? = null,
    accountId: Long? = null,
): List<Transaction> = all
    .filter {
        YearMonth.from(it.date) == month &&
            (categoryId == null || it.categoryId == categoryId) &&
            (accountId == null || it.accountId == accountId)
    }
    .sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.id })

fun totals(transactions: List<Transaction>): Totals = Totals(
    income = transactions.filter { it.kind == Kind.INCOME }.sumOf { it.amountCents },
    expenses = transactions.filter { it.kind == Kind.EXPENSE }.sumOf { it.amountCents },
)

/** Keeps the order of [transactions]; expects them already sorted by date. */
fun groupByDay(transactions: List<Transaction>): List<Pair<LocalDate, List<Transaction>>> =
    transactions.groupBy { it.date }.toList()

/** Expenses of one month per Category, largest first. */
fun expensesByCategory(all: List<Transaction>, month: YearMonth): List<CategoryShare> {
    val expenses = all.filter { it.kind == Kind.EXPENSE && YearMonth.from(it.date) == month }
    val total = expenses.sumOf { it.amountCents }
    if (total == 0L) return emptyList()
    return expenses.groupBy { it.categoryId }
        .map { (id, list) ->
            val cents = list.sumOf { it.amountCents }
            CategoryShare(id, cents, cents.toFloat() / total)
        }
        .sortedWith(compareByDescending<CategoryShare> { it.cents }.thenBy { it.categoryId })
}

/** Twelve months ending at [end], oldest first, including months without Transactions. */
fun lastTwelveMonths(all: List<Transaction>, end: YearMonth): List<MonthBar> {
    val byMonth = all.groupBy { YearMonth.from(it.date) }
    return (11L downTo 0L).map { back ->
        val month = end.minusMonths(back)
        val t = totals(byMonth[month].orEmpty())
        MonthBar(month, t.income, t.expenses)
    }
}
