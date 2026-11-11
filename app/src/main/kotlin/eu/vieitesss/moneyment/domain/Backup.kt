package eu.vieitesss.moneyment.domain

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.time.LocalDate

private const val BACKUP_VERSION = 1

// Limits on what a backup may hold. The app never writes anything near them; they keep a
// crafted file from storing rows the database cannot read back or totals that overflow.
private const val MAX_BACKUP_BYTES = 32 * 1024 * 1024
private const val MAX_TRANSACTIONS = 100_000
private const val MAX_LABELS = 1_000
private const val MAX_NOTE_LENGTH = 10_000
private const val MAX_NAME_LENGTH = 1_000
private const val MAX_EMOJI_LENGTH = 64
/** The largest Amount the form accepts: 7 digits and 2 decimals. */
private const val MAX_AMOUNT_CENTS = 999_999_999L
/** The years the date picker can show. */
private val YEARS = 1900..2100

/** The text of a backup file. Throws [IllegalArgumentException] when it is too large to be one. */
fun readBackupText(input: InputStream): String {
    val bytes = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        require(bytes.size() + read <= MAX_BACKUP_BYTES) { "backup too large" }
        bytes.write(buffer, 0, read)
    }
    return bytes.toByteArray().decodeToString()
}

/** The whole [snapshot] as a JSON backup that [decodeBackup] restores exactly. */
fun encodeBackup(snapshot: Snapshot): String {
    val root = JSONObject()
    root.put("app", "moneyment")
    root.put("version", BACKUP_VERSION)
    root.put("categories", JSONArray(snapshot.categories.map {
        JSONObject()
            .put("id", it.id)
            .put("kind", it.kind.name)
            .put("builtin", it.builtin ?: JSONObject.NULL)
            .put("name", it.name ?: JSONObject.NULL)
            .put("emoji", it.emoji)
            .put("color", it.color)
            .put("archived", it.archived)
            .put("position", it.position)
    }))
    root.put("accounts", JSONArray(snapshot.accounts.map {
        JSONObject()
            .put("id", it.id)
            .put("builtin", it.builtin ?: JSONObject.NULL)
            .put("name", it.name ?: JSONObject.NULL)
            .put("archived", it.archived)
    }))
    root.put("transactions", JSONArray(snapshot.transactions.map {
        JSONObject()
            .put("id", it.id)
            .put("kind", it.kind.name)
            .put("amountCents", it.amountCents)
            .put("categoryId", it.categoryId)
            .put("accountId", it.accountId)
            .put("date", it.date.toString())
            .put("note", it.note)
    }))
    return root.toString(2)
}

/** Throws [IllegalArgumentException] when [text] is not a usable backup. */
fun decodeBackup(text: String): Snapshot {
    val snapshot = try {
        val root = JSONObject(text)
        require(root.getString("app") == "moneyment") { "not a Moneyment backup" }
        require(root.getInt("version") == BACKUP_VERSION) { "unsupported backup version" }
        Snapshot(
            categories = root.getJSONArray("categories").objects().map {
                Category(
                    id = it.getLong("id"),
                    kind = Kind.valueOf(it.getString("kind")),
                    builtin = it.stringOrNull("builtin"),
                    name = it.stringOrNull("name"),
                    emoji = it.getString("emoji"),
                    color = it.getInt("color"),
                    archived = it.getBoolean("archived"),
                    position = it.getInt("position"),
                )
            },
            accounts = root.getJSONArray("accounts").objects().map {
                Account(
                    id = it.getLong("id"),
                    builtin = it.stringOrNull("builtin"),
                    name = it.stringOrNull("name"),
                    archived = it.getBoolean("archived"),
                )
            },
            transactions = root.getJSONArray("transactions").objects().map {
                Transaction(
                    id = it.getLong("id"),
                    kind = Kind.valueOf(it.getString("kind")),
                    amountCents = it.getLong("amountCents"),
                    categoryId = it.getLong("categoryId"),
                    accountId = it.getLong("accountId"),
                    date = LocalDate.parse(it.getString("date")),
                    note = it.getString("note"),
                )
            },
        )
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw IllegalArgumentException("malformed backup", e)
    }
    validate(snapshot)
    return snapshot
}

private fun validate(s: Snapshot) {
    require(s.categories.size <= MAX_LABELS) { "too many Categories" }
    require(s.accounts.size <= MAX_LABELS) { "too many Accounts" }
    require(s.transactions.size <= MAX_TRANSACTIONS) { "too many Transactions" }
    for (c in s.categories) {
        require((c.name?.length ?: 0) <= MAX_NAME_LENGTH) { "Category name too long" }
        require((c.builtin?.length ?: 0) <= MAX_NAME_LENGTH) { "Category key too long" }
        require(c.emoji.length <= MAX_EMOJI_LENGTH) { "Category emoji too long" }
    }
    for (a in s.accounts) {
        require((a.name?.length ?: 0) <= MAX_NAME_LENGTH) { "Account name too long" }
        require((a.builtin?.length ?: 0) <= MAX_NAME_LENGTH) { "Account key too long" }
    }
    require(s.categories.all { it.id > 0 }) { "Category id must be positive" }
    require(s.accounts.all { it.id > 0 }) { "Account id must be positive" }
    require(s.transactions.all { it.id > 0 }) { "Transaction id must be positive" }
    require(s.accounts.any { !it.archived }) { "backup has no usable Account" }
    require(s.categories.all { it.name != null || it.builtin != null }) { "Category without name" }
    require(s.accounts.all { it.name != null || it.builtin != null }) { "Account without name" }
    require(s.categories.map { it.id }.toSet().size == s.categories.size) { "duplicate Category id" }
    require(s.accounts.map { it.id }.toSet().size == s.accounts.size) { "duplicate Account id" }
    require(s.transactions.map { it.id }.toSet().size == s.transactions.size) { "duplicate Transaction id" }
    val kinds = s.categories.associate { it.id to it.kind }
    val accountIds = s.accounts.map { it.id }.toSet()
    for (t in s.transactions) {
        require(t.amountCents in 1..MAX_AMOUNT_CENTS) { "Amount out of range" }
        require(t.date.year in YEARS) { "Transaction ${t.id} has a date out of range" }
        require(t.note.length <= MAX_NOTE_LENGTH) { "Transaction ${t.id} has a note too long" }
        require(kinds[t.categoryId] == t.kind) { "Transaction ${t.id} has no matching Category" }
        require(t.accountId in accountIds) { "Transaction ${t.id} has no Account" }
    }
}

/** Transactions as CSV for spreadsheets, oldest first. One-way: it cannot be restored. */
fun encodeCsv(
    snapshot: Snapshot,
    categoryName: (Category) -> String,
    accountName: (Account) -> String,
): String {
    val rows = snapshot.transactions
        .sortedWith(compareBy<Transaction> { it.date }.thenBy { it.id })
        .map { t ->
            listOf(
                t.date.toString(),
                t.kind.name.lowercase(),
                centsToText(t.amountCents),
                snapshot.category(t.categoryId)?.let(categoryName).orEmpty(),
                snapshot.account(t.accountId)?.let(accountName).orEmpty(),
                t.note,
            )
        }
    val header = listOf("date", "kind", "amount", "category", "account", "note")
    return (listOf(header) + rows).joinToString("\r\n", postfix = "\r\n") { row ->
        row.joinToString(",") { csvField(it) }
    }
}

private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
