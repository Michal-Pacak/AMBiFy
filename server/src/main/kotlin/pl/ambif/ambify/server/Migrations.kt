package pl.ambif.ambify.server

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Applies SQL files from resources/db/migration in order, each one once.
 * A new migration = a new file + its name added to [FILES]; applied files are never edited.
 */
object Migrations {
    val FILES = listOf(
        "V001__init.sql",
    )

    fun apply(database: Database) {
        transaction(database) {
            exec(
                "CREATE TABLE IF NOT EXISTS schema_version (" +
                    "name TEXT PRIMARY KEY, applied_at TEXT NOT NULL DEFAULT (datetime('now')))"
            )
        }
        val applied = appliedNames(database)
        for (name in FILES.filterNot { it in applied }) {
            val sql = Migrations::class.java.getResource("/db/migration/$name")?.readText()
                ?: error("Missing migration file $name")
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

    // The SQLite driver runs one statement per call; migrations use ";" only at line ends.
    private fun statements(sql: String): List<String> =
        sql.lines()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(Regex(";\\s*(\\n|$)"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
