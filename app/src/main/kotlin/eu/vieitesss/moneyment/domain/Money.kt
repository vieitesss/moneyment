package eu.vieitesss.moneyment.domain

private val AMOUNT_INPUT = Regex("""\d{0,7}([.,]\d{0,2})?""")

/** True while [text] can still become a valid Amount as the person types. */
fun isAmountInput(text: String): Boolean = AMOUNT_INPUT.matches(text)

/** Cents for typed text such as "23,5" or "23.50"; null when empty, invalid, or zero. */
fun parseAmount(text: String): Long? {
    if (text.isEmpty() || !isAmountInput(text)) return null
    val parts = text.split('.', ',')
    val euros = parts[0].ifEmpty { "0" }.toLong()
    val cents = parts.getOrElse(1) { "" }.padEnd(2, '0').toLong()
    return (euros * 100 + cents).takeIf { it > 0 }
}

/** Plain text for cents, such as "23.50", with the given decimal separator. */
fun centsToText(cents: Long, separator: Char = '.'): String =
    "${cents / 100}$separator${(cents % 100).toString().padStart(2, '0')}"
