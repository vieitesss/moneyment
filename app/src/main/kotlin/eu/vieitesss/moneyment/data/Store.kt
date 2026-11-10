package eu.vieitesss.moneyment.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import eu.vieitesss.moneyment.domain.Account
import eu.vieitesss.moneyment.domain.Category
import eu.vieitesss.moneyment.domain.Kind
import eu.vieitesss.moneyment.domain.Snapshot
import eu.vieitesss.moneyment.domain.Transaction
import eu.vieitesss.moneyment.domain.defaultAccounts
import eu.vieitesss.moneyment.domain.defaultCategories
import java.time.LocalDate

/**
 * The on-device database. Callers read everything with [snapshot] and must call
 * every method off the main thread.
 */
class Store(context: Context) : SQLiteOpenHelper(context, "moneyment.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE category (
                id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, builtin TEXT, name TEXT,
                emoji TEXT NOT NULL, color INTEGER NOT NULL, archived INTEGER NOT NULL,
                position INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE account (
                id INTEGER PRIMARY KEY AUTOINCREMENT, builtin TEXT, name TEXT,
                archived INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE txn (
                id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL,
                amount_cents INTEGER NOT NULL, category_id INTEGER NOT NULL,
                account_id INTEGER NOT NULL, epoch_day INTEGER NOT NULL, note TEXT NOT NULL)"""
        )
        defaultCategories().forEach { db.insertOrThrow("category", null, it.values()) }
        defaultAccounts().forEach { db.insertOrThrow("account", null, it.values()) }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun snapshot(): Snapshot {
        val db = readableDatabase
        return Snapshot(
            categories = db.rows("SELECT id, kind, builtin, name, emoji, color, archived, position FROM category ORDER BY position, id") {
                Category(
                    id = getLong(0), kind = Kind.valueOf(getString(1)),
                    builtin = getStringOrNull(2), name = getStringOrNull(3),
                    emoji = getString(4), color = getInt(5), archived = getInt(6) != 0,
                    position = getInt(7),
                )
            },
            accounts = db.rows("SELECT id, builtin, name, archived FROM account ORDER BY id") {
                Account(getLong(0), getStringOrNull(1), getStringOrNull(2), getInt(3) != 0)
            },
            transactions = db.rows("SELECT id, kind, amount_cents, category_id, account_id, epoch_day, note FROM txn") {
                Transaction(
                    id = getLong(0), kind = Kind.valueOf(getString(1)), amountCents = getLong(2),
                    categoryId = getLong(3), accountId = getLong(4),
                    date = LocalDate.ofEpochDay(getLong(5)), note = getString(6),
                )
            },
        )
    }

    /**
     * Inserts when the id is 0 or unknown, otherwise replaces the row with that id.
     * Returns the stored row id.
     */
    fun save(transaction: Transaction): Long = save("txn", transaction.id, transaction.values())

    fun save(category: Category) {
        save("category", category.id, category.values())
    }

    fun save(account: Account) {
        save("account", account.id, account.values())
    }

    fun deleteTransaction(id: Long) {
        writableDatabase.delete("txn", "id = ?", arrayOf(id.toString()))
    }

    /** Deletes everything and stores [snapshot] instead, keeping its ids. */
    fun replaceAll(snapshot: Snapshot) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            listOf("txn", "category", "account").forEach { db.delete(it, null, null) }
            snapshot.categories.forEach { save("category", it.id, it.values()) }
            snapshot.accounts.forEach { save("account", it.id, it.values()) }
            snapshot.transactions.forEach { save("txn", it.id, it.values()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Throws [IllegalStateException] when the row could not be written. */
    private fun save(table: String, id: Long, values: ContentValues): Long {
        if (id != 0L) values.put("id", id)
        val row = writableDatabase.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        check(row != -1L) { "could not save to $table" }
        return row
    }

    private fun Category.values() = ContentValues().apply {
        put("kind", kind.name); put("builtin", builtin); put("name", name); put("emoji", emoji)
        put("color", color); put("archived", if (archived) 1 else 0); put("position", position)
    }

    private fun Account.values() = ContentValues().apply {
        put("builtin", builtin); put("name", name); put("archived", if (archived) 1 else 0)
    }

    private fun Transaction.values() = ContentValues().apply {
        put("kind", kind.name); put("amount_cents", amountCents); put("category_id", categoryId)
        put("account_id", accountId); put("epoch_day", date.toEpochDay()); put("note", note)
    }

    private fun <T> SQLiteDatabase.rows(sql: String, read: Cursor.() -> T): List<T> =
        rawQuery(sql, null).use { c ->
            buildList { while (c.moveToNext()) add(c.read()) }
        }

    private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
}
