package com.ravert.guitar_trainer.routing

import at.favre.lib.crypto.bcrypt.BCrypt
import com.ravert.guitar_trainer.db.AuthRepository
import com.ravert.guitar_trainer.db.StripeCustomerRecord
import com.ravert.guitar_trainer.db.UserEntitlementRecord
import com.ravert.guitar_trainer.db.UserRecord
import com.ravert.guitar_trainer.db.YoutubeMemberRecord
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.exceptions.ExposedSQLException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlin.text.Charsets.UTF_8

private const val SessionCookieName = "dct_session"
private const val SessionDurationMillis = 30L * 24L * 60L * 60L * 1000L
private const val SessionDurationSeconds = 30 * 24 * 60 * 60
private val EmailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

@Serializable
data class AuthRequest(
    val email: String,
    val password: String,
    val youtubeUsername: String? = null,
    val youtubeDisplayName: String? = null,
)

@Serializable
data class AuthUserDto(
    val uuid: String,
    val email: String,
    val youtubeUsername: String? = null,
    val youtubeChannelId: String? = null,
    val youtubeDisplayName: String? = null,
    val hasPremium: Boolean,
    val membershipTier: String? = null,
)

@Serializable
data class AuthResponse(
    val user: AuthUserDto,
)

@Serializable
data class LogoutResponse(
    val success: Boolean,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class ManualPremiumGrantRequest(
    val sourceLabel: String? = null,
    val endsAt: Long? = null,
)

@Serializable
data class SuccessResponse(
    val success: Boolean,
)

@Serializable
data class UpdateYoutubeDisplayNameRequest(
    val youtubeUsername: String? = null,
    val youtubeDisplayName: String? = null,
)

@Serializable
data class AdminUsersResponse(
    val users: List<AdminUserSummaryDto>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class AdminUserSummaryDto(
    val uuid: String,
    val email: String,
    val youtubeUsername: String? = null,
    val youtubeChannelId: String? = null,
    val youtubeDisplayName: String? = null,
    val hasPremium: Boolean,
    val membershipTier: String? = null,
    val premiumSources: List<String>,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class AdminUserDetailResponse(
    val user: AdminUserSummaryDto,
    val entitlements: List<AdminEntitlementDto>,
    val stripe: AdminStripeCustomerDto? = null,
    val youtube: AdminYoutubeMemberDto? = null,
)

@Serializable
data class AdminEntitlementDto(
    val uuid: String,
    val source: String,
    val status: String,
    val currentlyActive: Boolean,
    val startsAt: Long,
    val endsAt: Long? = null,
    val sourceExternalId: String? = null,
    val sourceLabel: String? = null,
    val membershipTier: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class AdminStripeCustomerDto(
    val stripeCustomerId: String,
    val stripeSubscriptionId: String? = null,
    val subscriptionStatus: String? = null,
    val currentPeriodEnd: Long? = null,
)

@Serializable
data class AdminYoutubeMemberDto(
    val matchedMember: Boolean,
    val youtubeChannelId: String,
    val displayName: String,
    val profileImageUrl: String? = null,
    val membershipLevelName: String? = null,
    val memberSince: Long? = null,
    val lastSeenAt: Long? = null,
)

@Serializable
private data class YoutubeChannelsResponse(
    val items: List<YoutubeChannelItem> = emptyList(),
)

@Serializable
private data class YoutubeChannelItem(
    val id: String,
)

fun Application.configureAuthRoutes(authRepository: AuthRepository, httpClient: HttpClient) {
    routing {
        route("/auth") {
            post("/signup") {
                val req = call.receive<AuthRequest>()
                val email = normalizeEmail(req.email)
                val validationError = validateAuthRequest(email, req.password)
                if (validationError != null) {
                    return@post call.respond(HttpStatusCode.BadRequest, validationError)
                }

                if (authRepository.findUserByEmail(email) != null) {
                    return@post call.respond(HttpStatusCode.Conflict, "Email already exists")
                }

                val now = nowMillis()
                val youtubeUsername = (req.youtubeUsername ?: req.youtubeDisplayName)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val normalizedYoutubeUsername = youtubeUsername?.let(::normalizeYoutubeUsername)
                val youtubeChannelId = youtubeUsername?.let { resolveYoutubeChannelId(httpClient, it) }
                val passwordHash = hashPassword(req.password)
                val user = try {
                    authRepository.createUser(
                        email = email,
                        passwordHash = passwordHash,
                        youtubeUsername = youtubeUsername,
                        normalizedYoutubeUsername = normalizedYoutubeUsername,
                        youtubeChannelId = youtubeChannelId,
                        now = now,
                    )
                } catch (e: ExposedSQLException) {
                    if (e.sqlState == "23505") {
                        return@post call.respond(HttpStatusCode.Conflict, "Email already exists")
                    }
                    throw e
                }

                if (youtubeChannelId != null) {
                    authRepository.grantYoutubePremiumIfMemberMatches(user.uuid, youtubeChannelId, now)
                }

                val token = createSession(authRepository, user.uuid, now)
                call.setSessionCookie(token)
                call.respond(HttpStatusCode.Created, authRepository.authResponse(user))
            }

            post("/login") {
                val req = call.receive<AuthRequest>()
                val email = normalizeEmail(req.email)
                if (!EmailRegex.matches(email) || req.password.isBlank()) {
                    return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                }

                val user = authRepository.findUserByEmail(email)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")

                if (!verifyPassword(req.password, user.passwordHash)) {
                    return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                }

                val token = createSession(authRepository, user.uuid, nowMillis())
                call.setSessionCookie(token)
                call.respond(authRepository.authResponse(user))
            }

            get("/me") {
                val user = call.requireUser(authRepository)
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")

                call.respond(authRepository.authResponse(user))
            }

            patch("/me/youtube") {
                val user = call.requireUser(authRepository)
                    ?: return@patch call.respond(HttpStatusCode.Unauthorized, "Unauthorized")

                val req = call.receive<UpdateYoutubeDisplayNameRequest>()
                val youtubeUsername = (req.youtubeUsername ?: req.youtubeDisplayName)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val normalizedYoutubeUsername = youtubeUsername?.let(::normalizeYoutubeUsername)
                val youtubeChannelId = youtubeUsername?.let { resolveYoutubeChannelId(httpClient, it) }
                val now = nowMillis()
                val updatedUser = authRepository.updateUserYoutubeUsername(
                    userUuid = user.uuid,
                    username = youtubeUsername,
                    normalizedUsername = normalizedYoutubeUsername,
                    youtubeChannelId = youtubeChannelId,
                    now = now,
                ) ?: return@patch call.respond(HttpStatusCode.NotFound, "User not found")

                if (user.youtubeChannelId != youtubeChannelId) {
                    authRepository.deactivateYoutubePremiumForUser(user.uuid, now)
                }
                if (youtubeChannelId != null) {
                    authRepository.grantYoutubePremiumIfMemberMatches(user.uuid, youtubeChannelId, now)
                }

                call.respond(authRepository.authResponse(updatedUser))
            }

            post("/change-password") {
                val user = call.requireUser(authRepository)
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")

                val req = call.receive<ChangePasswordRequest>()
                val passwordValidationError = validatePassword(req.newPassword)
                if (passwordValidationError != null) {
                    return@post call.respond(HttpStatusCode.BadRequest, passwordValidationError)
                }

                if (!verifyPassword(req.currentPassword, user.passwordHash)) {
                    return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
                }

                val updatedRows = authRepository.updateUserPasswordHash(
                    userUuid = user.uuid,
                    passwordHash = hashPassword(req.newPassword),
                    now = nowMillis(),
                )
                if (updatedRows == 0) {
                    return@post call.respond(HttpStatusCode.NotFound, "User not found")
                }

                call.respond(SuccessResponse(success = true))
            }

            post("/logout") {
                val token = call.request.cookies[SessionCookieName]
                if (token != null) {
                    authRepository.revokeSessionByTokenHash(hashSessionToken(token), nowMillis())
                }

                call.clearSessionCookie()
                call.respond(LogoutResponse(success = true))
            }
        }

        get("/admin/users") {
            if (!call.hasAdminAuth()) {
                return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }

            val limit = call.request.queryParameters["limit"]
                ?.toIntOrNull()
                ?.coerceIn(1, 100)
                ?: 50
            val offset = call.request.queryParameters["offset"]
                ?.toIntOrNull()
                ?.coerceAtLeast(0)
                ?: 0
            val premium = call.request.queryParameters["premium"] ?: "all"
            if (premium !in setOf("all", "yes", "no")) {
                return@get call.respond(HttpStatusCode.BadRequest, "premium must be all, yes, or no")
            }

            val users = authRepository.listUsers(call.request.queryParameters["query"])
                .map { user -> authRepository.adminUserSummaryDto(user) }
                .filter { user ->
                    when (premium) {
                        "yes" -> user.hasPremium
                        "no" -> !user.hasPremium
                        else -> true
                    }
                }

            call.respond(
                AdminUsersResponse(
                    users = users.drop(offset).take(limit),
                    total = users.size,
                    limit = limit,
                    offset = offset,
                )
            )
        }

        get("/admin/users/{userUuid}") {
            if (!call.hasAdminAuth()) {
                return@get call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }

            val userUuid = call.userUuidParameter()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user UUID")
            val user = authRepository.findUserByUuid(userUuid)
                ?: return@get call.respond(HttpStatusCode.NotFound, "User not found")

            call.respond(authRepository.adminUserDetailResponse(user))
        }

        patch("/admin/users/{userUuid}/youtube") {
            if (!call.hasAdminAuth()) {
                return@patch call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }

            val userUuid = call.userUuidParameter()
                ?: return@patch call.respond(HttpStatusCode.BadRequest, "Invalid user UUID")
            val user = authRepository.findUserByUuid(userUuid)
                ?: return@patch call.respond(HttpStatusCode.NotFound, "User not found")

            val req = call.receive<UpdateYoutubeDisplayNameRequest>()
            val youtubeUsername = (req.youtubeUsername ?: req.youtubeDisplayName)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
            val normalizedYoutubeUsername = youtubeUsername?.let(::normalizeYoutubeUsername)
            val youtubeChannelId = youtubeUsername?.let { resolveYoutubeChannelId(httpClient, it) }
            val now = nowMillis()
            val updatedUser = authRepository.updateUserYoutubeUsername(
                userUuid = user.uuid,
                username = youtubeUsername,
                normalizedUsername = normalizedYoutubeUsername,
                youtubeChannelId = youtubeChannelId,
                now = now,
            ) ?: return@patch call.respond(HttpStatusCode.NotFound, "User not found")

            if (user.youtubeChannelId != youtubeChannelId) {
                authRepository.deactivateYoutubePremiumForUser(user.uuid, now)
            }
            if (youtubeChannelId != null) {
                authRepository.grantYoutubePremiumIfMemberMatches(user.uuid, youtubeChannelId, now)
            }

            call.respond(authRepository.adminUserDetailResponse(updatedUser))
        }

        post("/admin/users/{userUuid}/grant-premium") {
            if (!call.hasAdminAuth()) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }

            val userUuid = call.userUuidParameter()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid user UUID")

            val user = authRepository.findUserByUuid(userUuid)
                ?: return@post call.respond(HttpStatusCode.NotFound, "User not found")

            val req = call.receive<ManualPremiumGrantRequest>()
            authRepository.grantManualPremium(userUuid, req.sourceLabel, req.endsAt, nowMillis())
            call.respond(authRepository.adminUserDetailResponse(user))
        }

        post("/admin/users/{userUuid}/revoke-premium") {
            if (!call.hasAdminAuth()) {
                return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            }

            val userUuid = call.userUuidParameter()
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid user UUID")

            val user = authRepository.findUserByUuid(userUuid)
                ?: return@post call.respond(HttpStatusCode.NotFound, "User not found")

            authRepository.revokeManualPremium(userUuid, nowMillis())
            call.respond(authRepository.adminUserDetailResponse(user))
        }
    }
}

suspend fun ApplicationCall.requireUser(authRepository: AuthRepository): UserRecord? {
    val token = request.cookies[SessionCookieName] ?: return null
    return authRepository.findUserBySessionTokenHash(hashSessionToken(token), nowMillis())
}

fun generateSessionToken(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

fun hashSessionToken(token: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(UTF_8))
    return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

fun nowMillis(): Long = System.currentTimeMillis()

fun ApplicationCall.setSessionCookie(token: String) {
    response.cookies.append(
        Cookie(
            name = SessionCookieName,
            value = token,
            path = "/",
            httpOnly = true,
            secure = sessionCookieSecure(),
            maxAge = SessionDurationSeconds,
            extensions = mapOf("SameSite" to "Lax"),
        )
    )
}

fun ApplicationCall.clearSessionCookie() {
    response.cookies.append(
        Cookie(
            name = SessionCookieName,
            value = "",
            path = "/",
            httpOnly = true,
            secure = sessionCookieSecure(),
            maxAge = 0,
            extensions = mapOf("SameSite" to "Lax"),
        )
    )
}

suspend fun AuthRepository.authResponse(user: UserRecord) = AuthResponse(
    user = userMembershipTier(user.uuid).let { membershipTier ->
        AuthUserDto(
            uuid = user.uuid.toString(),
            email = user.email,
            youtubeUsername = user.youtubeUsername,
            youtubeChannelId = user.youtubeChannelId,
            youtubeDisplayName = user.youtubeDisplayName,
            hasPremium = membershipTier != null,
            membershipTier = membershipTier?.apiValue,
        )
    }
)

private suspend fun AuthRepository.adminUserSummaryDto(user: UserRecord): AdminUserSummaryDto {
    val now = nowMillis()
    val entitlements = findEntitlementsByUserUuid(user.uuid)
    val activeSources = entitlements
        .filter { it.isCurrentlyActive(now) }
        .map { it.source }
        .distinct()
        .sorted()
    val membershipTier = userMembershipTier(user.uuid)

    return AdminUserSummaryDto(
        uuid = user.uuid.toString(),
        email = user.email,
        youtubeUsername = user.youtubeUsername,
        youtubeChannelId = user.youtubeChannelId,
        youtubeDisplayName = user.youtubeDisplayName,
        hasPremium = membershipTier != null,
        membershipTier = membershipTier?.apiValue,
        premiumSources = activeSources,
        createdAt = user.createdAt,
        updatedAt = user.updatedAt,
    )
}

private suspend fun AuthRepository.adminUserDetailResponse(user: UserRecord): AdminUserDetailResponse {
    val now = nowMillis()
    val entitlements = findEntitlementsByUserUuid(user.uuid)
    val stripeCustomer = findStripeCustomerByUserUuid(user.uuid)
    val youtubeMember = user.youtubeChannelId?.let { findYoutubeMemberByChannelId(it) }

    return AdminUserDetailResponse(
        user = adminUserSummaryDto(user),
        entitlements = entitlements.map { it.toAdminEntitlementDto(now) },
        stripe = stripeCustomer?.toAdminStripeCustomerDto(),
        youtube = youtubeMember?.toAdminYoutubeMemberDto(),
    )
}

private fun UserEntitlementRecord.toAdminEntitlementDto(now: Long) = AdminEntitlementDto(
    uuid = uuid.toString(),
    source = source,
    status = status,
    currentlyActive = isCurrentlyActive(now),
    startsAt = startsAt,
    endsAt = endsAt,
    sourceExternalId = sourceExternalId,
    sourceLabel = sourceLabel,
    membershipTier = membershipTier,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun UserEntitlementRecord.isCurrentlyActive(now: Long): Boolean =
    status == "active" && startsAt <= now && (endsAt == null || endsAt > now)

private fun StripeCustomerRecord.toAdminStripeCustomerDto() = AdminStripeCustomerDto(
    stripeCustomerId = stripeCustomerId,
    stripeSubscriptionId = stripeSubscriptionId,
    subscriptionStatus = subscriptionStatus,
    currentPeriodEnd = currentPeriodEnd,
)

private fun YoutubeMemberRecord.toAdminYoutubeMemberDto() = AdminYoutubeMemberDto(
    matchedMember = true,
    youtubeChannelId = youtubeChannelId.orEmpty(),
    displayName = displayName,
    profileImageUrl = profileImageUrl,
    membershipLevelName = membershipLevelName,
    memberSince = memberSince,
    lastSeenAt = lastSeenAt,
)

private fun createSession(authRepository: AuthRepository, userUuid: UUID, now: Long): String {
    val token = generateSessionToken()
    authRepository.createSession(
        userUuid = userUuid,
        sessionTokenHash = hashSessionToken(token),
        createdAt = now,
        expiresAt = now + SessionDurationMillis,
    )
    return token
}

private fun normalizeEmail(email: String): String = email.trim().lowercase()

private fun validateAuthRequest(email: String, password: String): String? {
    return when {
        !EmailRegex.matches(email) -> "Invalid email"
        else -> validatePassword(password)
    }
}

private fun validatePassword(password: String): String? {
    return when {
        password.isBlank() -> "Password is required"
        password.length < 8 -> "Password must be at least 8 characters"
        else -> null
    }
}

private fun hashPassword(password: String): String =
    BCrypt.withDefaults().hashToString(12, password.toCharArray())

private fun verifyPassword(password: String, passwordHash: String): Boolean =
    BCrypt.verifyer().verify(password.toCharArray(), passwordHash).verified

fun normalizeYoutubeName(value: String): String =
    value.trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")

fun normalizeYoutubeUsername(value: String): String =
    value.trim()
        .removePrefix("@")
        .lowercase()
        .replace(Regex("\\s+"), "")

suspend fun resolveYoutubeChannelId(httpClient: HttpClient, usernameOrHandle: String): String? {
    val accessToken = youtubeAccessTokenOrNull(httpClient) ?: return null
    val trimmed = usernameOrHandle.trim().takeIf { it.isNotBlank() } ?: return null

    resolveYoutubeChannelIdWithParam(httpClient, accessToken, "forHandle", trimmed)?.let { return it }
    resolveYoutubeChannelIdWithParam(httpClient, accessToken, "forUsername", trimmed.removePrefix("@"))?.let { return it }
    return null
}

private suspend fun resolveYoutubeChannelIdWithParam(
    httpClient: HttpClient,
    accessToken: String,
    paramName: String,
    paramValue: String,
): String? {
    val response = httpClient.get("https://youtube.googleapis.com/youtube/v3/channels") {
        header(HttpHeaders.Authorization, "Bearer $accessToken")
        parameter("part", "id")
        parameter(paramName, paramValue)
    }

    if (!response.status.isSuccess()) return null
    return response.body<YoutubeChannelsResponse>().items.firstOrNull()?.id
}

suspend fun youtubeAccessTokenOrNull(httpClient: HttpClient): String? {
    val clientId = System.getenv("YOUTUBE_CLIENT_ID") ?: return null
    val clientSecret = System.getenv("YOUTUBE_CLIENT_SECRET") ?: return null
    val refreshToken = System.getenv("YOUTUBE_REFRESH_TOKEN") ?: return null

    val response = httpClient.post("https://oauth2.googleapis.com/token") {
        setBody(
            FormDataContent(
                Parameters.build {
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("refresh_token", refreshToken)
                    append("grant_type", "refresh_token")
                    append("scope", "https://www.googleapis.com/auth/youtube.channel-memberships.creator")
                }
            )
        )
    }

    if (!response.status.isSuccess()) return null
    return try {
        response.body<AuthGoogleTokenResponse>().accessToken
    } catch (_: Exception) {
        null
    }
}

@Serializable
private data class AuthGoogleTokenResponse(
    @kotlinx.serialization.SerialName("access_token")
    val accessToken: String,
)

private fun sessionCookieSecure(): Boolean {
    val explicit = System.getenv("SESSION_COOKIE_SECURE")?.toBooleanStrictOrNull()
    if (explicit != null) return explicit

    return System.getenv("APP_PUBLIC_URL")?.startsWith("https://") == true ||
        System.getenv("ENVIRONMENT") == "production" ||
        System.getenv("KTOR_ENV") == "production" ||
        System.getenv("RENDER") == "true"
}

fun ApplicationCall.hasAdminAuth(): Boolean = request.cookies["admin_auth"] == "true"

private fun ApplicationCall.userUuidParameter(): UUID? =
    parameters["userUuid"]?.let {
        try {
            UUID.fromString(it)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
