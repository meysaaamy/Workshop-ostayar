package com.workshop.ledger.data.legacy

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.workshop.ledger.data.migration.LegacyRoomMigration1To2
import com.workshop.ledger.data.migration.LegacyRoomMigration2To3

/** Transitional v3 schema. No DAO or business writes until migration and recovery are validated. */
@Database(
    entities = [ProjectsLegacyEntity::class, UsersLegacyEntity::class, WagesLegacyEntity::class,
        AttendanceLegacyEntity::class, ExpensesLegacyEntity::class, ReceiptsLegacyEntity::class,
        PayoutsLegacyEntity::class, TransfersLegacyEntity::class, AdjustmentsLegacyEntity::class,
        CategoriesLegacyEntity::class], version = 3, exportSchema = true
)
abstract class WorkshopDatabase : RoomDatabase() {
    companion object {
        const val DATABASE_NAME = "workshop-ledger.db"

        /**
         * Construct lazily; opening runs migrations. Do not open a real user database before
         * safe backup, DataStore recovery and Android Room tests are complete.
         * Caller owns and closes the instance; app code must use a singleton.
         */
        fun create(context: Context): WorkshopDatabase = Room.databaseBuilder(
            context.applicationContext, WorkshopDatabase::class.java, DATABASE_NAME
        ).addMigrations(LegacyRoomMigration1To2, LegacyRoomMigration2To3).build()
    }
}
