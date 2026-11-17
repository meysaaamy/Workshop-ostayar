package com.workshop.ledger.data.settings

fun main() {
    val values=mapOf<String,Any>(
        "active_project_id" to "پروژه-۱", "attendance_coach_dismissed" to true,
        "auto_backup_envelope" to "{\"snaps\":[{\"h\":1.2500}]}",
        "onboarding_decision" to "later", "project_پروژه-۱_settings" to "{\"x\":1}",
        "project_a_b_settings" to "{\"x\":2}", "project__settings" to "unknown", "project_settings" to "overlap", "new_future_setting" to 17
    )
    val before=values.toMap();val result=LegacySettingsSemantics.interpret(values)
    check(result.activeProjectId=="پروژه-۱" && result.attendanceCoachDismissed && result.attendanceCoachWasPresent)
    check(result.autoBackupEnvelope=="{\"snaps\":[{\"h\":1.2500}]}")
    check(result.onboardingDecision=="later" && result.projectSettings==mapOf("پروژه-۱" to "{\"x\":1}","a_b" to "{\"x\":2}"))
    check(result.unrecognizedKeys==setOf("project__settings","project_settings","new_future_setting") && values==before)
    val empty=LegacySettingsSemantics.interpret(emptyMap())
    check(!empty.attendanceCoachDismissed && !empty.attendanceCoachWasPresent && empty.activeProjectId==null)
    for ((key,value) in listOf("active_project_id" to 3, "attendance_coach_dismissed" to "true", "project_a_settings" to 1)) {
        check(runCatching { LegacySettingsSemantics.interpret(mapOf(key to value)) }.exceptionOrNull() is LegacySettingsSemantics.InvalidType)
    }
    println("Legacy settings semantic interpretation tests passed; no DataStore or Android execution.")
}
