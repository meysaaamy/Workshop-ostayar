package com.workshop.ledger.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate

/** Immutable input snapshot. All money values are whole toman. */
data class FinancialSettings(val overtimeFactor: BigDecimal = BigDecimal.ONE, val absentPenalty: BigDecimal = BigDecimal("0.5"), val profitBasis: String = "cash", val range: DateRange = DateRange())
data class FinancialUser(val id: String, val name: String, val role: String? = null, val type: String, val active: Boolean = true, val main: Boolean = false, val workFactor: BigDecimal = BigDecimal.ZERO, val deletedAt: Long? = null)
data class FinancialExpense(val date: LocalDate, val amount: Long, val payerId: String? = null, val active: Boolean = true)
data class FinancialReceipt(val start: LocalDate, val end: LocalDate, val amount: Long, val active: Boolean = true)
data class FinancialPayout(val date: LocalDate, val userId: String, val amount: Long, val active: Boolean = true)
data class FinancialTransfer(val date: LocalDate, val fromId: String?, val toId: String?, val amount: Long, val kind: String, val active: Boolean = true)
data class FinancialAdjustment(val date: LocalDate, val userId: String, val kind: String, val amount: Long, val active: Boolean = true)
data class FinancialProject(
    val settings: FinancialSettings, val users: List<FinancialUser>, val wages: List<EffectiveWage> = emptyList(),
    val attendance: Map<LocalDate, Map<String, AttendanceValue>> = emptyMap(), val receipts: List<FinancialReceipt> = emptyList(),
    val expenses: List<FinancialExpense> = emptyList(), val payouts: List<FinancialPayout> = emptyList(),
    val transfers: List<FinancialTransfer> = emptyList(), val adjustments: List<FinancialAdjustment> = emptyList()
)

/** A compute result is not a database entity; fields are accumulated only during a single compute call. */
class FinancialAccount internal constructor(val user: FinancialUser) {
    var full = 0; internal set
    var half = 0; internal set
    var hourly = 0; internal set
    var absent = 0; internal set
    var overtimeHours: BigDecimal = BigDecimal.ZERO; internal set
    var days: BigDecimal = BigDecimal.ZERO; internal set
    var baseWage = 0L; internal set
    var bonus = 0L; internal set
    var penalty = 0L; internal set
    var assistance = 0L; internal set
    var settle = 0L; internal set
    var paid = 0L; internal set
    var given = 0L; internal set
    var taken = 0L; internal set
    var advance = 0L; internal set
    var reimburse = 0L; internal set
    var entitled = 0L; internal set
    var totalPaid = 0L; internal set
    var debt = 0L; internal set
    var workFinal: BigDecimal = BigDecimal.ZERO; internal set
    var percent: BigDecimal = BigDecimal.ZERO; internal set
    var share = 0L; internal set
    var sharedPersonalExp = 0L; internal set
    var finalBalance = 0L; internal set
}
class FinancialResult internal constructor(val range: DateRange, val overtimeFactor: BigDecimal, val basis: String) {
    val acc = linkedMapOf<String, FinancialAccount>()
    val errors = mutableListOf<String>()
    val masters = mutableListOf<FinancialAccount>()
    val staff = mutableListOf<FinancialAccount>()
    val apprentices = mutableListOf<FinancialAccount>()
    val others = mutableListOf<FinancialAccount>()
    val negativeMasters = mutableListOf<FinancialAccount>()
    var totalReceipts = 0L; internal set
    var unpaidWages = 0L; internal set
    var sharedExpenses = 0L; internal set
    var personalExpTotal = 0L; internal set
    var totalWages = 0L; internal set
    var totalCashPayouts = 0L; internal set
    var staffAssistance = 0L; internal set
    var masterBonus = 0L; internal set
    var masterPenalty = 0L; internal set
    var profit = 0L; internal set
    var unallocated = 0L; internal set
    var totalWorkFinal: BigDecimal = BigDecimal.ZERO; internal set
}

