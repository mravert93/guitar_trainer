package com.ravert.guitar_trainer.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

object DatabaseFactory {
    fun init() {
        val cfg = dbConfigFromEnv()

        val config = HikariConfig().apply {
            jdbcUrl = cfg.jdbcUrl
            username = cfg.user
            password = cfg.password
            maximumPoolSize = (System.getenv("DB_MAX_POOL_SIZE") ?: "10").toInt()
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"

            // Helpful timeouts
            connectionTimeout = 10_000
            idleTimeout = 60_000
            maxLifetime = 30 * 60_000
        }

        val dataSource = HikariDataSource(config)
        Database.connect(dataSource)

        transaction {
            exec("CREATE EXTENSION IF NOT EXISTS pgcrypto")
            exec(
                """
                CREATE TABLE IF NOT EXISTS users (
                    uuid uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    email text NOT NULL UNIQUE,
                    password_hash text NOT NULL,
                    youtube_username text NULL,
                    normalized_youtube_username text NULL,
                    youtube_channel_id text NULL,
                    youtube_display_name text NULL,
                    normalized_youtube_display_name text NULL,
                    created_at bigint NOT NULL,
                    updated_at bigint NOT NULL
                )
                """.trimIndent()
            )
            exec("ALTER TABLE users ALTER COLUMN uuid SET DEFAULT gen_random_uuid()")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS email text NULL")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash text NULL")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS youtube_username text NULL")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS normalized_youtube_username text NULL")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS youtube_channel_id text NULL")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS youtube_display_name text NULL")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS normalized_youtube_display_name text NULL")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at bigint NULL")
            exec("ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at bigint NULL")
            exec("UPDATE users SET email = 'legacy-' || uuid::text || '@example.invalid' WHERE email IS NULL")
            exec("UPDATE users SET password_hash = 'legacy-unusable-password-hash' WHERE password_hash IS NULL")
            exec("UPDATE users SET created_at = 0 WHERE created_at IS NULL")
            exec("UPDATE users SET updated_at = created_at WHERE updated_at IS NULL")
            exec("ALTER TABLE users ALTER COLUMN email SET NOT NULL")
            exec("ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL")
            exec("ALTER TABLE users ALTER COLUMN created_at SET NOT NULL")
            exec("ALTER TABLE users ALTER COLUMN updated_at SET NOT NULL")
            exec(
                """
                CREATE TABLE IF NOT EXISTS user_sessions (
                    uuid uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_uuid uuid NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
                    session_token_hash text NOT NULL UNIQUE,
                    created_at bigint NOT NULL,
                    expires_at bigint NOT NULL,
                    revoked_at bigint NULL
                )
                """.trimIndent()
            )
            exec(
                """
                CREATE TABLE IF NOT EXISTS user_entitlements (
                    uuid uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_uuid uuid NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
                    source text NOT NULL,
                    status text NOT NULL,
                    starts_at bigint NOT NULL,
                    ends_at bigint NULL,
                    source_external_id text NULL,
                    source_label text NULL,
                    created_at bigint NOT NULL,
                    updated_at bigint NOT NULL
                )
                """.trimIndent()
            )
            exec(
                """
                CREATE TABLE IF NOT EXISTS stripe_customers (
                    uuid uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_uuid uuid NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
                    stripe_customer_id text NOT NULL UNIQUE,
                    stripe_subscription_id text NULL UNIQUE,
                    subscription_status text NULL,
                    current_period_end bigint NULL,
                    created_at bigint NOT NULL,
                    updated_at bigint NOT NULL
                )
                """.trimIndent()
            )
            exec(
                """
                CREATE TABLE IF NOT EXISTS stripe_webhook_events (
                    stripe_event_id text PRIMARY KEY,
                    event_type text NOT NULL,
                    processed_at bigint NOT NULL
                )
                """.trimIndent()
            )
            exec(
                """
                CREATE TABLE IF NOT EXISTS youtube_members (
                    uuid uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    youtube_channel_id text NULL,
                    display_name text NOT NULL,
                    normalized_display_name text NOT NULL,
                    profile_image_url text NULL,
                    membership_level_name text NULL,
                    member_since bigint NULL,
                    last_seen_at bigint NOT NULL,
                    created_at bigint NOT NULL,
                    updated_at bigint NOT NULL
                )
                """.trimIndent()
            )
            exec("CREATE INDEX IF NOT EXISTS idx_user_sessions_token_hash ON user_sessions(session_token_hash)")
            exec("CREATE INDEX IF NOT EXISTS idx_user_sessions_user_uuid ON user_sessions(user_uuid)")
            exec("CREATE INDEX IF NOT EXISTS idx_user_entitlements_user_uuid ON user_entitlements(user_uuid)")
            exec("CREATE INDEX IF NOT EXISTS idx_user_entitlements_status ON user_entitlements(status)")
            exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email)")
            exec("CREATE INDEX IF NOT EXISTS idx_users_youtube_channel_id ON users(youtube_channel_id)")
            exec("CREATE INDEX IF NOT EXISTS idx_users_normalized_youtube_username ON users(normalized_youtube_username)")
            exec("CREATE INDEX IF NOT EXISTS idx_users_normalized_youtube_display_name ON users(normalized_youtube_display_name)")
            exec("CREATE INDEX IF NOT EXISTS idx_stripe_customers_user_uuid ON stripe_customers(user_uuid)")
            exec("CREATE INDEX IF NOT EXISTS idx_stripe_customers_subscription_id ON stripe_customers(stripe_subscription_id)")
            exec(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_youtube_members_youtube_channel_id
                ON youtube_members(youtube_channel_id)
                WHERE youtube_channel_id IS NOT NULL
                """.trimIndent()
            )
            exec(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_youtube_members_normalized_display_name
                ON youtube_members(normalized_display_name)
                """.trimIndent()
            )

            SchemaUtils.createMissingTablesAndColumns(
                ArtistsTable,
                AlbumsTable,
                SongsTable,
                BetaFeedbackTable,
                UsersTable,
                UserSessionsTable,
                UserEntitlementsTable,
                StripeCustomersTable,
                StripeWebhookEventsTable,
                YoutubeMembersTable
            )
        }
    }
}

data class DbConfig(
    val jdbcUrl: String,
    val user: String,
    val password: String
)

fun dbConfigFromEnv(): DbConfig {
    val raw = System.getenv("DATABASE_URL")
        ?: "postgres://postgres:password@localhost:5432/dctutorials"

    if (raw.startsWith("postgres://") || raw.startsWith("postgresql://")) {
        // Parse libpq-style URL (postgres://user:pass@host:port/db)
        val uri = URI(raw)
        val userInfo = uri.userInfo.split(":")
        val rUsername = userInfo[0]
        val rPassword = userInfo.getOrElse(1) { "" }
        val host = uri.host
        val port = if (uri.port == -1) 5432 else uri.port
        val db = uri.path.trimStart('/')
        val jdbcAddedUrl = "jdbc:postgresql://$host:$port/$db"

        return DbConfig(jdbcAddedUrl, rUsername, rPassword)
    } else {
        val uri = URI(raw)
        val (user, pass) = (uri.userInfo ?: "").split(":", limit = 2).let {
            it[0] to (it.getOrNull(1) ?: "")
        }

        val jdbcUrl = buildString {
            append("jdbc:postgresql://")
            append(uri.host)
            if (uri.port != -1) append(":${uri.port}")
            append(uri.path) // includes /dbname
            // SSL flags (safe defaults; adjust if you know internal URL doesn’t need it)
            append("?sslmode=require")
        }

        return DbConfig(jdbcUrl, user, pass)
    }
}
