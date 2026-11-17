package com.workshop.ledger.data.legacy

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** Transitional schema preserving legacy columns. REAL fields are archival only, never financial calculations. */
@Entity(tableName = "projects", primaryKeys = ["id"])
data class ProjectsLegacyEntity(
    @ColumnInfo(name = "id") val `id`: String,
    @ColumnInfo(name = "name") val `name`: String,
    @ColumnInfo(name = "createdAt") val `createdAt`: Long
)

@Entity(tableName = "users", primaryKeys = ["projectId", "id"], indices = [Index(value = ["projectId"], name = "index_users_projectId")])
data class UsersLegacyEntity(
    @ColumnInfo(name = "projectId") val `projectId`: String,
    @ColumnInfo(name = "id") val `id`: String,
    @ColumnInfo(name = "name") val `name`: String,
    @ColumnInfo(name = "role") val `role`: String,
    @ColumnInfo(name = "type") val `type`: String,
    @ColumnInfo(name = "workFactor") val `workFactor`: Double,
    @ColumnInfo(name = "active") val `active`: Long,
    @ColumnInfo(name = "main") val `main`: Long,
    @ColumnInfo(name = "createdAt") val `createdAt`: Long,
    @ColumnInfo(name = "deletedAt") val `deletedAt`: Long?,
    @ColumnInfo(name = "migration_workFactorDecimal", defaultValue = "''") val `migration_workFactorDecimal`: String
)

@Entity(tableName = "wages", primaryKeys = ["projectId", "id"], indices = [Index(value = ["projectId", "userId"], name = "index_wages_projectId_userId"), Index(value = ["projectId"], name = "index_wages_projectId")])
data class WagesLegacyEntity(
    @ColumnInfo(name = "projectId") val `projectId`: String,
    @ColumnInfo(name = "id") val `id`: String,
    @ColumnInfo(name = "userId") val `userId`: String,
    @ColumnInfo(name = "rate") val `rate`: Double,
    @ColumnInfo(name = "effectiveDate") val `effectiveDate`: String,
    @ColumnInfo(name = "migration_effectiveDateIso", defaultValue = "''") val `migration_effectiveDateIso`: String,
    @ColumnInfo(name = "migration_rateToman", defaultValue = "0") val `migration_rateToman`: Long
)

@Entity(tableName = "attendance", primaryKeys = ["projectId", "date", "userId"], indices = [Index(value = ["projectId", "date"], name = "index_attendance_projectId_date"), Index(value = ["projectId"], name = "index_attendance_projectId")])
data class AttendanceLegacyEntity(
    @ColumnInfo(name = "projectId") val `projectId`: String,
    @ColumnInfo(name = "date") val `date`: String,
    @ColumnInfo(name = "userId") val `userId`: String,
    @ColumnInfo(name = "valueJson") val `valueJson`: String,
    @ColumnInfo(name = "migration_dateIso", defaultValue = "''") val `migration_dateIso`: String
)

@Entity(tableName = "expenses", primaryKeys = ["projectId", "id"], indices = [Index(value = ["projectId", "date"], name = "index_expenses_projectId_date")])
data class ExpensesLegacyEntity(
    @ColumnInfo(name = "projectId") val `projectId`: String,
    @ColumnInfo(name = "id") val `id`: String,
    @ColumnInfo(name = "date") val `date`: String,
    @ColumnInfo(name = "description") val `description`: String,
    @ColumnInfo(name = "categoryId") val `categoryId`: String?,
    @ColumnInfo(name = "payerId") val `payerId`: String?,
    @ColumnInfo(name = "amount") val `amount`: Double,
    @ColumnInfo(name = "active") val `active`: Long,
    @ColumnInfo(name = "createdAt") val `createdAt`: Long,
    @ColumnInfo(name = "updatedAt") val `updatedAt`: Long,
    @ColumnInfo(name = "migration_dateIso", defaultValue = "''") val `migration_dateIso`: String,
    @ColumnInfo(name = "migration_amountToman", defaultValue = "0") val `migration_amountToman`: Long
)

