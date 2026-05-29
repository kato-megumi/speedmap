package com.example.speedometer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Trip::class, LocationPoint::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun locationPointDao(): LocationPointDao

    companion object {
        // v1 → v2: make `altitude` nullable so missing GPS altitudes are
        // stored as NULL instead of falsely written as 0.0 (sea level).
        // SQLite can't ALTER a column's NOT NULL constraint in place, so
        // we recreate the table.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `location_points_new` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `tripId` INTEGER NOT NULL,
                      `timestamp` INTEGER NOT NULL,
                      `latitude` REAL NOT NULL,
                      `longitude` REAL NOT NULL,
                      `altitude` REAL,
                      `speedMps` REAL NOT NULL,
                      `bearing` REAL NOT NULL,
                      `accuracy` REAL NOT NULL,
                      FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `location_points_new`
                      (id, tripId, timestamp, latitude, longitude, altitude, speedMps, bearing, accuracy)
                    SELECT id, tripId, timestamp, latitude, longitude, altitude, speedMps, bearing, accuracy
                    FROM `location_points`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `location_points`")
                db.execSQL("ALTER TABLE `location_points_new` RENAME TO `location_points`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_location_points_tripId` ON `location_points` (`tripId`)")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "speedometer.db"
                ).addMigrations(MIGRATION_1_2)
                    .build().also { INSTANCE = it }
            }
    }
}
