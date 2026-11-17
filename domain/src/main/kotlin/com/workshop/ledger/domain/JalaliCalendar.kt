package com.workshop.ledger.domain

/** Integer-only port of the jalaali-js calendar embedded in the legacy APK. */
object JalaliCalendar {
    data class JalaliDate(val year: Int, val month: Int, val day: Int)
    data class GregorianDate(val year: Int, val month: Int, val day: Int)
    data class YearInfo(val leap: Int, val gregorianYear: Int, val marchDay: Int)

    private val breaks = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)

    fun yearInfo(year: Int): YearInfo {
        require(year >= breaks.first() && year < breaks.last()) { "Invalid Jalaali year $year" }
        val gy = year + 621
        var leapJ = -14
        var previous = breaks[0]
        var jump = 0
        for (i in 1 until breaks.size) {
            val current = breaks[i]
            jump = current - previous
            if (year < current) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            previous = current
        }
        var n = year - previous
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ++
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = (((n + 1) % 33) - 1) % 4
        if (leap == -1) leap = 4
        return YearInfo(leap, gy, march)
    }

    fun isLeapYear(year: Int): Boolean = yearInfo(year).leap == 0
    fun monthLength(year: Int, month: Int): Int = when {
        month <= 6 -> 31
        month <= 11 -> 30
        isLeapYear(year) -> 30
        else -> 29
    }
    fun isValidDate(year: Int, month: Int, day: Int): Boolean =
        year in -61..3177 && month in 1..12 && day >= 1 && day <= monthLength(year, month)

    private fun gregorianToDay(year: Int, month: Int, day: Int): Int {
        var n = ((year + (month - 8) / 6 + 100100) * 1461) / 4 + (153 * ((month + 9) % 12) + 2) / 5 + day - 34840408
        n = n - (((year + 100100 + (month - 8) / 6) / 100) * 3) / 4 + 752
        return n
    }
    private fun dayToGregorian(jdn: Int): GregorianDate {
        var j = 4 * jdn + 139361631
        j = j + (((4 * jdn + 183187720) / 146097) * 3 / 4) * 4 - 3908
        val i = ((j % 1461) / 4) * 5 + 308
        val day = ((i % 153) / 5) + 1
        val month = (i / 153) % 12 + 1
        val year = j / 1461 - 100100 + (8 - month) / 6
        return GregorianDate(year, month, day)
    }
    private fun jalaliToDay(year: Int, month: Int, day: Int): Int {
        val r = yearInfo(year)
        return gregorianToDay(r.gregorianYear, 3, r.marchDay) + (month - 1) * 31 - (month / 7) * (month - 7) + day - 1
    }
    private fun dayToJalali(jdn: Int): JalaliDate {
        val gy = dayToGregorian(jdn).year
        var jy = gy - 621
        val r = yearInfo(jy)
        val first = gregorianToDay(gy, 3, r.marchDay)
        var k = jdn - first
        if (k >= 0) {
            if (k <= 185) return JalaliDate(jy, 1 + k / 31, k % 31 + 1)
            k -= 186
        } else {
            jy--
            k += 179
            if (r.leap == 1) k++
        }
        return JalaliDate(jy, 7 + k / 30, k % 30 + 1)
    }
    // Legacy conversion functions do not validate the day before converting; callers must validate explicitly.
    fun toGregorian(year: Int, month: Int, day: Int): GregorianDate = dayToGregorian(jalaliToDay(year, month, day))
    fun toJalali(year: Int, month: Int, day: Int): JalaliDate = dayToJalali(gregorianToDay(year, month, day))
}