@Entity(tableName = "receipts", primaryKeys = ["projectId", "id"], indices = [Index(value = ["projectId", "dateFrom"], name = "index_receipts_projectId_dateFrom")])
data class ReceiptsLegacyEntity(
    @ColumnInfo(name = "projectId") val `projectId`: String,
    @ColumnInfo(name = "id") val `id`: String,
    @ColumnInfo(name = "dateFrom") val `dateFrom`: String,
    @ColumnInfo(name = "dateTo") val `dateTo`: String,
    @ColumnInfo(name = "amount") val `amount`: Double,
    @ColumnInfo(name = "description") val `description`: String,
    @ColumnInfo(name = "active") val `active`: Long,
    @ColumnInfo(name = "createdAt") val `createdAt`: Long,
    @ColumnInfo(name = "updatedAt") val `updatedAt`: Long,
    @ColumnInfo(name = "migration_dateFromIso", defaultValue = "''") val `migration_dateFromIso`: String,
    @ColumnInfo(name = "migration_dateToIso", defaultValue = "''") val `migration_dateToIso`: String,
    @ColumnInfo(name = "migration_amountToman", defaultValue = "0") val `migration_amountToman`: Long
)

@Entity(tableName = "payouts", primaryKeys = ["projectId", "id"], indices = [Index(value = ["projectId", "date"], name = "index_payouts_projectId_date")])
data class PayoutsLegacyEntity(
    @ColumnInfo(name = "projectId") val `projectId`: String,
    @ColumnInfo(name = "id") val `id`: String,
    @ColumnInfo(name = "date") val `date`: String,
    @ColumnInfo(name = "userId") val `userId`: String,
    @ColumnInfo(name = "amount") val `amount`: Double,
    @ColumnInfo(name = "description") val `description`: String,
    @ColumnInfo(name = "active") val `active`: Long,
    @ColumnInfo(name = "createdAt") val `createdAt`: Long,
    @ColumnInfo(name = "updatedAt") val `updatedAt`: Long,
    @ColumnInfo(name = "migration_dateIso", defaultValue = "''") val `migration_dateIso`: String,
    @ColumnInfo(name = "migration_amountToman", defaultValue = "0") val `migration_amountToman`: Long
)

@Entity(tableName = "transfers", primaryKeys = ["projectId", "id"], indices = [Index(value = ["projectId", "date"], name = "index_transfers_projectId_date")])
data class TransfersLegacyEntity(
    @ColumnInfo(name = "projectId") val `projectId`: String,
    @ColumnInfo(name = "id") val `id`: String,
    @ColumnInfo(name = "date") val `date`: String,
    @ColumnInfo(name = "fromId") val `fromId`: String?,
    @ColumnInfo(name = "toId") val `toId`: String?,
    @ColumnInfo(name = "amount") val `amount`: Double,
    @ColumnInfo(name = "description") val `description`: String,
    @ColumnInfo(name = "kind") val `kind`: String,
    @ColumnInfo(name = "active") val `active`: Long,
    @ColumnInfo(name = "createdAt") val `createdAt`: Long,
    @ColumnInfo(name = "updatedAt") val `updatedAt`: Long,
    @ColumnInfo(name = "migration_dateIso", defaultValue = "''") val `migration_dateIso`: String,
    @ColumnInfo(name = "migration_amountToman", defaultValue = "0") val `migration_amountToman`: Long
)

@Entity(tableName = "adjustments", primaryKeys = ["projectId", "id"], indices = [Index(value = ["projectId", "date"], name = "index_adjustments_projectId_date")])
data class AdjustmentsLegacyEntity(
    @ColumnInfo(name = "projectId") val `projectId`: String,
    @ColumnInfo(name = "id") val `id`: String,
    @ColumnInfo(name = "date") val `date`: String,
    @ColumnInfo(name = "userId") val `userId`: String,
    @ColumnInfo(name = "kind") val `kind`: String,
    @ColumnInfo(name = "amount") val `amount`: Double,
    @ColumnInfo(name = "description") val `description`: String,
    @ColumnInfo(name = "active") val `active`: Long,
    @ColumnInfo(name = "createdAt") val `createdAt`: Long,
    @ColumnInfo(name = "updatedAt") val `updatedAt`: Long,
    @ColumnInfo(name = "migration_dateIso", defaultValue = "''") val `migration_dateIso`: String,
    @ColumnInfo(name = "migration_amountToman", defaultValue = "0") val `migration_amountToman`: Long
)

@Entity(tableName = "categories", primaryKeys = ["projectId", "id"])
data class CategoriesLegacyEntity(
    @ColumnInfo(name = "projectId") val `projectId`: String,
    @ColumnInfo(name = "id") val `id`: String,
    @ColumnInfo(name = "name") val `name`: String
)
