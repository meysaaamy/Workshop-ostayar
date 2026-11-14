package com.workshop.ledger.data.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** APK-extracted statements, not proof of the real v1 schema. Room validates the final schema. */
object LegacyRoomMigration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)
    internal val statements: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `projects` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `users` (`projectId` TEXT NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `type` TEXT NOT NULL, `workFactor` REAL NOT NULL, `active` INTEGER NOT NULL, `main` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`projectId`, `id`))",
        "CREATE INDEX IF NOT EXISTS `index_users_projectId` ON `users` (`projectId`)",
        "CREATE TABLE IF NOT EXISTS `wages` (`projectId` TEXT NOT NULL, `id` TEXT NOT NULL, `userId` TEXT NOT NULL, `rate` REAL NOT NULL, `effectiveDate` TEXT NOT NULL, PRIMARY KEY(`projectId`, `id`))",
        "CREATE INDEX IF NOT EXISTS `index_wages_projectId` ON `wages` (`projectId`)",
        "CREATE INDEX IF NOT EXISTS `index_wages_projectId_userId` ON `wages` (`projectId`, `userId`)",
        "CREATE TABLE IF NOT EXISTS `attendance` (`projectId` TEXT NOT NULL, `date` TEXT NOT NULL, `userId` TEXT NOT NULL, `valueJson` TEXT NOT NULL, PRIMARY KEY(`projectId`, `date`, `userId`))",
        "CREATE INDEX IF NOT EXISTS `index_attendance_projectId` ON `attendance` (`projectId`)",
        "CREATE INDEX IF NOT EXISTS `index_attendance_projectId_date` ON `attendance` (`projectId`, `date`)",
        "CREATE TABLE IF NOT EXISTS `expenses` (`projectId` TEXT NOT NULL, `id` TEXT NOT NULL, `date` TEXT NOT NULL, `description` TEXT NOT NULL, `categoryId` TEXT, `payerId` TEXT, `amount` REAL NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`projectId`, `id`))",
        "CREATE INDEX IF NOT EXISTS `index_expenses_projectId_date` ON `expenses` (`projectId`, `date`)",
        "CREATE TABLE IF NOT EXISTS `receipts` (`projectId` TEXT NOT NULL, `id` TEXT NOT NULL, `dateFrom` TEXT NOT NULL, `dateTo` TEXT NOT NULL, `amount` REAL NOT NULL, `description` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`projectId`, `id`))",
        "CREATE INDEX IF NOT EXISTS `index_receipts_projectId_dateFrom` ON `receipts` (`projectId`, `dateFrom`)",
        "CREATE TABLE IF NOT EXISTS `payouts` (`projectId` TEXT NOT NULL, `id` TEXT NOT NULL, `date` TEXT NOT NULL, `userId` TEXT NOT NULL, `amount` REAL NOT NULL, `description` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`projectId`, `id`))",
        "CREATE INDEX IF NOT EXISTS `index_payouts_projectId_date` ON `payouts` (`projectId`, `date`)",
        "CREATE TABLE IF NOT EXISTS `transfers` (`projectId` TEXT NOT NULL, `id` TEXT NOT NULL, `date` TEXT NOT NULL, `fromId` TEXT, `toId` TEXT, `amount` REAL NOT NULL, `description` TEXT NOT NULL, `kind` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`projectId`, `id`))",
        "CREATE INDEX IF NOT EXISTS `index_transfers_projectId_date` ON `transfers` (`projectId`, `date`)",
        "CREATE TABLE IF NOT EXISTS `adjustments` (`projectId` TEXT NOT NULL, `id` TEXT NOT NULL, `date` TEXT NOT NULL, `userId` TEXT NOT NULL, `kind` TEXT NOT NULL, `amount` REAL NOT NULL, `description` TEXT NOT NULL, `active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`projectId`, `id`))",
        "CREATE INDEX IF NOT EXISTS `index_adjustments_projectId_date` ON `adjustments` (`projectId`, `date`)",
        "CREATE TABLE IF NOT EXISTS `categories` (`projectId` TEXT NOT NULL, `id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`projectId`, `id`))"
    )
}
