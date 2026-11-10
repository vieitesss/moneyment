package eu.vieitesss.moneyment.domain

import java.time.LocalDate

enum class Kind { EXPENSE, INCOME }

/**
 * A built-in Category or Account has a [builtin] key and a null [name] until the
 * person renames it: the app then shows its own translated label for the key.
 */
data class Category(
    val id: Long,
    val kind: Kind,
    val builtin: String?,
    val name: String?,
    val emoji: String,
    val color: Int,
    val archived: Boolean,
    val position: Int,
)

data class Account(
    val id: Long,
    val builtin: String?,
    val name: String?,
    val archived: Boolean,
)

data class Transaction(
    val id: Long,
    val kind: Kind,
    val amountCents: Long,
    val categoryId: Long,
    val accountId: Long,
    val date: LocalDate,
    val note: String,
)

/** Everything the app stores. */
data class Snapshot(
    val categories: List<Category>,
    val accounts: List<Account>,
    val transactions: List<Transaction>,
) {
    fun category(id: Long): Category? = categories.firstOrNull { it.id == id }
    fun account(id: Long): Account? = accounts.firstOrNull { it.id == id }

    companion object {
        val EMPTY = Snapshot(emptyList(), emptyList(), emptyList())
    }
}

val PALETTE: List<Int> = listOf(
    0xFF2E7D32, 0xFFEF6C00, 0xFF1565C0, 0xFF6D4C41, 0xFF546E7A, 0xFFC62828,
    0xFFAD1457, 0xFF6A1B9A, 0xFF00838F, 0xFF9E9D24, 0xFF4527A0, 0xFF00695C,
).map { it.toInt() }

const val BUILTIN_CASH = "cash"

/** Categories and the Account a fresh install starts with. Ids are assigned by storage. */
fun defaultCategories(): List<Category> {
    fun make(kind: Kind, vararg items: Pair<String, String>) =
        items.mapIndexed { i, (key, emoji) ->
            Category(0, kind, key, null, emoji, PALETTE[i % PALETTE.size], false, i)
        }
    return make(
        Kind.EXPENSE,
        "groceries" to "🛒", "eating_out" to "🍽️", "transport" to "🚌", "housing" to "🏠",
        "bills" to "🧾", "health" to "💊", "shopping" to "🛍️", "leisure" to "🎬",
        "travel" to "✈️", "other_expense" to "📦",
    ) + make(
        Kind.INCOME,
        "salary" to "💼", "gift" to "🎁", "refund" to "↩️", "other_income" to "📦",
    )
}

fun defaultAccounts(): List<Account> = listOf(Account(0, BUILTIN_CASH, null, false))

/**
 * The Account a new Draft preselects: [lastUsed] when it can still be chosen, else the
 * one of the newest Transaction, else the first Account that is not archived.
 */
fun defaultAccountId(accounts: List<Account>, transactions: List<Transaction>, lastUsed: Long?): Long {
    val active = accounts.filter { !it.archived }
    val newest = transactions.maxByOrNull { it.id }?.accountId
    return active.firstOrNull { it.id == lastUsed }?.id
        ?: active.firstOrNull { it.id == newest }?.id
        ?: active.first().id
}
