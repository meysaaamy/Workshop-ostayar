package com.workshop.ledger.data.export

/** Numbers must already have the desired finite numeric text; text cells are formula guarded. */
sealed interface CsvCell {
    data object Empty : CsvCell
    data class Text(val value: String) : CsvCell
    data class Integer(val value: Long) : CsvCell
    /** Decimal formatting/rounding is the caller's responsibility, not performed by this writer. */
    class Decimal private constructor(val canonical: String) : CsvCell {
        companion object {
            // Avoid allowing formulas, whitespace, non-finite values and noncanonical leading/trailing zeroes.
            private val syntax = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]*[1-9])?(?:e[+-][1-9][0-9]*)?")
            fun fromCanonical(value: String): Decimal {
                require(value.length <= 128 && syntax.matches(value) && value != "-0") { "Invalid canonical decimal" }
                return Decimal(value)
            }
        }
    }
}

/** Reproduces the legacy csvCell and downloadCSV layout for these supported cell types. */
object LegacyCsv {
    fun cell(value: CsvCell): String = when (value) {
        CsvCell.Empty -> ""
        is CsvCell.Integer -> value.value.toString()
        is CsvCell.Decimal -> value.canonical
        is CsvCell.Text -> {
            val s = if (value.value.firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'" + value.value else value.value
            if (s.any { it == '"' || it == ',' || it == '\r' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        }
    }
    fun encode(rows: List<List<CsvCell>>): ByteArray =
        ("\uFEFF" + rows.joinToString("\r\n") { row -> row.joinToString(",", transform = ::cell) }).toByteArray(Charsets.UTF_8)
}
