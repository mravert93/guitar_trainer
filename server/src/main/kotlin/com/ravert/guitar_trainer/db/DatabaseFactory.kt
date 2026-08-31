package com.ravert.guitar_trainer.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.net.URI

object DatabaseFactory {
    private const val TabMetadataMigrationId = "2026-07-24-bundled-lesson-tabs-metadata-v2"

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
                    membership_tier text NOT NULL DEFAULT 'premium',
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
            exec(
                """
                CREATE TABLE IF NOT EXISTS youtube_member_count_snapshots (
                    snapshot_date text PRIMARY KEY,
                    member_count integer NOT NULL,
                    captured_at bigint NOT NULL,
                    created_at bigint NOT NULL,
                    updated_at bigint NOT NULL
                )
                """.trimIndent()
            )
            exec("CREATE INDEX IF NOT EXISTS idx_user_sessions_token_hash ON user_sessions(session_token_hash)")
            exec("CREATE INDEX IF NOT EXISTS idx_user_sessions_user_uuid ON user_sessions(user_uuid)")
            exec("CREATE INDEX IF NOT EXISTS idx_user_entitlements_user_uuid ON user_entitlements(user_uuid)")
            exec("CREATE INDEX IF NOT EXISTS idx_user_entitlements_status ON user_entitlements(status)")
            exec("ALTER TABLE user_entitlements ADD COLUMN IF NOT EXISTS membership_tier text NOT NULL DEFAULT 'premium'")
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
                AppMigrationsTable,
                UsersTable,
                UserSessionsTable,
                UserEntitlementsTable,
                StripeCustomersTable,
                StripeWebhookEventsTable,
                YoutubeMembersTable,
                YoutubeMemberCountSnapshotsTable
            )
            exec("ALTER TABLE songs ADD COLUMN IF NOT EXISTS tuning text NULL")
            exec("ALTER TABLE songs ADD COLUMN IF NOT EXISTS capo text NULL")
            exec("ALTER TABLE songs ADD COLUMN IF NOT EXISTS chords text NULL")
            exec("ALTER TABLE songs ADD COLUMN IF NOT EXISTS technique text NULL")
            exec("ALTER TABLE songs ADD COLUMN IF NOT EXISTS created_at bigint NOT NULL DEFAULT 0")
            exec("ALTER TABLE songs ADD COLUMN IF NOT EXISTS updated_at bigint NOT NULL DEFAULT 0")
            exec("ALTER TABLE songs ADD COLUMN IF NOT EXISTS release_at bigint NULL")
            exec("ALTER TABLE songs ADD COLUMN IF NOT EXISTS cloudinary_video_public_id text NULL")
            exec("ALTER TABLE songs ADD COLUMN IF NOT EXISTS cloudinary_video_format text NULL")
            exec("ALTER TABLE songs ADD COLUMN IF NOT EXISTS cloudinary_video_version bigint NULL")
            exec("ALTER TABLE songs ADD COLUMN IF NOT EXISTS cloudinary_video_duration_seconds double precision NULL")
            exec("CREATE INDEX IF NOT EXISTS idx_songs_release_at ON songs(release_at)")
            val existingSongBackfillTime = System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)
            exec("UPDATE songs SET created_at = $existingSongBackfillTime WHERE created_at = 0")
            exec("UPDATE songs SET updated_at = created_at WHERE updated_at = 0")
            exec(
                """
                CREATE TABLE IF NOT EXISTS user_song_favorites (
                    uuid uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    user_uuid uuid NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
                    song_uuid uuid NOT NULL REFERENCES songs(id) ON DELETE CASCADE,
                    created_at bigint NOT NULL,
                    UNIQUE (user_uuid, song_uuid)
                )
                """.trimIndent()
            )
            exec("ALTER TABLE user_song_favorites ALTER COLUMN uuid SET DEFAULT gen_random_uuid()")
            exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_user_song_favorites_user_song ON user_song_favorites(user_uuid, song_uuid)")
            exec("CREATE INDEX IF NOT EXISTS idx_user_song_favorites_user_uuid ON user_song_favorites(user_uuid)")
            exec("CREATE INDEX IF NOT EXISTS idx_user_song_favorites_song_uuid ON user_song_favorites(song_uuid)")
            exec(
                """
                CREATE TABLE IF NOT EXISTS song_tab_requests (
                    uuid uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    requested_by_user_uuid uuid NULL REFERENCES users(uuid) ON DELETE SET NULL,
                    artist_name text NOT NULL,
                    song_name text NOT NULL,
                    normalized_artist_name text NOT NULL,
                    normalized_song_name text NOT NULL,
                    details text NULL,
                    status text NOT NULL,
                    completed_song_uuid uuid NULL REFERENCES songs(id) ON DELETE SET NULL,
                    created_at bigint NOT NULL,
                    updated_at bigint NOT NULL,
                    completed_at bigint NULL,
                    CONSTRAINT chk_song_tab_requests_status
                        CHECK (status IN ('requested', 'in_progress', 'completed')),
                    UNIQUE (normalized_artist_name, normalized_song_name)
                )
                """.trimIndent()
            )
            exec(
                """
                CREATE TABLE IF NOT EXISTS song_tab_request_votes (
                    uuid uuid PRIMARY KEY DEFAULT gen_random_uuid(),
                    request_uuid uuid NOT NULL REFERENCES song_tab_requests(uuid) ON DELETE CASCADE,
                    user_uuid uuid NOT NULL REFERENCES users(uuid) ON DELETE CASCADE,
                    created_at bigint NOT NULL,
                    UNIQUE (request_uuid, user_uuid)
                )
                """.trimIndent()
            )
            exec("ALTER TABLE song_tab_requests ALTER COLUMN uuid SET DEFAULT gen_random_uuid()")
            exec("ALTER TABLE song_tab_request_votes ALTER COLUMN uuid SET DEFAULT gen_random_uuid()")
            exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_song_tab_requests_normalized_names ON song_tab_requests(normalized_artist_name, normalized_song_name)")
            exec("CREATE INDEX IF NOT EXISTS idx_song_tab_requests_status ON song_tab_requests(status)")
            exec("CREATE INDEX IF NOT EXISTS idx_song_tab_requests_created_at ON song_tab_requests(created_at)")
            exec("CREATE UNIQUE INDEX IF NOT EXISTS idx_song_tab_request_votes_request_user ON song_tab_request_votes(request_uuid, user_uuid)")
            exec("CREATE INDEX IF NOT EXISTS idx_song_tab_request_votes_request_uuid ON song_tab_request_votes(request_uuid)")
            exec("CREATE INDEX IF NOT EXISTS idx_song_tab_request_votes_user_uuid ON song_tab_request_votes(user_uuid)")
            applyBundledTabMetadataMigration()
        }
    }

    private fun applyBundledTabMetadataMigration() {
        val alreadyApplied = !AppMigrationsTable
            .selectAll()
            .where { AppMigrationsTable.id eq TabMetadataMigrationId }
            .empty()
        if (alreadyApplied) return

        val csvText = Thread.currentThread().contextClassLoader
            .getResourceAsStream("lesson-tabs-metadata.csv")
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error("Bundled lesson-tabs-metadata.csv resource was not found")

        val rows = parseCsv(csvText, initialDrop = 0)
        require(rows.isNotEmpty()) { "Bundled lesson-tabs-metadata.csv is empty" }

        val header = rows.first().map { it.trim() }
        fun idx(name: String): Int = header.indexOfFirst { it.equals(name, ignoreCase = true) }

        val artistIdx = idx("Artist")
        val songIdx = idx("Song")
        val tuningIdx = idx("Tuning")
        val capoIdx = idx("Capo")
        val chordsIdx = idx("Chords")
        val techniqueIdx = idx("Technique")
        require(artistIdx >= 0 && songIdx >= 0 && tuningIdx >= 0 && capoIdx >= 0 && chordsIdx >= 0 && techniqueIdx >= 0) {
            "Bundled lesson-tabs-metadata.csv must include Artist, Song, Tuning, Capo, Chords, Technique. Found: $header"
        }

        val artistsByName = ArtistsTable
            .selectAll()
            .associate { row -> row[ArtistsTable.name].normalizeArtistLookupText() to row[ArtistsTable.id] }

        val songsByArtist = SongsTable
            .selectAll()
            .groupBy { row -> row[SongsTable.artistId] }
        val songsByName = SongsTable
            .selectAll()
            .groupBy { row -> row[SongsTable.name].normalizeLookupText() }

        var updatedSongs = 0
        var skippedRows = 0
        var missingSongs = 0
        val missingRows = mutableListOf<String>()

        rows.drop(1).forEach { row ->
            val artistName = row.getOrNull(artistIdx)?.trim().orEmpty()
            val songName = row.getOrNull(songIdx)?.trim().orEmpty()
            if (artistName.isBlank() || songName.isBlank()) {
                skippedRows++
                return@forEach
            }

            val artistId = artistsByName[artistName.normalizeArtistLookupText()]
            val songRow = findTabMetadataSongRow(
                songName = songName,
                artistId = artistId,
                songsByArtist = songsByArtist,
                songsByName = songsByName,
            )

            if (songRow == null) {
                missingSongs++
                if (missingRows.size < 20) missingRows += "$artistName - $songName"
                return@forEach
            }

            updatedSongs += SongsTable.update({ SongsTable.id eq songRow[SongsTable.id] }) {
                it[tuning] = row.getOrNull(tuningIdx).normalizeOptionalText()
                it[capo] = row.getOrNull(capoIdx).normalizeOptionalText()
                it[chords] = row.getOrNull(chordsIdx).normalizeOptionalText()
                it[technique] = row.getOrNull(techniqueIdx).normalizeOptionalText()
            }
        }

        AppMigrationsTable.insert {
            it[id] = TabMetadataMigrationId
            it[appliedAt] = System.currentTimeMillis()
        }

        println(
            "Applied $TabMetadataMigrationId: updatedSongs=$updatedSongs, " +
                "skippedRows=$skippedRows, missingSongs=$missingSongs, missingRows=${missingRows.joinToString("; ")}"
        )
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

private fun parseCsv(csv: String, initialDrop: Int): List<List<String>> {
    val lines = csv.split("\n")
        .drop(initialDrop)
        .map { it.trimEnd('\r') }
        .filter { it.isNotBlank() }

    return lines.map { line ->
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    out += sb.toString()
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        out
    }
}

private fun String?.normalizeOptionalText(): String? =
    this
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun findTabMetadataSongRow(
    songName: String,
    artistId: java.util.UUID?,
    songsByArtist: Map<java.util.UUID, List<org.jetbrains.exposed.sql.ResultRow>>,
    songsByName: Map<String, List<org.jetbrains.exposed.sql.ResultRow>>,
): org.jetbrains.exposed.sql.ResultRow? {
    val normalizedSongName = songName.normalizeLookupText()
    val artistMatch = artistId?.let {
        songsByArtist[it]
            ?.firstOrNull { song -> song[SongsTable.name].normalizeLookupText() == normalizedSongName }
    }
    if (artistMatch != null) return artistMatch

    return songsByName[normalizedSongName]
        ?.singleOrNull()
}

private fun String.normalizeLookupText(): String =
    trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")

private fun String.normalizeArtistLookupText(): String =
    normalizeLookupText()
        .replace("&", "and")
        .replace(Regex("\\by\\b"), "and")
        .replace(Regex("\\s+"), " ")
