package com.workshop.ledger

import android.app.Application
import com.workshop.ledger.data.legacy.WorkshopDatabase
import com.workshop.ledger.data.settings.LegacySettingsStore

/** The warning-only app never accesses this property, so startup cannot migrate user data. */
class StayarApplication : Application() {
    // Creating a Room instance is lazy; its first query or openHelper access opens/migrates the DB.
    // Do not access in production until backup and real Android migration tests are complete.
    val legacySettings: LegacySettingsStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LegacySettingsStore.fromApp(this)
    }
    val workshopDatabase: WorkshopDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        WorkshopDatabase.create(this)
    }
}
