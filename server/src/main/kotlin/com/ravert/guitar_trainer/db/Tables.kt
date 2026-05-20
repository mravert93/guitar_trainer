package com.ravert.guitar_trainer.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ReferenceOption

object ArtistsTable : Table("artists") {
    val id = uuid("id")
    val name = varchar("name", 255)
    val imageUrl = text("image_url").nullable()

    override val primaryKey = PrimaryKey(id)
}

object AlbumsTable : Table("albums") {
    val id = uuid("id")
    val artistId = reference("artist_id", ArtistsTable.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val imageUrl = text("image_url").nullable()

    override val primaryKey = PrimaryKey(id)
}

object SongsTable : Table("songs") {
    val id = uuid("id")
    val artistId = reference("artist_id", ArtistsTable.id, onDelete = ReferenceOption.CASCADE)
    val albumId = reference("album_id", AlbumsTable.id, onDelete = ReferenceOption.SET_NULL)
    val name = varchar("name", 255)
    val lengthSeconds = integer("length_seconds")
    val bpm = integer("bpm")
    val docUrl = text("doc_url")

    override val primaryKey = PrimaryKey(id)
}

object BetaFeedbackTable : Table("beta_feedback") {
    val id = uuid("id")
    val created_at = long("created_at")
    val email = varchar("email", 255).nullable()
    val rating = varchar("rating", 255)
    val favorite_part = varchar("favorite_part", 255)
    val bug_report = varchar("bug_report", 255)
    val feature_request = varchar("feature_request", 255)
    val general_feedback = varchar("general_feedback", 255)
}

object UsersTable : Table("users") {
    val uuid = uuid("uuid")
    val email = text("email")
    val passwordHash = text("password_hash")
    val youtubeUsername = text("youtube_username").nullable()
    val normalizedYoutubeUsername = text("normalized_youtube_username").nullable()
    val youtubeChannelId = text("youtube_channel_id").nullable()
    val youtubeDisplayName = text("youtube_display_name").nullable()
    val normalizedYoutubeDisplayName = text("normalized_youtube_display_name").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(uuid)
}

object UserSessionsTable : Table("user_sessions") {
    val uuid = uuid("uuid")
    val userUuid = reference("user_uuid", UsersTable.uuid, onDelete = ReferenceOption.CASCADE)
    val sessionTokenHash = text("session_token_hash")
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    val revokedAt = long("revoked_at").nullable()

    override val primaryKey = PrimaryKey(uuid)
}

object UserEntitlementsTable : Table("user_entitlements") {
    val uuid = uuid("uuid")
    val userUuid = reference("user_uuid", UsersTable.uuid, onDelete = ReferenceOption.CASCADE)
    val sourceValue = text("source")
    val status = text("status")
    val startsAt = long("starts_at")
    val endsAt = long("ends_at").nullable()
    val sourceExternalId = text("source_external_id").nullable()
    val sourceLabel = text("source_label").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(uuid)
}

object StripeCustomersTable : Table("stripe_customers") {
    val uuid = uuid("uuid")
    val userUuid = reference("user_uuid", UsersTable.uuid, onDelete = ReferenceOption.CASCADE)
    val stripeCustomerId = text("stripe_customer_id")
    val stripeSubscriptionId = text("stripe_subscription_id").nullable()
    val subscriptionStatus = text("subscription_status").nullable()
    val currentPeriodEnd = long("current_period_end").nullable()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(uuid)
}

object StripeWebhookEventsTable : Table("stripe_webhook_events") {
    val stripeEventId = text("stripe_event_id")
    val eventType = text("event_type")
    val processedAt = long("processed_at")

    override val primaryKey = PrimaryKey(stripeEventId)
}

object YoutubeMembersTable : Table("youtube_members") {
    val uuid = uuid("uuid")
    val youtubeChannelId = text("youtube_channel_id").nullable()
    val displayName = text("display_name")
    val normalizedDisplayName = text("normalized_display_name")
    val profileImageUrl = text("profile_image_url").nullable()
    val membershipLevelName = text("membership_level_name").nullable()
    val memberSince = long("member_since").nullable()
    val lastSeenAt = long("last_seen_at")
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(uuid)
}
