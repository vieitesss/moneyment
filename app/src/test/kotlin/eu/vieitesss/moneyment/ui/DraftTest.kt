package eu.vieitesss.moneyment.ui

import eu.vieitesss.moneyment.domain.Account
import eu.vieitesss.moneyment.domain.Category
import eu.vieitesss.moneyment.domain.Kind
import eu.vieitesss.moneyment.domain.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DraftTest {
    private val groceries = Category(1, Kind.EXPENSE, "groceries", null, "🛒", 0, false, 0)
    private val travel = Category(2, Kind.EXPENSE, "travel", null, "✈️", 1, false, 1)
    private val cash = Account(1, "cash", null, false)
    private val card = Account(2, null, "Card", false)

    private fun data(archivedCategory: Boolean = false, archivedAccount: Boolean = false) = Snapshot(
        categories = listOf(groceries, travel.copy(archived = archivedCategory)),
        accounts = listOf(cash, card.copy(archived = archivedAccount)),
        transactions = emptyList(),
    )

    private fun draft(categoryId: Long?, accountId: Long = card.id) =
        FormState(Kind.EXPENSE, "12,50", categoryId, accountId, LocalDate.of(2026, 11, 10), "Lunch")

    @Test
    fun aDraftKeepsChoicesThatCanStillBeMade() {
        val form = draft(travel.id)
        assertEquals(form, repairedDraft(form, data(), lastUsedAccountId = null))
    }

    @Test
    fun anArchivedCategoryIsDropped() {
        val form = draft(travel.id)
        val repaired = repairedDraft(form, data(archivedCategory = true), lastUsedAccountId = null)
        assertEquals(null, repaired.categoryId)
    }

    @Test
    fun anArchivedAccountBecomesTheDefaultOne() {
        val form = draft(groceries.id)
        val repaired = repairedDraft(form, data(archivedAccount = true), lastUsedAccountId = null)
        assertEquals(cash.id, repaired.accountId)
    }

    @Test
    fun theRepairedDraftKeepsEverythingElseThePersonTyped() {
        val form = draft(travel.id)
        val repaired = repairedDraft(form, data(archivedCategory = true, archivedAccount = true), null)
        assertEquals(form.copy(categoryId = null, accountId = cash.id), repaired)
    }

    @Test
    fun saveNeedsAnAmountAboveZeroAndACategory() {
        val form = draft(groceries.id)
        assertEquals(true, form.canSave)
        listOf("", "0", "0,00", ",").forEach {
            assertEquals(it, false, form.copy(amountText = it).amountValid)
            assertEquals(it, false, form.copy(amountText = it).canSave)
        }
        assertEquals(false, form.copy(categoryId = null).categoryValid)
        assertEquals(false, form.copy(categoryId = null).canSave)
    }
}
