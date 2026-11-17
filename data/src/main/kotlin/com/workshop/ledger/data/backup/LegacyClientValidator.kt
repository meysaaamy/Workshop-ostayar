package com.workshop.ledger.data.backup

import com.workshop.ledger.domain.LegacyReportNumber
import java.math.BigDecimal

/** Unusual values outside the supported JSON snapshot model fail closed, never silently repaired. */
class UnsupportedLegacyValidationShape : IllegalArgumentException("Unsupported legacy validation input shape")

/** Legacy web clientValidate for supported JSON objects; no native validation or restoration. */
object LegacyClientValidator {
    private val idPattern = Regex("^[A-Za-z0-9_.-]{1,64}$")
    private val absent = JsonValue.Null
    private fun field(o: JsonValue.Object, key: String): JsonValue = o.fields[key] ?: absent
    private fun isNull(v: JsonValue) = v == JsonValue.Null
    private fun booleanOrNull(v: JsonValue) = isNull(v) || v is JsonValue.Bool
    private fun jsWhitespace(c: Char) = c in '\u0009'..'\u000d' || c == '\u0020' || c == '\u00a0' || c == '\u1680' ||
        c in '\u2000'..'\u200a' || c == '\u2028' || c == '\u2029' || c == '\u202f' || c == '\u205f' || c == '\u3000' || c == '\ufeff'
    private fun validText(v: JsonValue, max: Int) = v is JsonValue.Text && v.value.length <= max && v.value.any { !jsWhitespace(it) }
    private fun validId(v: JsonValue) = v is JsonValue.Text && idPattern.matches(v.value)
    private fun among(v: JsonValue, allowed: Set<String>) = v is JsonValue.Text && v.value in allowed
    private fun inRange(v: JsonValue, min: BigDecimal, max: BigDecimal?): Boolean {
        val number = LegacyFieldValidators.number(v) ?: return false
        val lower = LegacyFieldValidators.compare(number, min) ?: return false
        val upper = if(max == null) 0 else LegacyFieldValidators.compare(number, max) ?: return false
        return lower >= 0 && (max == null || upper <= 0)
    }
    private fun jsString(v: JsonValue): String = when(v) {
        JsonValue.Null -> "null"
        is JsonValue.Text -> v.value
        is JsonValue.Bool -> v.value.toString()
        is JsonValue.Number -> {
            val n = LegacyFieldValidators.number(v) ?: throw UnsupportedLegacyValidationShape()
            try { LegacyReportNumber.string(n) } catch (_: ArithmeticException) { throw UnsupportedLegacyValidationShape() }
        }
        is JsonValue.Object -> "[object Object]"
        is JsonValue.Array -> v.values.joinToString(",") { if(it == JsonValue.Null) "" else jsString(it) }
    }
    private fun falsy(v: JsonValue) = isNull(v) || v == JsonValue.Bool(false) || v == JsonValue.Text("") ||
        (v is JsonValue.Number && LegacyFieldValidators.number(v)?.let { LegacyFieldValidators.compare(it,BigDecimal.ZERO) == 0 } == true)
    private fun arrayOrEmpty(v: JsonValue): List<JsonValue> = when {
        falsy(v) -> emptyList()
        v is JsonValue.Array -> v.values
        else -> throw UnsupportedLegacyValidationShape()
    }
    private fun stringProperty(o: JsonValue.Object, key: String): String =
        if (o.fields.containsKey(key)) jsString(field(o,key)) else "undefined"

