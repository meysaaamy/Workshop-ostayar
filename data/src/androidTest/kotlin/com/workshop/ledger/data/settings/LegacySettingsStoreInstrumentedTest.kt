package com.workshop.ledger.data.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LegacySettingsStoreInstrumentedTest {
    @Test fun readsRealPreferencesWithoutRewritingAndLeavesUnknownKeys() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val path=File(context.cacheDir,"legacy-settings-test.preferences_pb")
        path.delete()
        val scope=CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store=PreferenceDataStoreFactory.create(scope=scope,produceFile={ path })
            store.edit { p ->
                p[stringPreferencesKey("active_project_id")]= "p"
                p[booleanPreferencesKey("attendance_coach_dismissed")]=true
                p[stringPreferencesKey("project_p_settings")]= "{\"h\":1.2500}"
                p[stringPreferencesKey("auto_backup_envelope")]= "{\"snaps\":[]}"
                p[stringPreferencesKey("onboarding_decision")]= "later"
                p[intPreferencesKey("unknown")]=42
            }
            val original=path.readBytes()
            val snapshot=LegacySettingsStore(store).read()
            assertEquals("p",snapshot.activeProjectId);assertTrue(snapshot.attendanceCoachDismissed)
            assertEquals("{\"h\":1.2500}",snapshot.projectSettings["p"])
            assertEquals("{\"snaps\":[]}",snapshot.autoBackupEnvelope);assertEquals("later",snapshot.onboardingDecision)
            assertEquals(setOf("unknown"),snapshot.unrecognizedKeys)
            assertArrayEquals(original,path.readBytes())
        } finally { scope.cancel();path.delete() }
    }
}
