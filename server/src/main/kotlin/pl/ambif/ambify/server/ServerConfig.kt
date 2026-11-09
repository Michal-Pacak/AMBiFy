package pl.ambif.ambify.server

// Settings come from environment variables (set in the systemd unit on the home server).
data class ServerConfig(
    val host: String,
    val port: Int,
    val databasePath: String,
    val nasPath: String,
    val backupDir: String,
    /** Where publish-app.sh puts the app for download; outside the program directory that deploy.sh replaces. */
    val releasesDir: String,
    /** Pliki logu zdarzeń telefonów dzieci (build kids); poza katalogiem programu, deploy.sh ich nie nadpisuje. */
    val monitorDir: String,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()) = ServerConfig(
            host = env["AMBIFY_HOST"] ?: "127.0.0.1",
            port = env["AMBIFY_PORT"]?.toInt() ?: 8080,
            databasePath = env["AMBIFY_DB"] ?: "data/ambify.db",
            nasPath = env["AMBIFY_NAS"] ?: "/mnt/nas",
            backupDir = env["AMBIFY_BACKUP_DIR"] ?: "/mnt/nas/Backup/serverek/ambify",
            releasesDir = env["AMBIFY_RELEASES"] ?: "/opt/ambify-server/releases",
            monitorDir = env["AMBIFY_MONITOR"] ?: "/opt/ambify-server/monitor",
        )
    }
}
