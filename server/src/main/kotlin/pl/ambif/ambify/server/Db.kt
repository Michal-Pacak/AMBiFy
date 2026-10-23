package pl.ambif.ambify.server

import java.io.File
import java.sql.Connection
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object Db {
    /** Opens the SQLite file (creating it if needed) and applies pending migrations. */
    fun open(path: String): Database {
        File(path).absoluteFile.parentFile?.mkdirs()
        val database = Database.connect(
            url = "jdbc:sqlite:$path?foreign_keys=on&journal_mode=WAL&busy_timeout=5000",
            driver = "org.sqlite.JDBC",
            // SQLite supports only serializable transactions.
            databaseConfig = DatabaseConfig { defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE },
        )
        Migrations.apply(database)
        return database
    }

    fun isReachable(database: Database): Boolean = runCatching {
        transaction(database) { exec("SELECT 1") { } }
    }.isSuccess
}