/** Legacy order and daily rounding on valid dates. Arithmetic outside Long fails rather than silently wrapping. */
object FinancialEngine {
    private fun add(a: Long, b: Long) = Math.addExact(a,b)
    private fun sub(a: Long, b: Long) = Math.subtractExact(a,b)
    private fun binary(value: BigDecimal) = LegacyBinary64.decimal(value)
    private fun sumDecimal(a: BigDecimal, b: BigDecimal) = (binary(a) + binary(b)).toBigDecimal()
    private fun asJalali(d: LocalDate): String {
        val j = JalaliCalendar.toJalali(d.year,d.monthValue,d.dayOfMonth)
        return "%04d/%02d/%02d".format(java.util.Locale.ROOT,j.year,j.month,j.day)
    }
    fun compute(project: FinancialProject, range: DateRange = project.settings.range): FinancialResult {
        val ot = if (project.settings.overtimeFactor >= BigDecimal.ONE) project.settings.overtimeFactor else BigDecimal.ONE
        val pen = if (project.settings.absentPenalty >= BigDecimal.ZERO && project.settings.absentPenalty <= BigDecimal("3")) project.settings.absentPenalty else BigDecimal("0.5")
        val basis = if (project.settings.profitBasis == "commitment") "commitment" else "cash"
        val r = FinancialResult(range, ot, basis)
        fun inside(date: LocalDate) = (range.from == null || date >= range.from) && (range.to == null || date <= range.to)
        project.users.filter { it.deletedAt == null || it.deletedAt == 0L }.forEach { r.acc[it.id] = FinancialAccount(it) }
        project.attendance.toSortedMap().forEach { (date, day) ->
            if (!inside(date)) return@forEach
            day.forEach entry@{ (userId,value) ->
                val a = r.acc[userId] ?: return@entry
                val fraction = AttendanceMath.dayFraction(value,ot,pen)
                if (fraction == null) {
                    if (r.errors.size < 30) {
                        val extra = if (value is AttendanceValue.Hours && value.hours > BigDecimal("24")) " (${value.hours.stripTrailingZeros().toPlainString()} ساعت؛ حداکثر 24 ساعت در روز)" else ""
                        r.errors.add("مقدار حضور نامعتبر: ${a.user.name} — ${asJalali(date)}$extra")
                    }
                    return@entry
                }
                when (value) {
                    AttendanceValue.Full -> a.full++
                    AttendanceValue.Half -> a.half++
                    AttendanceValue.Absent -> a.absent++
                    is AttendanceValue.LegacyString -> a.hourly++
                    is AttendanceValue.Hours -> {
                        a.hourly++
                        if (value.hours > BigDecimal("8")) a.overtimeHours = sumDecimal(a.overtimeHours,(binary(value.hours)-binary(BigDecimal("8"))).toBigDecimal())
                    }
                }
                a.days = sumDecimal(a.days,fraction)
                if (a.user.type != "master") {
                    val rate = WageRates.forDate(userId,date,project.wages)
                    val daily = (binary(fraction)*LegacyBinary64.integer(BigInteger.valueOf(rate))).roundJs().longValueExact()
                    a.baseWage = add(a.baseWage,daily)
                }
            }
        }
        project.receipts.filter { it.active }.forEach { r.totalReceipts = add(r.totalReceipts,ReceiptShares.share(it.amount,it.start,it.end,range)) }
        project.expenses.filter { it.active && inside(it.date) }.forEach {
            val a = r.acc[it.payerId]
            if (a != null && a.user.type == "master") { a.reimburse=add(a.reimburse,it.amount); r.personalExpTotal=add(r.personalExpTotal,it.amount) }
            else r.sharedExpenses=add(r.sharedExpenses,it.amount)
        }
        project.payouts.filter { it.active && inside(it.date) }.forEach {
            val a = r.acc[it.userId] ?: return@forEach
            if (a.user.type != "master") { a.paid=add(a.paid,it.amount); r.totalCashPayouts=add(r.totalCashPayouts,it.amount) }
            else if (r.errors.size < 30) r.errors.add("پرداختی به اوستا ثبت شده است (${a.user.name}) و محاسبه نشد")
        }
        project.transfers.filter { it.active && inside(it.date) }.forEach {
            if (it.kind == "advance") {
                val a = r.acc[it.toId] ?: r.acc[it.fromId]
                if (a != null && a.user.type == "master") a.advance=add(a.advance,it.amount)
            } else {
                r.acc[it.fromId]?.let { a -> a.given=add(a.given,it.amount) }
                r.acc[it.toId]?.let { a -> a.taken=add(a.taken,it.amount) }
            }
        }
        project.adjustments.filter { it.active && inside(it.date) }.forEach {
            val a=r.acc[it.userId] ?: return@forEach
            val master=a.user.type=="master"
            when(it.kind) {
                "bonus" -> { a.bonus=add(a.bonus,it.amount); if(master) r.masterBonus=add(r.masterBonus,it.amount) }
                "penalty" -> { a.penalty=add(a.penalty,it.amount); if(master) r.masterPenalty=add(r.masterPenalty,it.amount) }
                "assistance" -> { a.assistance=add(a.assistance,it.amount); if(!master) r.staffAssistance=add(r.staffAssistance,it.amount) }
                "settle" -> a.settle=add(a.settle,it.amount)
            }
        }
        r.acc.values.filter { it.user.type != "master" }.forEach { a ->
            a.entitled=sub(add(a.baseWage,a.bonus),a.penalty)
            a.totalPaid=add(a.paid,a.assistance);a.debt=sub(a.entitled,a.totalPaid)
            r.totalWages=add(r.totalWages,a.entitled);r.unpaidWages=add(r.unpaidWages,maxOf(0,a.debt))
            r.staff.add(a); if(a.user.type=="apprentice") r.apprentices.add(a) else r.others.add(a)
        }
        r.profit = if (basis=="commitment") add(sub(sub(sub(r.totalReceipts,r.sharedExpenses),r.totalWages),r.masterBonus),r.masterPenalty)
            else add(sub(sub(sub(sub(r.totalReceipts,r.sharedExpenses),r.totalCashPayouts),r.staffAssistance),r.masterBonus),r.masterPenalty)
        val masters=r.acc.values.filter { it.user.type=="master" }
        masters.forEach { a ->
            val w = if(a.user.active) binary(a.days)*binary(a.user.workFactor) else LegacyBinary64.zero
            a.workFinal = if(w < LegacyBinary64.zero) BigDecimal.ZERO else w.toBigDecimal()
            r.totalWorkFinal=sumDecimal(r.totalWorkFinal,a.workFinal)
        }
        val weights=masters.map { it.workFinal }
        val shares=LargestRemainder.allocate(BigDecimal.valueOf(r.profit),weights)
        val personal=LargestRemainder.allocate(BigDecimal.valueOf(r.personalExpTotal),weights)
        masters.forEachIndexed { i,a ->
            a.percent=if(r.totalWorkFinal.signum()==0) BigDecimal.ZERO else (binary(a.workFinal)/binary(r.totalWorkFinal)).toBigDecimal()
            a.share=shares[i];a.sharedPersonalExp=personal[i]
            a.finalBalance=add(sub(add(sub(sub(sub(add(sub(add(a.share,a.reimburse),a.sharedPersonalExp),a.given),a.taken),a.advance),a.assistance),a.bonus),a.penalty),a.settle)
            r.masters.add(a)
        }
        if (r.totalWorkFinal.signum()==0) {
            r.unallocated=r.profit
            if(r.profit!=0L || masters.isNotEmpty()) r.errors.add("مجموع کارکرد اوستاهای فعال صفر است؛ سود قابل تقسیم توزیع نشد")
        }
        r.negativeMasters.addAll(r.masters.filter { it.finalBalance<0 })
        return r
    }
}
