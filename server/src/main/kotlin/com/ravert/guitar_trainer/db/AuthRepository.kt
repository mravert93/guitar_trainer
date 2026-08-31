package com.ravert.guitar_trainer.db

import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

data class UserRecord(
    val uuid: UUID,
    val email: String,
    val passwordHash: String,
    val youtubeUsername: String?,
    val normalizedYoutubeUsername: String?,
    val youtubeChannelId: String?,
    val youtubeDisplayName: String?,
    val normalizedYoutubeDisplayName: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class StripeCustomerRecord(
    val userUuid: UUID,
    val stripeCustomerId: String,
    val stripeSubscriptionId: String?,
    val subscriptionStatus: String?,
    val currentPeriodEnd: Long?,
)

data class YoutubeMemberRecord(
    val youtubeChannelId: String?,
    val displayName: String,
    val normalizedDisplayName: String,
    val profileImageUrl: String?,
    val membershipLevelName: String?,
    val memberSince: Long?,
    val lastSeenAt: Long? = null,
)

data class YoutubeMemberCountSnapshotRecord(
    val snapshotDate: String,
    val memberCount: Int,
    val capturedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

data class UserEntitlementRecord(
    val uuid: UUID,
    val userUuid: UUID,
    val source: String,
    val status: String,
    val startsAt: Long,
    val endsAt: Long?,
    val sourceExternalId: String?,
    val sourceLabel: String?,
    val membershipTier: String,
    val createdAt: Long,
    val updatedAt: Long,
)

class AuthRepository {
    fun createUser(
        email: String,
        passwordHash: String,
        youtubeUsername: String?,
        normalizedYoutubeUsername: String?,
        youtubeChannelId: String?,
        now: Long,
    ): UserRecord = transaction {
        val uuid = UUID.randomUUID()
        UsersTable.insert {
            it[UsersTable.uuid] = uuid
            it[UsersTable.email] = email
            it[UsersTable.passwordHash] = passwordHash
            it[UsersTable.youtubeUsername] = youtubeUsername
            it[UsersTable.normalizedYoutubeUsername] = normalizedYoutubeUsername
            it[UsersTable.youtubeChannelId] = youtubeChannelId
            it[UsersTable.youtubeDisplayName] = null
            it[UsersTable.normalizedYoutubeDisplayName] = null
            it[createdAt] = now
            it[updatedAt] = now
        }
        UserRecord(uuid, email, passwordHash, youtubeUsername, normalizedYoutubeUsername, youtubeChannelId, null, null, now, now)
    }

    fun findUserByEmail(email: String): UserRecord? = transaction {
        UsersTable
            .selectAll()
            .where { UsersTable.email eq email }
            .singleOrNull()
            ?.toUserRecord()
    }

    fun findUserByUuid(uuid: UUID): UserRecord? = transaction {
        UsersTable
            .selectAll()
            .where { UsersTable.uuid eq uuid }
            .singleOrNull()
            ?.toUserRecord()
    }

    fun listUsers(query: String?): List<UserRecord> = transaction {
        val normalizedQuery = query
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }

        UsersTable
            .selectAll()
            .orderBy(UsersTable.createdAt to SortOrder.DESC)
            .map { it.toUserRecord() }
            .filter { user ->
                normalizedQuery == null ||
                    user.email.lowercase().contains(normalizedQuery) ||
                    user.youtubeUsername?.lowercase()?.contains(normalizedQuery) == true ||
                    user.youtubeChannelId?.lowercase()?.contains(normalizedQuery) == true
            }
    }

    fun createSession(userUuid: UUID, sessionTokenHash: String, createdAt: Long, expiresAt: Long) = transaction {
        UserSessionsTable.insert {
            it[uuid] = UUID.randomUUID()
            it[UserSessionsTable.userUuid] = userUuid
            it[UserSessionsTable.sessionTokenHash] = sessionTokenHash
            it[UserSessionsTable.createdAt] = createdAt
            it[UserSessionsTable.expiresAt] = expiresAt
            it[revokedAt] = null
        }
    }

    fun findUserBySessionTokenHash(sessionTokenHash: String, now: Long): UserRecord? = transaction {
        UserSessionsTable
            .join(UsersTable, JoinType.INNER, UserSessionsTable.userUuid, UsersTable.uuid)
            .selectAll()
            .where {
                (UserSessionsTable.sessionTokenHash eq sessionTokenHash) and
                    UserSessionsTable.revokedAt.isNull() and
                    (UserSessionsTable.expiresAt greater now)
            }
            .orderBy(UserSessionsTable.createdAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toUserRecord()
    }

    fun revokeSessionByTokenHash(sessionTokenHash: String, now: Long): Int = transaction {
        UserSessionsTable.update({
            (UserSessionsTable.sessionTokenHash eq sessionTokenHash) and UserSessionsTable.revokedAt.isNull()
        }) {
            it[revokedAt] = now
        }
    }

    suspend fun userHasPremium(userUuid: UUID): Boolean = transaction {
        val now = System.currentTimeMillis()
        !UserEntitlementsTable
            .selectAll()
            .where {
                (UserEntitlementsTable.userUuid eq userUuid) and
                    (UserEntitlementsTable.status eq "active") and
                    (UserEntitlementsTable.startsAt lessEq now) and
                    (UserEntitlementsTable.endsAt.isNull() or (UserEntitlementsTable.endsAt greater now))
            }
            .limit(1)
            .empty()
    }

    suspend fun userMembershipTier(userUuid: UUID): MembershipTier? = transaction {
        val now = System.currentTimeMillis()
        val activeTiers = UserEntitlementsTable
            .selectAll()
            .where {
                (UserEntitlementsTable.userUuid eq userUuid) and
                    (UserEntitlementsTable.status eq "active") and
                    (UserEntitlementsTable.startsAt lessEq now) and
                    (UserEntitlementsTable.endsAt.isNull() or (UserEntitlementsTable.endsAt greater now))
            }
            .mapNotNull { MembershipTier.fromApiValue(it[UserEntitlementsTable.membershipTier]) }

        when {
            MembershipTier.PREMIUM_PLUS in activeTiers -> MembershipTier.PREMIUM_PLUS
            activeTiers.isNotEmpty() -> MembershipTier.PREMIUM
            else -> null
        }
    }

    fun findEntitlementsByUserUuid(userUuid: UUID): List<UserEntitlementRecord> = transaction {
        UserEntitlementsTable
            .selectAll()
            .where { UserEntitlementsTable.userUuid eq userUuid }
            .orderBy(UserEntitlementsTable.createdAt to SortOrder.DESC)
            .map { it.toUserEntitlementRecord() }
    }

    fun grantManualPremium(userUuid: UUID, sourceLabel: String?, endsAt: Long?, now: Long) = transaction {
        UserEntitlementsTable.insert {
            it[uuid] = UUID.randomUUID()
            it[UserEntitlementsTable.userUuid] = userUuid
            it[sourceValue] = "manual"
            it[status] = "active"
            it[startsAt] = now
            it[UserEntitlementsTable.endsAt] = endsAt
            it[sourceExternalId] = null
            it[UserEntitlementsTable.sourceLabel] = sourceLabel
            it[membershipTier] = MembershipTier.PREMIUM.apiValue
            it[createdAt] = now
            it[updatedAt] = now
        }
    }

    fun findStripeCustomerByUserUuid(userUuid: UUID): StripeCustomerRecord? = transaction {
        StripeCustomersTable
            .selectAll()
            .where { StripeCustomersTable.userUuid eq userUuid }
            .singleOrNull()
            ?.toStripeCustomerRecord()
    }

    fun findStripeCustomerByCustomerId(stripeCustomerId: String): StripeCustomerRecord? = transaction {
        StripeCustomersTable
            .selectAll()
            .where { StripeCustomersTable.stripeCustomerId eq stripeCustomerId }
            .singleOrNull()
            ?.toStripeCustomerRecord()
    }

    fun findStripeCustomerBySubscriptionId(stripeSubscriptionId: String): StripeCustomerRecord? = transaction {
        StripeCustomersTable
            .selectAll()
            .where { StripeCustomersTable.stripeSubscriptionId eq stripeSubscriptionId }
            .singleOrNull()
            ?.toStripeCustomerRecord()
    }

    fun upsertStripeCustomer(
        userUuid: UUID,
        stripeCustomerId: String,
        stripeSubscriptionId: String?,
        subscriptionStatus: String?,
        currentPeriodEnd: Long?,
        now: Long,
    ) = transaction {
        val existing = StripeCustomersTable
            .selectAll()
            .where { StripeCustomersTable.stripeCustomerId eq stripeCustomerId }
            .singleOrNull()

        if (existing == null) {
            StripeCustomersTable.insert {
                it[uuid] = UUID.randomUUID()
                it[StripeCustomersTable.userUuid] = userUuid
                it[StripeCustomersTable.stripeCustomerId] = stripeCustomerId
                it[StripeCustomersTable.stripeSubscriptionId] = stripeSubscriptionId
                it[StripeCustomersTable.subscriptionStatus] = subscriptionStatus
                it[StripeCustomersTable.currentPeriodEnd] = currentPeriodEnd
                it[createdAt] = now
                it[updatedAt] = now
            }
        } else {
            StripeCustomersTable.update({ StripeCustomersTable.stripeCustomerId eq stripeCustomerId }) {
                it[StripeCustomersTable.userUuid] = userUuid
                it[StripeCustomersTable.stripeSubscriptionId] =
                    stripeSubscriptionId ?: existing[StripeCustomersTable.stripeSubscriptionId]
                it[StripeCustomersTable.subscriptionStatus] =
                    subscriptionStatus ?: existing[StripeCustomersTable.subscriptionStatus]
                it[StripeCustomersTable.currentPeriodEnd] =
                    currentPeriodEnd ?: existing[StripeCustomersTable.currentPeriodEnd]
                it[updatedAt] = now
            }
        }
    }

    fun hasProcessedStripeWebhookEvent(stripeEventId: String): Boolean = transaction {
        !StripeWebhookEventsTable
            .selectAll()
            .where { StripeWebhookEventsTable.stripeEventId eq stripeEventId }
            .empty()
    }

    fun recordStripeWebhookEvent(stripeEventId: String, eventType: String, processedAt: Long) = transaction {
        StripeWebhookEventsTable.insertIgnore {
            it[StripeWebhookEventsTable.stripeEventId] = stripeEventId
            it[StripeWebhookEventsTable.eventType] = eventType
            it[StripeWebhookEventsTable.processedAt] = processedAt
        }
    }

    fun activateStripePremium(
        userUuid: UUID,
        stripeSubscriptionId: String,
        subscriptionStatus: String?,
        currentPeriodEnd: Long?,
        membershipTier: MembershipTier,
        now: Long,
    ) = transaction {
        upsertEntitlement(
            userUuid = userUuid,
            source = "stripe",
            sourceExternalId = stripeSubscriptionId,
            status = "active",
            startsAt = now,
            endsAt = currentPeriodEnd,
            sourceLabel = subscriptionStatus,
            membershipTier = membershipTier,
            now = now,
        )
    }

    fun keepStripePremiumUntilCurrentPeriodEnd(
        userUuid: UUID,
        stripeSubscriptionId: String,
        subscriptionStatus: String?,
        currentPeriodEnd: Long?,
        membershipTier: MembershipTier,
        now: Long,
    ) = transaction {
        val existing = findEntitlement("stripe", stripeSubscriptionId)
        if (existing == null) {
            upsertEntitlement(
                userUuid = userUuid,
                source = "stripe",
                sourceExternalId = stripeSubscriptionId,
                status = "active",
                startsAt = now,
                endsAt = currentPeriodEnd,
                sourceLabel = subscriptionStatus,
                membershipTier = membershipTier,
                now = now,
            )
        } else {
            UserEntitlementsTable.update({ UserEntitlementsTable.uuid eq existing[UserEntitlementsTable.uuid] }) {
                it[status] = "active"
                it[sourceLabel] = subscriptionStatus
                it[UserEntitlementsTable.membershipTier] = membershipTier.apiValue
                it[updatedAt] = now
            }
        }
    }

    fun deactivateStripePremium(stripeSubscriptionId: String, now: Long): Int = transaction {
        UserEntitlementsTable.update({
            (UserEntitlementsTable.sourceValue eq "stripe") and
                (UserEntitlementsTable.sourceExternalId eq stripeSubscriptionId) and
                (UserEntitlementsTable.status eq "active")
        }) {
            it[status] = "inactive"
            it[updatedAt] = now
        }
    }

    fun updateUserYoutubeUsername(
        userUuid: UUID,
        username: String?,
        normalizedUsername: String?,
        youtubeChannelId: String?,
        now: Long,
    ): UserRecord? =
        transaction {
            UsersTable.update({ UsersTable.uuid eq userUuid }) {
                it[youtubeUsername] = username
                it[normalizedYoutubeUsername] = normalizedUsername
                it[UsersTable.youtubeChannelId] = youtubeChannelId
                it[updatedAt] = now
            }
            UsersTable
                .selectAll()
                .where { UsersTable.uuid eq userUuid }
                .singleOrNull()
                ?.toUserRecord()
        }

    fun updateUserPasswordHash(userUuid: UUID, passwordHash: String, now: Long): Int = transaction {
        UsersTable.update({ UsersTable.uuid eq userUuid }) {
            it[UsersTable.passwordHash] = passwordHash
            it[updatedAt] = now
        }
    }

    fun findYoutubeMemberByNormalizedName(normalizedDisplayName: String): YoutubeMemberRecord? = transaction {
        YoutubeMembersTable
            .selectAll()
            .where { YoutubeMembersTable.normalizedDisplayName eq normalizedDisplayName }
            .singleOrNull()
            ?.toYoutubeMemberRecord()
    }

    fun findYoutubeMemberByChannelId(youtubeChannelId: String): YoutubeMemberRecord? = transaction {
        YoutubeMembersTable
            .selectAll()
            .where { YoutubeMembersTable.youtubeChannelId eq youtubeChannelId }
            .singleOrNull()
            ?.toYoutubeMemberRecord()
    }

    fun upsertYoutubeMembers(members: List<YoutubeMemberRecord>, now: Long) = transaction {
        members.forEach { member ->
            val existing = if (member.youtubeChannelId != null) {
                YoutubeMembersTable
                    .selectAll()
                    .where { YoutubeMembersTable.youtubeChannelId eq member.youtubeChannelId }
                    .singleOrNull()
            } else {
                YoutubeMembersTable
                    .selectAll()
                    .where { YoutubeMembersTable.normalizedDisplayName eq member.normalizedDisplayName }
                    .singleOrNull()
            }

            if (existing == null) {
                YoutubeMembersTable.insert {
                    it[uuid] = UUID.randomUUID()
                    it[YoutubeMembersTable.youtubeChannelId] = member.youtubeChannelId
                    it[displayName] = member.displayName
                    it[normalizedDisplayName] = member.normalizedDisplayName
                    it[profileImageUrl] = member.profileImageUrl
                    it[membershipLevelName] = member.membershipLevelName
                    it[memberSince] = member.memberSince
                    it[lastSeenAt] = now
                    it[createdAt] = now
                    it[updatedAt] = now
                }
            } else {
                YoutubeMembersTable.update({ YoutubeMembersTable.uuid eq existing[YoutubeMembersTable.uuid] }) {
                    it[YoutubeMembersTable.youtubeChannelId] = member.youtubeChannelId
                    it[displayName] = member.displayName
                    it[profileImageUrl] = member.profileImageUrl
                    it[membershipLevelName] = member.membershipLevelName
                    it[memberSince] = member.memberSince
                    it[lastSeenAt] = now
                    it[updatedAt] = now
                }
            }
        }
    }

    fun grantYoutubePremiumForCurrentMatches(members: List<YoutubeMemberRecord>, now: Long) = transaction {
        members.forEach { member ->
            val memberChannelId = member.youtubeChannelId ?: return@forEach
            UsersTable
                .selectAll()
                .where { UsersTable.youtubeChannelId eq memberChannelId }
                .forEach { user ->
                    upsertEntitlement(
                        userUuid = user[UsersTable.uuid],
                        source = "youtube",
                        sourceExternalId = memberChannelId,
                        status = "active",
                        startsAt = now,
                        endsAt = null,
                        sourceLabel = member.membershipLevelName,
                        now = now,
                    )
                }
        }
    }

    fun deactivateYoutubeEntitlementsMissingFromSync(seenYoutubeChannelIds: Set<String>, now: Long): Int = transaction {
        if (seenYoutubeChannelIds.isEmpty()) {
            UserEntitlementsTable.update({
                (UserEntitlementsTable.sourceValue eq "youtube") and (UserEntitlementsTable.status eq "active")
            }) {
                it[status] = "inactive"
                it[updatedAt] = now
            }
        } else {
            UserEntitlementsTable.update({
                (UserEntitlementsTable.sourceValue eq "youtube") and
                    (UserEntitlementsTable.status eq "active") and
                    (UserEntitlementsTable.sourceExternalId notInList seenYoutubeChannelIds)
            }) {
                it[status] = "inactive"
                it[updatedAt] = now
            }
        }
    }

    fun grantYoutubePremiumIfMemberMatches(userUuid: UUID, youtubeChannelId: String, now: Long) = transaction {
        val member = YoutubeMembersTable
            .selectAll()
            .where { YoutubeMembersTable.youtubeChannelId eq youtubeChannelId }
            .singleOrNull()
            ?: return@transaction

        upsertEntitlement(
            userUuid = userUuid,
            source = "youtube",
            sourceExternalId = youtubeChannelId,
            status = "active",
            startsAt = now,
            endsAt = null,
            sourceLabel = member[YoutubeMembersTable.membershipLevelName],
            now = now,
        )
    }

    fun deactivateYoutubePremiumForUser(userUuid: UUID, now: Long): Int = transaction {
        UserEntitlementsTable.update({
            (UserEntitlementsTable.userUuid eq userUuid) and
                (UserEntitlementsTable.sourceValue eq "youtube") and
                (UserEntitlementsTable.status eq "active")
        }) {
            it[status] = "inactive"
            it[updatedAt] = now
        }
    }

    fun upsertYoutubeMemberCountSnapshot(snapshotDate: String, memberCount: Int, capturedAt: Long) = transaction {
        YoutubeMemberCountSnapshotsTable.insertIgnore {
            it[YoutubeMemberCountSnapshotsTable.snapshotDate] = snapshotDate
            it[YoutubeMemberCountSnapshotsTable.memberCount] = memberCount
            it[YoutubeMemberCountSnapshotsTable.capturedAt] = capturedAt
            it[createdAt] = capturedAt
            it[updatedAt] = capturedAt
        }
        YoutubeMemberCountSnapshotsTable.update({
            YoutubeMemberCountSnapshotsTable.snapshotDate eq snapshotDate
        }) {
            it[YoutubeMemberCountSnapshotsTable.memberCount] = memberCount
            it[YoutubeMemberCountSnapshotsTable.capturedAt] = capturedAt
            it[updatedAt] = capturedAt
        }
    }

    fun listYoutubeMemberCountSnapshots(
        startDate: String?,
        endDate: String?,
    ): List<YoutubeMemberCountSnapshotRecord> = transaction {
        YoutubeMemberCountSnapshotsTable
            .selectAll()
            .orderBy(YoutubeMemberCountSnapshotsTable.snapshotDate to SortOrder.ASC)
            .map { it.toYoutubeMemberCountSnapshotRecord() }
            .filter { snapshot ->
                (startDate == null || snapshot.snapshotDate >= startDate) &&
                    (endDate == null || snapshot.snapshotDate <= endDate)
            }
    }

    fun revokeManualPremium(userUuid: UUID, now: Long): Int = transaction {
        UserEntitlementsTable.update({
            (UserEntitlementsTable.userUuid eq userUuid) and
                (UserEntitlementsTable.sourceValue eq "manual") and
                (UserEntitlementsTable.status eq "active")
        }) {
            it[status] = "inactive"
            it[updatedAt] = now
        }
    }

    private fun ResultRow.toUserRecord() = UserRecord(
        uuid = this[UsersTable.uuid],
        email = this[UsersTable.email],
        passwordHash = this[UsersTable.passwordHash],
        youtubeUsername = this[UsersTable.youtubeUsername],
        normalizedYoutubeUsername = this[UsersTable.normalizedYoutubeUsername],
        youtubeChannelId = this[UsersTable.youtubeChannelId],
        youtubeDisplayName = this[UsersTable.youtubeDisplayName],
        normalizedYoutubeDisplayName = this[UsersTable.normalizedYoutubeDisplayName],
        createdAt = this[UsersTable.createdAt],
        updatedAt = this[UsersTable.updatedAt],
    )

    private fun ResultRow.toStripeCustomerRecord() = StripeCustomerRecord(
        userUuid = this[StripeCustomersTable.userUuid],
        stripeCustomerId = this[StripeCustomersTable.stripeCustomerId],
        stripeSubscriptionId = this[StripeCustomersTable.stripeSubscriptionId],
        subscriptionStatus = this[StripeCustomersTable.subscriptionStatus],
        currentPeriodEnd = this[StripeCustomersTable.currentPeriodEnd],
    )

    private fun ResultRow.toYoutubeMemberRecord() = YoutubeMemberRecord(
        youtubeChannelId = this[YoutubeMembersTable.youtubeChannelId],
        displayName = this[YoutubeMembersTable.displayName],
        normalizedDisplayName = this[YoutubeMembersTable.normalizedDisplayName],
        profileImageUrl = this[YoutubeMembersTable.profileImageUrl],
        membershipLevelName = this[YoutubeMembersTable.membershipLevelName],
        memberSince = this[YoutubeMembersTable.memberSince],
        lastSeenAt = this[YoutubeMembersTable.lastSeenAt],
    )

    private fun ResultRow.toYoutubeMemberCountSnapshotRecord() = YoutubeMemberCountSnapshotRecord(
        snapshotDate = this[YoutubeMemberCountSnapshotsTable.snapshotDate],
        memberCount = this[YoutubeMemberCountSnapshotsTable.memberCount],
        capturedAt = this[YoutubeMemberCountSnapshotsTable.capturedAt],
        createdAt = this[YoutubeMemberCountSnapshotsTable.createdAt],
        updatedAt = this[YoutubeMemberCountSnapshotsTable.updatedAt],
    )

    private fun ResultRow.toUserEntitlementRecord() = UserEntitlementRecord(
        uuid = this[UserEntitlementsTable.uuid],
        userUuid = this[UserEntitlementsTable.userUuid],
        source = this[UserEntitlementsTable.sourceValue],
        status = this[UserEntitlementsTable.status],
        startsAt = this[UserEntitlementsTable.startsAt],
        endsAt = this[UserEntitlementsTable.endsAt],
        sourceExternalId = this[UserEntitlementsTable.sourceExternalId],
        sourceLabel = this[UserEntitlementsTable.sourceLabel],
        membershipTier = this[UserEntitlementsTable.membershipTier],
        createdAt = this[UserEntitlementsTable.createdAt],
        updatedAt = this[UserEntitlementsTable.updatedAt],
    )

    private fun findEntitlement(source: String, sourceExternalId: String): ResultRow? =
        UserEntitlementsTable
            .selectAll()
            .where {
                (UserEntitlementsTable.sourceValue eq source) and
                    (UserEntitlementsTable.sourceExternalId eq sourceExternalId)
            }
            .singleOrNull()

    private fun upsertEntitlement(
        userUuid: UUID,
        source: String,
        sourceExternalId: String,
        status: String,
        startsAt: Long,
        endsAt: Long?,
        sourceLabel: String?,
        membershipTier: MembershipTier = MembershipTier.PREMIUM,
        now: Long,
    ) {
        val existing = UserEntitlementsTable
            .selectAll()
            .where {
                (UserEntitlementsTable.userUuid eq userUuid) and
                    (UserEntitlementsTable.sourceValue eq source) and
                    (UserEntitlementsTable.sourceExternalId eq sourceExternalId)
            }
            .singleOrNull()

        if (existing == null) {
            UserEntitlementsTable.insert {
                it[uuid] = UUID.randomUUID()
                it[UserEntitlementsTable.userUuid] = userUuid
                it[sourceValue] = source
                it[UserEntitlementsTable.status] = status
                it[UserEntitlementsTable.startsAt] = startsAt
                it[UserEntitlementsTable.endsAt] = endsAt
                it[UserEntitlementsTable.sourceExternalId] = sourceExternalId
                it[UserEntitlementsTable.sourceLabel] = sourceLabel
                it[UserEntitlementsTable.membershipTier] = membershipTier.apiValue
                it[createdAt] = now
                it[updatedAt] = now
            }
        } else {
            UserEntitlementsTable.update({ UserEntitlementsTable.uuid eq existing[UserEntitlementsTable.uuid] }) {
                it[UserEntitlementsTable.status] = status
                it[UserEntitlementsTable.startsAt] = startsAt
                it[UserEntitlementsTable.endsAt] = endsAt
                it[UserEntitlementsTable.sourceLabel] = sourceLabel
                it[UserEntitlementsTable.membershipTier] = membershipTier.apiValue
                it[updatedAt] = now
            }
        }
    }
}
