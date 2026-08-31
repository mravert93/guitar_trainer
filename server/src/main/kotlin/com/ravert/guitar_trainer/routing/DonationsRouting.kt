package com.ravert.guitar_trainer.routing

import com.ravert.guitar_trainer.db.AuthRepository
import com.ravert.guitar_trainer.db.MembershipTier
import com.ravert.guitar_trainer.db.UserRecord
import com.stripe.Stripe
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Customer
import com.stripe.model.Event
import com.stripe.model.Invoice
import com.stripe.model.StripeObject
import com.stripe.model.Subscription
import com.stripe.model.billingportal.Session as BillingPortalSession
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.CustomerCreateParams
import com.stripe.param.billingportal.SessionCreateParams as BillingPortalSessionCreateParams
import com.stripe.param.checkout.SessionCreateParams
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CreateMembershipSessionRequest(
    val tier: String,
)

@Serializable
data class CreateMembershipSessionResponse(
    val url: String,
)

@Serializable
data class CreateBillingPortalSessionRequest(
    val returnPath: String? = null,
)

@Serializable
data class CreateBillingPortalSessionResponse(
    val url: String,
)

fun Application.configureDonationRouting(authRepository: AuthRepository) {
    val secretKey = System.getenv("STRIPE_SECRET_KEY")
    val webhookSecret = System.getenv("STRIPE_WEBHOOK_SECRET")
    val publicUrl = System.getenv("APP_PUBLIC_URL")
    val membershipPrices = StripeMembershipPrices(
        premiumPriceId = System.getenv("STRIPE_PREMIUM_PRICE_ID"),
        premiumPlusPriceId = System.getenv("STRIPE_PREMIUM_PLUS_PRICE_ID"),
    )

    Stripe.apiKey = secretKey

    routing {
        post("/stripe/create-checkout-session") {
            if (secretKey.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.InternalServerError, "Stripe is not configured")
            }
            if (publicUrl.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.InternalServerError, "APP_PUBLIC_URL is not configured")
            }

            val user = call.requireUser(authRepository)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            val req = call.receive<CreateMembershipSessionRequest>()
            val membershipTier = MembershipTier.fromApiValue(req.tier)
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Unsupported membership tier")
            val priceId = membershipPrices.priceIdFor(membershipTier)
                ?: return@post call.respond(
                    HttpStatusCode.InternalServerError,
                    "Stripe price is not configured for ${membershipTier.apiValue}",
                )
            val existingStripeCustomer = authRepository.findStripeCustomerByUserUuid(user.uuid)
            if (existingStripeCustomer?.stripeSubscriptionId != null &&
                existingStripeCustomer.subscriptionStatus in ACTIVE_STRIPE_SUBSCRIPTION_STATUSES
            ) {
                return@post call.respond(
                    HttpStatusCode.Conflict,
                    "You already have a Stripe membership. Manage or change it from your account.",
                )
            }

            val stripeCustomerId = getOrCreateStripeCustomer(authRepository, user, nowMillis())
            val params = buildCheckoutSessionParams(
                publicUrl = publicUrl,
                user = user,
                stripeCustomerId = stripeCustomerId,
                priceId = priceId,
                membershipTier = membershipTier,
            )
            val session = Session.create(params)

            call.respond(CreateMembershipSessionResponse(session.url))
        }

        post("/stripe/create-billing-portal-session") {
            if (secretKey.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.InternalServerError, "Stripe is not configured")
            }
            if (publicUrl.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.InternalServerError, "APP_PUBLIC_URL is not configured")
            }

            val user = call.requireUser(authRepository)
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Unauthorized")
            val stripeCustomer = authRepository.findStripeCustomerByUserUuid(user.uuid)
                ?: return@post call.respond(HttpStatusCode.NotFound, "Stripe customer not found")
            val req = call.receive<CreateBillingPortalSessionRequest>()

            val session = BillingPortalSession.create(
                BillingPortalSessionCreateParams.builder()
                    .setCustomer(stripeCustomer.stripeCustomerId)
                    .setReturnUrl(buildBillingPortalReturnUrl(publicUrl, req.returnPath))
                    .build()
            )

            call.respond(CreateBillingPortalSessionResponse(session.url))
        }

        post("/stripe/webhook") {
            if (webhookSecret.isNullOrBlank()) {
                return@post call.respond(HttpStatusCode.InternalServerError, "Stripe webhook is not configured")
            }

            val payload = call.receiveText()
            val signature = call.request.headers["Stripe-Signature"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing Stripe signature")

            val event = try {
                Webhook.constructEvent(payload, signature, webhookSecret)
            } catch (_: SignatureVerificationException) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid Stripe signature")
            }

            if (authRepository.hasProcessedStripeWebhookEvent(event.id)) {
                return@post call.respond(SuccessResponse(success = true))
            }

            processStripeWebhookEvent(authRepository, event, membershipPrices)
            authRepository.recordStripeWebhookEvent(event.id, event.type, nowMillis())
            call.respond(SuccessResponse(success = true))
        }
    }
}

