package com.workshop.ledger.data.export

import com.workshop.ledger.domain.*
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Base64
import stayar.parity.JsonReader

internal fun obj(v: Any?) = v as Map<*, *>
internal fun arr(v: Any?) = v as? List<*> ?: emptyList<Any?>()
internal fun num(v: Any?, default: Long = 0): BigDecimal = v as? BigDecimal ?: BigDecimal.valueOf(default)
internal fun money(v: Any?): Long = num(v).longValueExact()
internal fun active(o: Map<*, *>) = o["active"] != false
internal fun date(s: String): LocalDate {
    val p=s.split('/').map(String::toInt)
    check(JalaliCalendar.isValidDate(p[0],p[1],p[2])) { "Invalid legacy date $s" }
    val g=JalaliCalendar.toGregorian(p[0],p[1],p[2]);return LocalDate.of(g.year,g.month,g.day)
}
internal fun optDate(v: Any?): LocalDate? = (v as? String)?.takeIf { it.isNotEmpty() }?.let(::date)
internal fun range(o: Map<*, *>) = DateRange(optDate(o["from"]),optDate(o["to"]))
internal fun attendance(v: Any?): AttendanceValue = when(v) {
    is Map<*, *> -> AttendanceValue.Hours(v["h"] as BigDecimal)
    is String -> when(v) { "1" -> AttendanceValue.LegacyString.FULL; "0.5" -> AttendanceValue.LegacyString.HALF; "0" -> AttendanceValue.LegacyString.ABSENT; else -> error("Unsupported legacy attendance $v") }
    is BigDecimal -> when {
        v.compareTo(BigDecimal.ZERO)==0 -> AttendanceValue.Absent
        v.compareTo(BigDecimal("0.5"))==0 -> AttendanceValue.Half
        v.compareTo(BigDecimal.ONE)==0 -> AttendanceValue.Full
        else -> error("Unsupported legacy attendance $v")
    }
    else -> error("Unsupported legacy attendance $v")
}
internal fun project(o: Map<*, *>): FinancialProject {
    val settings=obj(o["settings"])
    val cfg=FinancialSettings(num(settings["overtimeFactor"],1),num(settings["absentPenalty"],0).let { if(settings["absentPenalty"]==null) BigDecimal("0.5") else it },settings["profitBasis"] as? String ?: "cash",DateRange(optDate(settings["rangeFrom"]),optDate(settings["rangeTo"])))
    val users=arr(o["users"]).map { val x=obj(it); FinancialUser(x["id"] as String,x["name"] as String,x["role"] as? String,x["type"] as String,active(x),x["main"]==true,num(x["workFactor"]),(x["deletedAt"] as? BigDecimal)?.longValueExact()) }
    val wages=arr(o["wages"]).map { val x=obj(it);EffectiveWage(x["userId"] as String,date(x["effectiveDate"] as String),money(x["rate"])) }
    val days=linkedMapOf<LocalDate,Map<String,AttendanceValue>>()
    obj(o["attendance"]).forEach { (d,day) -> days[date(d as String)]=obj(day).entries.associate { (id,value) -> id as String to attendance(value) } }
    val receipts=arr(o["receipts"]).map { val x=obj(it);FinancialReceipt(date(x["dateFrom"] as String),date(x["dateTo"] as String),money(x["amount"]),active(x)) }
    val expenses=arr(o["expenses"]).map { val x=obj(it);FinancialExpense(date(x["date"] as String),money(x["amount"]),x["payerId"] as? String,active(x)) }
    val payouts=arr(o["payouts"]).map { val x=obj(it);FinancialPayout(date(x["date"] as String),x["userId"] as String,money(x["amount"]),active(x)) }
    val transfers=arr(o["transfers"]).map { val x=obj(it);FinancialTransfer(date(x["date"] as String),x["fromId"] as? String,x["toId"] as? String,money(x["amount"]),x["kind"] as String,active(x)) }
    val adjustments=arr(o["adjustments"]).map { val x=obj(it);FinancialAdjustment(date(x["date"] as String),x["userId"] as String,x["kind"] as String,money(x["amount"]),active(x)) }
    return FinancialProject(cfg,users,wages,days,receipts,expenses,payouts,transfers,adjustments)
}
fun main(args: Array<String>) {
    require(args.size == 1)
    val golden = obj(JsonReader(File(args[0]).readText(Charsets.UTF_8)).read())
    val primary = arr(golden["cases"]).map(::obj).first { it["fn"] == "exportFinancialReportCSV" }
    val cases = listOf(primary) + arr(golden["financialVariants"]).map(::obj)
    check(cases.size == 15)
    cases.forEachIndexed { index, c ->
        val originalProject = obj(arr(c["args"]).single())
        val result = FinancialEngine.compute(project(originalProject))
        val actual = LegacyCsv.encode(FinancialCsvReport.rows(result, originalProject["name"] as String, "1403/01/15 12:34"))
        val expected = Base64.getDecoder().decode(obj(c["result"])["contentBase64"] as String)
        check(actual.contentEquals(expected)) {
            val a = actual.toString(Charsets.UTF_8); val e = expected.toString(Charsets.UTF_8)
            val first = a.indices.firstOrNull { it >= e.length || a[it] != e[it] } ?: minOf(a.length,e.length)
            "Financial CSV case $index differs at char $first; actual=${a.drop(first).take(80)} expected=${e.drop(first).take(80)}"
        }
    }
    println("Matched 15 complete financial CSV byte fixtures generated from legacy project snapshots; file export not implemented.")
}
