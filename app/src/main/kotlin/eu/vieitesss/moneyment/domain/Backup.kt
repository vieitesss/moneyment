package eu.vieitesss.moneyment.domain

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private const val BACKUP_VERSION = 1

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
        require(t.amountCents > 0) { "Amount must be positive" }
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
