package com.workshop.ledger.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// Same file and preference names as the APK. One delegate per process; no overwrite or conversion.
private val Context.legacyLedgerSettings by preferencesDataStore(name = "ledger_settings")

/** Experimental read-only semantics; app does not access this store during startup. */
class LegacySettingsStore internal constructor(private val store: DataStore<Preferences>) {
    companion object {
        fun fromApp(context: Context): LegacySettingsStore = LegacySettingsStore(context.applicationContext.legacyLedgerSettings)
    }
    suspend fun read(): LegacySettingsSemantics.Snapshot {
        val values=store.data.first().asMap().mapKeys { it.key.name }
        return LegacySettingsSemantics.interpret(values)
    }
}