private fun buildBillingPortalReturnUrl(publicUrl: String, returnPath: String?): String {
    val safeReturnPath = returnPath
        ?.trim()
        ?.takeIf { it.startsWith("/") && !it.startsWith("//") }
        ?: "/account"

    return publicUrl.trimEnd('/') + safeReturnPath
}

private fun getOrCreateStripeCustomer(authRepository: AuthRepository, user: UserRecord, now: Long): String {
    val existing = authRepository.findStripeCustomerByUserUuid(user.uuid)
    if (existing != null) return existing.stripeCustomerId

    val customer = Customer.create(
        CustomerCreateParams.builder()
            .setEmail(user.email)
            .putMetadata("userUuid", user.uuid.toString())
            .build()
    )
    authRepository.upsertStripeCustomer(
        userUuid = user.uuid,
        stripeCustomerId = customer.id,
        stripeSubscriptionId = null,
        subscriptionStatus = null,
        currentPeriodEnd = null,
        now = now,
    )
    return customer.id
}

private fun buildCheckoutSessionParams(
    publicUrl: String,
    user: UserRecord,
    stripeCustomerId: String,
    priceId: String,
    membershipTier: MembershipTier,
): SessionCreateParams {
    return SessionCreateParams.builder()
        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
        .setCustomer(stripeCustomerId)
        .setClientReferenceId(user.uuid.toString())
        .setSuccessUrl("${publicUrl.trimEnd('/')}/memberships/success?session_id={CHECKOUT_SESSION_ID}")
        .setCancelUrl("${publicUrl.trimEnd('/')}/memberships?canceled=1")
        .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
        .putMetadata("userUuid", user.uuid.toString())
        .putMetadata("source", "membership")
        .putMetadata("membershipTier", membershipTier.apiValue)
        .addLineItem(
            SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPrice(priceId)
                .build()
        )
        .setSubscriptionData(
            SessionCreateParams.SubscriptionData.builder()
                .putMetadata("userUuid", user.uuid.toString())
                .putMetadata("source", "membership")
                .putMetadata("membershipTier", membershipTier.apiValue)
                .build()
        )
        .build()
}

private fun processStripeWebhookEvent(
    authRepository: AuthRepository,
    event: Event,
    membershipPrices: StripeMembershipPrices,
) {
    when (event.type) {
        "checkout.session.completed" -> {
            val session = event.stripeObject() as? Session ?: return
            if (session.mode != "subscription") return
            handleCheckoutSessionCompleted(authRepository, session, membershipPrices)
        }

        "customer.subscription.created",
        "customer.subscription.updated" -> {
            val subscription = event.stripeObject() as? Subscription ?: return
            handleSubscriptionState(authRepository, subscription, membershipPrices)
        }

        "customer.subscription.deleted" -> {
            val subscription = event.stripeObject() as? Subscription ?: return
            authRepository.deactivateStripePremium(subscription.id, nowMillis())
            linkStripeSubscription(authRepository, subscription)
        }

        "invoice.paid" -> {
            val invoice = event.stripeObject() as? Invoice ?: return
            val subscription = invoice.subscription?.let { Subscription.retrieve(it) } ?: return
            handleSubscriptionState(authRepository, subscription, membershipPrices)
        }

        "invoice.payment_failed" -> {
            val invoice = event.stripeObject() as? Invoice ?: return
            val subscription = invoice.subscription?.let { Subscription.retrieve(it) } ?: return
            linkStripeSubscription(authRepository, subscription)
        }
    }
}

private fun handleCheckoutSessionCompleted(
    authRepository: AuthRepository,
    session: Session,
    membershipPrices: StripeMembershipPrices,
) {
    val userUuid = session.metadata?.get("userUuid")?.toUuidOrNull() ?: return
    val stripeCustomerId = session.customer ?: return
    val stripeSubscriptionId = session.subscription
    val subscription = stripeSubscriptionId?.let { Subscription.retrieve(it) }
    authRepository.upsertStripeCustomer(
        userUuid = userUuid,
        stripeCustomerId = stripeCustomerId,
        stripeSubscriptionId = stripeSubscriptionId,
        subscriptionStatus = subscription?.status,
        currentPeriodEnd = subscription?.currentPeriodEndMillis(),
        now = nowMillis(),
    )
    subscription?.let { handleSubscriptionState(authRepository, it, membershipPrices) }
}