    fun validate(state: JsonValue): String {
        val projects = (state as? JsonValue.Object)?.let { field(it,"projects") } as? JsonValue.Array
        if (projects == null || projects.values.isEmpty() || projects.values.size > 100) return "تعداد پروژه‌ها باید بین ۱ تا ۱۰۰ باشد"
        val projectIds = mutableSetOf<String>()
        projects.values.forEachIndexed { pi, value ->
            val label="پروژه ${pi+1}"
            val p=value as? JsonValue.Object ?: return "$label: شناسه نامعتبر است"
            val id=field(p,"id")
            if(!validId(id)) return "$label: شناسه نامعتبر است"
            val idText=(id as JsonValue.Text).value
            if(!projectIds.add(idText)) return "$label: شناسه «$idText» تکراری است"
            if(!validText(field(p,"name"),200)) return "$label: نام پروژه خالی یا بیش از ۲۰۰ نویسه است"
            val settingsValue=field(p,"settings")
            val settings = settingsValue as? JsonValue.Object ?: if(falsy(settingsValue)) JsonValue.Object(emptyMap()) else throw UnsupportedLegacyValidationShape()
            for(k in listOf("rangeFrom","rangeTo")) {
                val v=field(settings,k)
                if(!isNull(v) && v != JsonValue.Text("") && !LegacyFieldValidators.validDate(v)) return "$label: تاریخ بازه ($k) نامعتبر است"
            }
            val ot=field(settings,"overtimeFactor")
            if(!isNull(ot) && !inRange(ot,BigDecimal.ONE,BigDecimal("5"))) return "$label: ضریب اضافه‌کار باید بین ۱ تا ۵ باشد"
            val pen=field(settings,"absentPenalty")
            if(!isNull(pen) && !inRange(pen,BigDecimal.ZERO,BigDecimal("3"))) return "$label: کسر غایب باید بین ۰ تا ۳ روز باشد"
            val basis=field(settings,"profitBasis")
            if(!isNull(basis) && !among(basis,setOf("cash","commitment"))) return "$label: مبنای سود نامعتبر است"
            val theme=field(settings,"theme")
            if(!isNull(theme) && !among(theme,setOf("system","light","dark"))) return "$label: تم نامعتبر است"
            if(!booleanOrNull(field(settings,"locked"))) return "$label: وضعیت قفل مالی باید بولی باشد"
            fun desc(r: JsonValue.Object, e: String): String {
                val d=field(r,"description")
                return if(!isNull(d) && jsString(d).length>2000) "$e: توضیحات بیش از حد طولانی است" else ""
            }
            fun money(r: JsonValue.Object, e: String, key: String = "amount") =
                if(LegacyFieldValidators.validAmount(field(r,key))) "" else "$e: مبلغ ($key) منفی، نامعتبر یا بیش از سقف مجاز است"
            fun day(r: JsonValue.Object, e: String, key: String) =
                if(LegacyFieldValidators.validDate(field(r,key))) "" else "$e: تاریخ ($key) باید به شکل YYYY/MM/DD معتبر باشد"
            fun each(key: String, name: String, validate: (JsonValue.Object,String)->String): String {
                val seen=mutableSetOf<String>()
                for((i,item) in arrayOrEmpty(field(p,key)).withIndex()) {
                    val e="$label/$name ${i+1}"
                    val r=item as? JsonValue.Object ?: return "$e: ساختار نامعتبر است"
                    val recId=field(r,"id")
                    if(!validId(recId)) return "$e: شناسه نامعتبر است"
                    val n=(recId as JsonValue.Text).value
                    if(!seen.add(n)) return "$e: شناسه «$n» تکراری است"
                    val error=validate(r,e); if(error.isNotEmpty()) return error
                }
                return ""
            }
            val users=each("users","کاربر") { u,e ->
                val name=field(u,"name")
                when {
                    !validText(name,200) -> "$e: نام خالی یا بیش از ۲۰۰ نویسه است"
                    !isNull(field(u,"role")) && jsString(field(u,"role")).length>200 -> "$e: عنوان نقش بیش از حد طولانی است"
                    !among(field(u,"type"),setOf("master","apprentice","custom")) -> "$e «${(name as JsonValue.Text).value}»: نوع کاربر «${stringProperty(u,"type")}» مجاز نیست (فقط master، apprentice، custom)"
                    !inRange(field(u,"workFactor"),BigDecimal.ZERO,null) -> "$e «${(name as JsonValue.Text).value}»: ضریب کارکرد (workFactor) وجود ندارد یا منفی است"
                    !booleanOrNull(field(u,"active")) || !booleanOrNull(field(u,"main")) -> "$e: مقدار active و main باید بولی باشد"
                    else -> ""
                }
            }
            if(users.isNotEmpty()) return users
            val categories=each("categories","دسته") { c,e -> if(validText(field(c,"name"),200)) "" else "$e: نام خالی یا بیش از ۲۰۰ نویسه است" }
            if(categories.isNotEmpty()) return categories
            val wages=each("wages","نرخ") { r,e -> money(r,e,"rate").ifEmpty { day(r,e,"effectiveDate") } }
            if(wages.isNotEmpty()) return wages
            val expenses=each("expenses","هزینه") { r,e -> day(r,e,"date").ifEmpty { money(r,e) }.ifEmpty { desc(r,e) } }
            if(expenses.isNotEmpty()) return expenses
            val receipts=each("receipts","واریزی") { r,e ->
                day(r,e,"dateFrom").ifEmpty { day(r,e,"dateTo") }.ifEmpty {
                    if((field(r,"dateFrom") as JsonValue.Text).value > (field(r,"dateTo") as JsonValue.Text).value) "$e: تاریخ شروع بعد از تاریخ پایان است" else ""
                }.ifEmpty { money(r,e) }.ifEmpty { desc(r,e) }
            }
            if(receipts.isNotEmpty()) return receipts
            val payouts=each("payouts","پرداختی") { r,e -> day(r,e,"date").ifEmpty { money(r,e) }.ifEmpty { desc(r,e) } }
            if(payouts.isNotEmpty()) return payouts
            val transfers=each("transfers","انتقال") { r,e ->
                day(r,e,"date").ifEmpty { money(r,e) }.ifEmpty { desc(r,e) }.ifEmpty {
                    if(among(field(r,"kind"),setOf("peer","advance"))) "" else "$e: نوع انتقال باید peer یا advance باشد"
                }
            }
            if(transfers.isNotEmpty()) return transfers
            val adjustments=each("adjustments","تعدیل") { r,e ->
                day(r,e,"date").ifEmpty { money(r,e) }.ifEmpty { desc(r,e) }.ifEmpty {
                    if(among(field(r,"kind"),setOf("bonus","penalty","assistance","settle"))) "" else "$e: نوع باید bonus، penalty، assistance یا settle باشد"
                }
            }
            if(adjustments.isNotEmpty()) return adjustments
            val attendanceValue=field(p,"attendance")
            val attendance=attendanceValue as? JsonValue.Object ?: if(falsy(attendanceValue)) JsonValue.Object(emptyMap()) else throw UnsupportedLegacyValidationShape()
            for((date, dayValue) in attendance.fields) {
                if(!LegacyFieldValidators.validDate(JsonValue.Text(date))) return "$label/حضور: تاریخ «$date» نامعتبر است"
                val d=dayValue as? JsonValue.Object ?: return "$label/حضور $date: ساختار نامعتبر است"
                if(d.fields.values.any { !LegacyFieldValidators.validAttendance(it) }) return "$label/حضور $date: مقدار کارکرد نامعتبر یا خارج از ۰ تا ۲۴ ساعت"
            }
        }
        return ""
    }
}
