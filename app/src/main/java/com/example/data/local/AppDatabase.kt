package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AutoClassificationRule
import com.example.data.model.CustomTripType
import com.example.data.model.ExpenseReceipt
import com.example.data.model.SavedPlace
import com.example.data.model.ServiceReminder
import com.example.data.model.Trip
import com.example.data.model.Vehicle

@Database(
    entities = [
        Trip::class,
        Vehicle::class,
        SavedPlace::class,
        CustomTripType::class,
        AutoClassificationRule::class,
        ExpenseReceipt::class,
        ServiceReminder::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun customTripTypeDao(): CustomTripTypeDao
    abstract fun autoClassificationRuleDao(): AutoClassificationRuleDao
    abstract fun expenseReceiptDao(): ExpenseReceiptDao
    abstract fun serviceReminderDao(): ServiceReminderDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN tripTypeId TEXT")
                db.execSQL("ALTER TABLE auto_classification_rules ADD COLUMN tripTypeId TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Ensure default custom_trip_types exist so string trip types can be mapped to IDs
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO custom_trip_types (id, name, colorHex, isTaxDeductible, customRatePerMile, iconName, isDefault) VALUES
                    ('type_1', 'Walmart Spark', '#0D9488', 1, 0.70, 'Briefcase', 1),
                    ('type_2', 'Instacart', '#EA580C', 1, 0.70, 'ShoppingCart', 0),
                    ('type_3', 'DoorDash', '#DC2626', 1, 0.70, 'Fastfood', 0),
                    ('type_4', 'UberEats', '#16A34A', 1, 0.70, 'Restaurant', 0),
                    ('type_5', 'Grubhub', '#BE123C', 1, 0.70, 'LocalDining', 0),
                    ('type_6', 'Lyft', '#DB2777', 1, 0.70, 'LocalTaxi', 0),
                    ('type_7', 'Personal', '#2563EB', 0, 0.00, 'Person', 0),
                    ('type_8', 'Medical', '#D97706', 1, 0.22, 'LocalHospital', 0),
                    ('type_9', 'Charity', '#7C3AED', 1, 0.14, 'VolunteerActivism', 0),
                    ('type_10', 'Commute', '#64748B', 0, 0.00, 'DirectionsCar', 0)
                    """.trimIndent()
                )

                // 2. Migrate auto_classification_rules string tripTypeName -> tripTypeId
                db.execSQL(
                    """
                    UPDATE auto_classification_rules
                    SET tripTypeId = (
                        SELECT id FROM custom_trip_types
                        WHERE LOWER(custom_trip_types.name) = LOWER(auto_classification_rules.tripTypeName)
                        LIMIT 1
                    )
                    WHERE tripTypeId IS NULL OR tripTypeId = ''
                    """.trimIndent()
                )

                // 3. Migrate trips string tags/title/purpose -> tripTypeId
                db.execSQL(
                    """
                    UPDATE trips
                    SET tripTypeId = (
                        SELECT id FROM custom_trip_types
                        WHERE LOWER(custom_trip_types.name) = LOWER(TRIM(trips.tags))
                           OR LOWER(trips.tags) LIKE '%' || LOWER(custom_trip_types.name) || '%'
                           OR LOWER(trips.title) LIKE '%' || LOWER(custom_trip_types.name) || '%'
                        LIMIT 1
                    )
                    WHERE tripTypeId IS NULL OR tripTypeId = ''
                    """.trimIndent()
                )
                db.execSQL("UPDATE trips SET tripTypeId = 'type_1' WHERE (tripTypeId IS NULL OR tripTypeId = '') AND (LOWER(tags) LIKE '%spark%' OR LOWER(title) LIKE '%spark%')")
                db.execSQL("UPDATE trips SET tripTypeId = 'type_2' WHERE (tripTypeId IS NULL OR tripTypeId = '') AND (LOWER(tags) LIKE '%instacart%' OR LOWER(title) LIKE '%instacart%')")
                db.execSQL("UPDATE trips SET tripTypeId = 'type_3' WHERE (tripTypeId IS NULL OR tripTypeId = '') AND (LOWER(tags) LIKE '%doordash%' OR LOWER(title) LIKE '%doordash%')")
                db.execSQL("UPDATE trips SET tripTypeId = 'type_4' WHERE (tripTypeId IS NULL OR tripTypeId = '') AND (LOWER(tags) LIKE '%ubereats%' OR LOWER(title) LIKE '%ubereats%')")
                db.execSQL("UPDATE trips SET tripTypeId = 'type_7' WHERE (tripTypeId IS NULL OR tripTypeId = '') AND purpose = 'PERSONAL'")
                db.execSQL("UPDATE trips SET tripTypeId = 'type_8' WHERE (tripTypeId IS NULL OR tripTypeId = '') AND purpose = 'MEDICAL'")
                db.execSQL("UPDATE trips SET tripTypeId = 'type_9' WHERE (tripTypeId IS NULL OR tripTypeId = '') AND purpose = 'CHARITY'")
                db.execSQL("UPDATE trips SET tripTypeId = 'type_10' WHERE (tripTypeId IS NULL OR tripTypeId = '') AND purpose = 'COMMUTE'")
                db.execSQL("UPDATE trips SET tripTypeId = 'type_1' WHERE (tripTypeId IS NULL OR tripTypeId = '') AND (purpose = 'BUSINESS' OR purpose = 'GIG_DELIVERY')")

                // 4. Normalize vehicle fuelType strings ("Electric EV" -> "Electric", "Plug-in Hybrid (PHEV)" -> "Plug-in Hybrid")
                db.execSQL("UPDATE vehicles SET fuelType = 'Electric' WHERE LOWER(fuelType) LIKE '%electric%' OR LOWER(fuelType) = 'ev' OR LOWER(fuelType) LIKE '% ev'")
                db.execSQL("UPDATE vehicles SET fuelType = 'Plug-in Hybrid' WHERE LOWER(fuelType) LIKE '%plug-in%' OR LOWER(fuelType) LIKE '%phev%'")

                // 5. Clean up service_reminders for EVs: NEVER keep engine oil, oil filter, transmission fluid, spark plugs, engine air filter, or engine coolant
                db.execSQL(
                    """
                    DELETE FROM service_reminders
                    WHERE vehicleId IN (
                        SELECT id FROM vehicles WHERE LOWER(fuelType) LIKE '%electric%' OR LOWER(fuelType) = 'ev'
                    )
                    AND (
                        LOWER(title) LIKE '%oil%'
                        OR LOWER(title) LIKE '%transmission%'
                        OR LOWER(title) LIKE '%gearbox%'
                        OR LOWER(title) LIKE '%spark%'
                        OR LOWER(title) LIKE '%engine%'
                        OR (LOWER(title) LIKE '%air filter%' AND LOWER(title) NOT LIKE '%cabin%')
                        OR (LOWER(title) LIKE '%coolant%' AND LOWER(title) NOT LIKE '%battery%' AND LOWER(title) NOT LIKE '%drive-unit%' AND LOWER(title) NOT LIKE '%high-voltage%')
                        OR LOWER(title) LIKE '%hybrid%'
                        OR LOWER(title) LIKE '%regenerative%'
                    )
                    """.trimIndent()
                )

                // 6. Clean up service_reminders for Gas vehicles: remove EV/Hybrid-only items
                db.execSQL(
                    """
                    DELETE FROM service_reminders
                    WHERE vehicleId IN (
                        SELECT id FROM vehicles WHERE LOWER(fuelType) = 'gas'
                    )
                    AND (
                        LOWER(title) LIKE '%high-voltage%'
                        OR LOWER(title) LIKE '%drive-unit%'
                        OR LOWER(title) LIKE '%charge port%'
                        OR LOWER(title) LIKE '%charging port%'
                        OR LOWER(title) LIKE '%hybrid%'
                        OR LOWER(title) LIKE '%inverter%'
                        OR LOWER(title) LIKE '%regenerative%'
                        OR LOWER(title) LIKE '%wiper%'
                        OR LOWER(title) LIKE '%suspension%'
                        OR LOWER(title) LIKE '%software%'
                    )
                    """.trimIndent()
                )

                // 7. Clean up service_reminders for Hybrid (non-PHEV) vehicles: remove EV-only and PHEV charging port items
                db.execSQL(
                    """
                    DELETE FROM service_reminders
                    WHERE vehicleId IN (
                        SELECT id FROM vehicles WHERE LOWER(fuelType) = 'hybrid'
                    )
                    AND (
                        LOWER(title) LIKE '%high-voltage%'
                        OR LOWER(title) LIKE '%drive-unit%'
                        OR LOWER(title) LIKE '%charge port%'
                        OR LOWER(title) LIKE '%charging port%'
                        OR LOWER(title) LIKE '%wiper%'
                        OR LOWER(title) LIKE '%suspension%'
                        OR LOWER(title) LIKE '%software%'
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "miletracker_database"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
