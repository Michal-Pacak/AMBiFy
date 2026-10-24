package pl.ambif.ambify.server

// Settings come from environment variables (set in the systemd unit on the home server).
data class ServerConfig(
    val host: String,
    val port: Int,
    val databasePath: String,
    val nasPath: String,
    val backupDir: String,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()) = ServerConfig(
            host = env["AMBIFY_HOST"] ?: "127.0.0.1",
            port = env["AMBIFY_PORT"]?.toInt() ?: 8080,
            databasePath = env["AMBIFY_DB"] ?: "data/ambify.db",
            nasPath = env["AMBIFY_NAS"] ?: "/mnt/nas",
            backupDir = env["AMBIFY_BACKUP_DIR"] ?: "/mnt/nas/Backup/serverek/ambify",
        )
    }
}
