package pl.ambif.ambify.server

import java.sql.DriverManager
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Applies SQL files from resources/db/migration in order, each one once.
 * A new migration = a new file + its name added to [FILES]; applied files are never edited.
 */
object Migrations {
    val FILES = listOf(
        "V001__init.sql",
        "V002__accounts.sql",
        "V003__communities.sql",
        "V004__avatars.sql",
    )

    /** First line of a file that rebuilds a table: it runs with foreign keys off, checked before commit. */
    private const val FOREIGN_KEYS_OFF = "-- foreign_keys=off"

    /** [files] is for tests that build an older database first. */
    fun apply(database: Database, files: List<String> = FILES) {
        transaction(database) {
            exec(
                "CREATE TABLE IF NOT EXISTS schema_version (" +
                    "name TEXT PRIMARY KEY, applied_at TEXT NOT NULL DEFAULT (datetime('now')))"
            )
        }
        val applied = appliedNames(database)
        for (name in files.filterNot { it in applied }) {
            val sql = Migrations::class.java.getResource("/db/migration/$name")?.readText()
                ?: error("Missing migration file $name")
            if (sql.startsWith(FOREIGN_KEYS_OFF)) {
                applyWithoutForeignKeys(database, name, sql)
                continue
            }
            // One transaction per file: a failing migration leaves the database unchanged.
            transaction(database) {
                statements(sql).forEach { exec(it) }
                exec("INSERT INTO schema_version(name) VALUES ('$name')")
            }
        }
    }

    fun appliedNames(database: Database): Set<String> = transaction(database) {
        exec("SELECT name FROM schema_version") { rs ->
            buildSet { while (rs.next()) add(rs.getString(1)) }
        } ?: emptySet()
    }

    /**
     * SQLite ignores `PRAGMA foreign_keys` inside a transaction, so this file gets its own connection:
     * keys off, one transaction, then `foreign_key_check` must find nothing or everything is rolled back.
     */
    private fun applyWithoutForeignKeys(database: Database, name: String, sql: String) {
        DriverManager.getConnection(database.url).use { connection ->
            connection.createStatement().use { it.execute("PRAGMA foreign_keys = OFF") }
            connection.autoCommit = false
            try {
                connection.createStatement().use { statement ->
                    statements(sql).forEach { statement.execute(it) }
                    statement.executeQuery("PRAGMA foreign_key_check").use { broken ->
                        check(!broken.next()) { "Migration $name breaks a foreign key in table ${broken.getString(1)}" }
                    }
                    statement.execute("INSERT INTO schema_version(name) VALUES ('$name')")
                }
                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            }
        }
    }

    // The SQLite driver runs one statement per call; migrations use ";" only at line ends.
    private fun statements(sql: String): List<String> =
        sql.lines()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(Regex(";\\s*(\\n|$)"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