private fun handleSubscriptionState(
    authRepository: AuthRepository,
    subscription: Subscription,
    membershipPrices: StripeMembershipPrices,
) {
    linkStripeSubscription(authRepository, subscription)
    val userUuid = stripeUserUuid(authRepository, subscription) ?: return
    val membershipTier = subscription.membershipTier(membershipPrices)

    when (subscription.status) {
        "active",
        "trialing" -> {
            if (subscription.isCancelingAtPeriodEnd()) {
                authRepository.deactivateStripePremium(subscription.id, nowMillis())
            } else {
                authRepository.activateStripePremium(
                    userUuid = userUuid,
                    stripeSubscriptionId = subscription.id,
                    subscriptionStatus = subscription.status,
                    currentPeriodEnd = subscription.currentPeriodEndMillis(),
                    membershipTier = membershipTier,
                    now = nowMillis(),
                )
            }
        }

        "canceled",
        "unpaid",
        "incomplete_expired",
        "paused" -> authRepository.deactivateStripePremium(subscription.id, nowMillis())

        "past_due",
        "incomplete" -> authRepository.keepStripePremiumUntilCurrentPeriodEnd(
            userUuid = userUuid,
            stripeSubscriptionId = subscription.id,
            subscriptionStatus = subscription.status,
            currentPeriodEnd = subscription.currentPeriodEndMillis(),
            membershipTier = membershipTier,
            now = nowMillis(),
        )
    }
}

private fun linkStripeSubscription(authRepository: AuthRepository, subscription: Subscription) {
    val userUuid = stripeUserUuid(authRepository, subscription) ?: return
    val customerId = subscription.customer ?: return
    authRepository.upsertStripeCustomer(
        userUuid = userUuid,
        stripeCustomerId = customerId,
        stripeSubscriptionId = subscription.id,
        subscriptionStatus = subscription.status,
        currentPeriodEnd = subscription.currentPeriodEndMillis(),
        now = nowMillis(),
    )
}

private fun stripeUserUuid(authRepository: AuthRepository, subscription: Subscription): UUID? {
    subscription.metadata?.get("userUuid")?.toUuidOrNull()?.let { return it }
    authRepository.findStripeCustomerBySubscriptionId(subscription.id)?.userUuid?.let { return it }
    subscription.customer?.let { customerId ->
        authRepository.findStripeCustomerByCustomerId(customerId)?.userUuid?.let { return it }
    }
    return null
}

private fun Event.stripeObject(): StripeObject? =
    dataObjectDeserializer.getObject().orElseGet {
        try {
            dataObjectDeserializer.deserializeUnsafe()
        } catch (_: Exception) {
            null
        }
    }

private fun Subscription.isCancelingAtPeriodEnd(): Boolean = cancelAtPeriodEnd == true

private fun Subscription.currentPeriodEndMillis(): Long? = currentPeriodEnd?.times(1000L)

private fun Subscription.membershipTier(membershipPrices: StripeMembershipPrices): MembershipTier {
    val priceId = items?.data?.firstOrNull()?.price?.id
    return membershipPrices.tierForPriceId(priceId)
        ?: MembershipTier.fromApiValue(metadata?.get("membershipTier"))
        ?: MembershipTier.PREMIUM
}

private data class StripeMembershipPrices(
    val premiumPriceId: String?,
    val premiumPlusPriceId: String?,
) {
    fun priceIdFor(tier: MembershipTier): String? = when (tier) {
        MembershipTier.PREMIUM -> premiumPriceId
        MembershipTier.PREMIUM_PLUS -> premiumPlusPriceId
    }?.takeIf { it.isNotBlank() }

    fun tierForPriceId(priceId: String?): MembershipTier? {
        if (priceId.isNullOrBlank()) return null
        return when (priceId) {
            premiumPriceId -> MembershipTier.PREMIUM
            premiumPlusPriceId -> MembershipTier.PREMIUM_PLUS
            else -> null
        }
    }
}

private val ACTIVE_STRIPE_SUBSCRIPTION_STATUSES = setOf(
    "active",
    "trialing",
    "past_due",
    "incomplete",
)

private fun String.toUuidOrNull(): UUID? =
    try {
        UUID.fromString(this)
    } catch (_: IllegalArgumentException) {
        null
    }
