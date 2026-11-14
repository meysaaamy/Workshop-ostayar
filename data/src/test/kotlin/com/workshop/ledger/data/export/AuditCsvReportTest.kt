package com.workshop.ledger.data.export

import com.workshop.ledger.domain.*
import java.io.File
import java.util.Base64
import stayar.parity.JsonReader

fun main(args: Array<String>) {
    require(args.size == 1)
    val golden = obj(JsonReader(File(args[0]).readText(Charsets.UTF_8)).read())
    val primary = arr(golden["cases"]).map(::obj).first { it["fn"] == "exportDetailedAuditCSV" }
    val cases = listOf(primary) + arr(golden["financialVariants"]).map(::obj)
    check(cases.size == 15)
    cases.forEachIndexed { index,c ->
        val input = obj(arr(c["args"]).single()); val p = project(input)
        val receipts = arr(input["receipts"]).mapIndexed { i,v -> AuditReceipt(p.receipts[i],obj(v)["description"] as? String) }
        val expenses = arr(input["expenses"]).mapIndexed { i,v -> val x=obj(v); AuditExpense(p.expenses[i],x["description"] as? String,x["categoryId"] as? String) }
        val payouts = arr(input["payouts"]).mapIndexed { i,v -> AuditPayout(p.payouts[i],obj(v)["description"] as? String) }
        val transfers = arr(input["transfers"]).mapIndexed { i,v -> AuditTransfer(p.transfers[i],obj(v)["description"] as? String) }
        val adjustments = arr(input["adjustments"]).mapIndexed { i,v -> AuditAdjustment(p.adjustments[i],obj(v)["description"] as? String) }
        val categories=arr(input["categories"]).map { v -> val x=obj(v); AuditCategory(x["id"] as String,x["name"] as String) }
        val details=AuditDetails(receipts,expenses,payouts,transfers,adjustments,categories)
        val actual=LegacyCsv.encode(AuditCsvReport.rows(p,details,input["name"] as String))
        val result = if(index==0) obj(c["result"]) else obj(c["auditResult"])
        val expected=Base64.getDecoder().decode(result["contentBase64"] as String)
        check(actual.contentEquals(expected)) {
            val a=actual.toString(Charsets.UTF_8); val e=expected.toString(Charsets.UTF_8)
            val pos=a.indices.firstOrNull { it>=e.length || a[it]!=e[it] } ?: minOf(a.length,e.length)
            "Audit CSV case $index differs at char $pos; actual=${a.drop(pos).take(100)} expected=${e.drop(pos).take(100)}"
        }
        if(receipts.isNotEmpty()) check(runCatching { AuditCsvReport.rows(p,details.copy(receipts=emptyList()),"p") }.isFailure)
    }
    println("Matched 15 complete detailed-audit CSV byte fixtures from legacy project snapshots; file export not implemented.")
}
