package com.valdigley.claudevs.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.valdigley.claudevs.data.model.SSHConnection
import com.valdigley.claudevs.data.model.CommandHistory
import com.valdigley.claudevs.data.model.DeveloperProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [SSHConnection::class, CommandHistory::class, DeveloperProfile::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun connectionDao(): ConnectionDao
    abstract fun historyDao(): HistoryDao
    abstract fun developerProfileDao(): DeveloperProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 5 to 6: Remove anthropicApiKey column from ssh_connections
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite doesn't support DROP COLUMN directly, so we need to recreate the table
                // 1. Create new table without anthropicApiKey
                db.execSQL("""
                    CREATE TABLE ssh_connections_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        host TEXT NOT NULL,
                        port INTEGER NOT NULL DEFAULT 22,
                        username TEXT NOT NULL,
                        password TEXT,
                        privateKey TEXT,
                        color TEXT NOT NULL DEFAULT '#4CAF50',
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        lastConnected INTEGER,
                        workingDirectory TEXT
                    )
                """.trimIndent())

                // 2. Copy data from old table (excluding anthropicApiKey)
                db.execSQL("""
                    INSERT INTO ssh_connections_new (id, name, host, port, username, password, privateKey, color, isDefault, lastConnected, workingDirectory)
                    SELECT id, name, host, port, username, password, privateKey, color, isDefault, lastConnected, workingDirectory
                    FROM ssh_connections
                """.trimIndent())

                // 3. Drop old table
                db.execSQL("DROP TABLE ssh_connections")

                // 4. Rename new table to original name
                db.execSQL("ALTER TABLE ssh_connections_new RENAME TO ssh_connections")
            }
        }

        // Migration from version 1 to 2 (placeholder for future reference)
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add workingDirectory column
                db.execSQL("ALTER TABLE ssh_connections ADD COLUMN workingDirectory TEXT")
            }
        }

        // Migration from version 2 to 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create command_history table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS command_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        command TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        connectionId INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        // Migration from version 3 to 4
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create developer_profile table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS developer_profile (
                        id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                        name TEXT NOT NULL DEFAULT '',
                        email TEXT NOT NULL DEFAULT '',
                        phone TEXT NOT NULL DEFAULT '',
                        whatsapp TEXT NOT NULL DEFAULT '',
                        ddd TEXT NOT NULL DEFAULT '',
                        city TEXT NOT NULL DEFAULT '',
                        state TEXT NOT NULL DEFAULT '',
                        country TEXT NOT NULL DEFAULT 'Brasil',
                        primaryColor TEXT NOT NULL DEFAULT '#4CAF50',
                        secondaryColor TEXT NOT NULL DEFAULT '#2196F3',
                        accentColor TEXT NOT NULL DEFAULT '#FF9800',
                        preferredFontFamily TEXT NOT NULL DEFAULT 'Inter',
                        preferredStyle TEXT NOT NULL DEFAULT 'modern',
                        company TEXT NOT NULL DEFAULT '',
                        website TEXT NOT NULL DEFAULT '',
                        github TEXT NOT NULL DEFAULT '',
                        linkedin TEXT NOT NULL DEFAULT '',
                        baseDomain TEXT NOT NULL DEFAULT '',
                        projectsBasePath TEXT NOT NULL DEFAULT '',
                        webServerType TEXT NOT NULL DEFAULT 'nginx',
                        sslEnabled INTEGER NOT NULL DEFAULT 1,
                        copyrightText TEXT NOT NULL DEFAULT '',
                        copyrightYear TEXT NOT NULL DEFAULT '',
                        customInstructions TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        // Migration from version 4 to 5
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add anthropicApiKey column (this was added in v5, removed in v6)
                db.execSQL("ALTER TABLE ssh_connections ADD COLUMN anthropicApiKey TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "claudevs_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .addCallback(DatabaseCallback())
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDatabase(database.connectionDao(), database.developerProfileDao())
                    }
                }
            }

            suspend fun populateDatabase(connectionDao: ConnectionDao, profileDao: DeveloperProfileDao) {
                // Conexões SSH pré-cadastradas
                val connections = listOf(
                    SSHConnection(
                        name = "Sites e Sistemas",
                        host = "147.93.182.205",
                        port = 22,
                        username = "root",
                        password = "*AUBfG@_aL2eV\$",
                        color = "#FF5722",
                        workingDirectory = "/var/www/sites/gestao_crm"
                    ),
                    SSHConnection(
                        name = "Estudos",
                        host = "86.48.18.182",
                        port = 22,
                        username = "root",
                        password = "~57Lr^^bJos",
                        color = "#2196F3"
                    ),
                    SSHConnection(
                        name = "Supabase",
                        host = "207.180.219.25",
                        port = 22,
                        username = "root",
                        password = "@160225Mht#",
                        color = "#4CAF50"
                    ),
                    SSHConnection(
                        name = "Finanças",
                        host = "72.60.1.224",
                        port = 22,
                        username = "root",
                        password = ",iU0s?Mm,/1k#Eh..ga4",
                        color = "#9C27B0"
                    )
                )
                connections.forEach { connectionDao.insertConnection(it) }

                // Perfil do desenvolvedor
                val profile = DeveloperProfile(
                    name = "Valdigley Santos",
                    country = "Brasil",
                    github = "valdigley",
                    projectsBasePath = "/var/www/sites",
                    webServerType = "nginx",
                    sslEnabled = true,
                    copyrightText = "Valdigley Santos",
                    copyrightYear = "2025"
                )
                profileDao.insertOrUpdate(profile)
            }
        }
    }
}
