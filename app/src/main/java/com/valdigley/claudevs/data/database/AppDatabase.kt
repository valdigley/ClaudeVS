package com.valdigley.claudevs.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "claudevs_db"
                )
                .fallbackToDestructiveMigration()
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
