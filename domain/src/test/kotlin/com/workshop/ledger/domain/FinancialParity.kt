package com.workshop.ledger.domain

import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import stayar.parity.JsonReader

private fun obj(v: Any?) = v as Map<*, *>
private fun arr(v: Any?) = v as? List<*> ?: emptyList<Any?>()
private fun num(v: Any?, default: Long = 0): BigDecimal = v as? BigDecimal ?: BigDecimal.valueOf(default)
private fun money(v: Any?): Long = num(v).longValueExact()
private fun active(o: Map<*, *>) = o["active"] != false
private fun date(s: String): LocalDate {
    val p=s.split('/').map(String::toInt)
    check(JalaliCalendar.isValidDate(p[0],p[1],p[2])) { "Invalid legacy date $s" }
    val g=JalaliCalendar.toGregorian(p[0],p[1],p[2]);return LocalDate.of(g.year,g.month,g.day)
}
private fun optDate(v: Any?): LocalDate? = (v as? String)?.takeIf { it.isNotEmpty() }?.let(::date)
private fun range(o: Map<*, *>) = DateRange(optDate(o["from"]),optDate(o["to"]))
private fun attendance(v: Any?): AttendanceValue = when(v) {
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
private fun project(o: Map<*, *>): FinancialProject {
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
private fun exactLong(expected: Any?, actual: Long, label: String) {
    check((expected as BigDecimal).longValueExact()==actual) { "$label expected=$expected actual=$actual" }
}
private fun exactBinary(expected: Any?, actual: BigDecimal, label: String) {
    check(LegacyBinary64.decimal(expected as BigDecimal).compareTo(LegacyBinary64.decimal(actual))==0) { "$label expected=$expected actual=$actual" }
}
private fun account(expected: Map<*, *>, a: FinancialAccount, label: String) {
    val identity=mapOf("id" to a.user.id,"name" to a.user.name,"role" to a.user.role,"type" to a.user.type,"active" to a.user.active,"main" to a.user.main)
    val ints=mapOf("full" to a.full.toLong(),"half" to a.half.toLong(),"hourly" to a.hourly.toLong(),"absent" to a.absent.toLong(),"baseWage" to a.baseWage,"bonus" to a.bonus,"penalty" to a.penalty,"assistance" to a.assistance,"settle" to a.settle,"paid" to a.paid,"given" to a.given,"taken" to a.taken,"advance" to a.advance,"reimburse" to a.reimburse,"entitled" to a.entitled,"totalPaid" to a.totalPaid,"debt" to a.debt,"share" to a.share,"sharedPersonalExp" to a.sharedPersonalExp,"finalBalance" to a.finalBalance)
    val decimals=mapOf("workFactor" to a.user.workFactor,"overtimeHours" to a.overtimeHours,"days" to a.days,"workFinal" to a.workFinal,"percent" to a.percent)
    for((key,value) in expected) {
        val k=key as String
        when {
            identity.containsKey(k) -> check(value==identity[k]) { "$label.$k expected=$value actual=${identity[k]}" }
            ints.containsKey(k) -> exactLong(value,ints.getValue(k),"$label.$k")
            decimals.containsKey(k) -> exactBinary(value,decimals.getValue(k),"$label.$k")
            else -> error("Unmapped legacy account field $label.$k")
        }
    }
}
private fun accounts(expected: Any?, actual: List<FinancialAccount>, label: String) {
    val list=arr(expected);check(list.size==actual.size) { "$label size expected=${list.size} actual=${actual.size}" }
    list.forEachIndexed { index,it -> account(obj(it),actual[index],"$label[$index]") }
}

fun main(args: Array<String>) {
    require(args.size==1)
    val root=obj(JsonReader(File(args[0]).readText(Charsets.UTF_8)).read())
    val cases=arr(root["cases"]).map(::obj).filter { it["fn"]=="compute" }
    check(cases.size>=200)
    cases.forEachIndexed { index,c ->
        val arguments=arr(c["args"]);val p=project(obj(arguments[1]));val rangeArg=arguments[0]
        val result=if(rangeArg==null) FinancialEngine.compute(p) else FinancialEngine.compute(p,range(obj(rangeArg)))
        val expected=obj(c["result"]);val label="compute[$index]"
        check(expected["basis"]==result.basis);exactBinary(expected["ot"],result.overtimeFactor,"$label.ot")
        val all=obj(expected["acc"]);check(all.keys==result.acc.keys) { "$label account keys differ" }
        all.forEach { (id,v) -> account(obj(v),result.acc.getValue(id as String),"$label.acc.$id") }
        check(arr(expected["errors"])==result.errors) { "$label errors expected=${expected["errors"]} actual=${result.errors}" }
        accounts(expected["masters"],result.masters,"$label.masters");accounts(expected["staff"],result.staff,"$label.staff")
        accounts(expected["apprentices"],result.apprentices,"$label.apprentices");accounts(expected["others"],result.others,"$label.others")
        accounts(expected["negativeMasters"],result.negativeMasters,"$label.negativeMasters")
        val totals=mapOf("totalReceipts" to result.totalReceipts,"unpaidWages" to result.unpaidWages,"sharedExpenses" to result.sharedExpenses,"personalExpTotal" to result.personalExpTotal,"totalWages" to result.totalWages,"totalCashPayouts" to result.totalCashPayouts,"staffAssistance" to result.staffAssistance,"masterBonus" to result.masterBonus,"masterPenalty" to result.masterPenalty,"profit" to result.profit,"unallocated" to result.unallocated)
        totals.forEach { (key,value)->exactLong(expected[key],value,"$label.$key") }
        exactBinary(expected["totalWorkFinal"],result.totalWorkFinal,"$label.totalWorkFinal")
        val eRange=obj(expected["range"]);check(optDate(eRange["from"])==result.range.from && optDate(eRange["to"])==result.range.to)
        val expectedKeys=setOf("range","ot","basis","acc","errors","masters","staff","apprentices","others","negativeMasters","totalWorkFinal")+totals.keys
        check(expected.keys==expectedKeys) { "$label unhandled result fields ${expected.keys - expectedKeys}" }
    }
    println("Matched all fields in ${cases.size} valid-date compute scenarios against the old engine (money exact, fractions compared as legacy binary values).")
}
