package com.workshop.ledger.data.settings

/** Read-only interpretation of supported legacy preferences. Does not normalize or write. */
object LegacySettingsSemantics {
    data class Snapshot(
        val activeProjectId: String?,
        val attendanceCoachDismissed: Boolean,
        val attendanceCoachWasPresent: Boolean,
        val autoBackupEnvelope: String?,
        val onboardingDecision: String?,
        val projectSettings: Map<String, String>,
        /** Values not interpreted here; the underlying DataStore file is never rewritten. */
        val unrecognizedKeys: Set<String>
    )
    class InvalidType(val key: String) : IllegalArgumentException("Unexpected legacy preference type: $key")

    fun interpret(values: Map<String, Any>): Snapshot {
        fun string(key: String): String? {
            val value=values[key] ?: return null
            return value as? String ?: throw InvalidType(key)
        }
        val coach=values["attendance_coach_dismissed"]
        if (coach != null && coach !is Boolean) throw InvalidType("attendance_coach_dismissed")
        val known=mutableSetOf("active_project_id", "attendance_coach_dismissed", "auto_backup_envelope", "onboarding_decision")
        val projects=mutableMapOf<String,String>()
        for ((key,value) in values) {
            if (!key.startsWith("project_") || !key.endsWith("_settings")) continue
            if (key.length < 17) continue
            val id=key.substring(8,key.length-9)
            if (id.isEmpty()) continue
            if (value !is String) throw InvalidType(key) // legacy ignores this entry; fail closed here.
            projects[id]=value; known.add(key)
        }
        return Snapshot(string("active_project_id"), coach ?: false, coach != null,
            string("auto_backup_envelope"),string("onboarding_decision"),projects.toMap(),values.keys-known)
    }
}
