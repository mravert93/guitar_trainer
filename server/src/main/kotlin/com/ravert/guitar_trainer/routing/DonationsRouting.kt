package com.ravert.guitar_trainer.routing

import com.ravert.guitar_trainer.db.AuthRepository
import com.ravert.guitar_trainer.db.UserRecord
import com.stripe.Stripe
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Customer
import com.stripe.model.Event
import com.stripe.model.Invoice
import com.stripe.model.StripeObject
import com.stripe.model.Subscription
import com.stripe.model.checkout.Session
import com.stripe.net.Webhook
import com.stripe.param.CustomerCreateParams
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
import kotlin.math.roundToLong

@Serializable
data class CreateDonationSessionRequest(
    val amountDollars: Double,
    val frequency: String,
)

@Serializable
data class CreateDonationSessionResponse(
    val url: String,
)

fun Application.configureDonationRouting(authRepository: AuthRepository) {
    val secretKey = System.getenv("STRIPE_SECRET_KEY")
    val webhookSecret = System.getenv("STRIPE_WEBHOOK_SECRET")
    val publicUrl = System.getenv("APP_PUBLIC_URL")

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
            val req = call.receive<CreateDonationSessionRequest>()

            if (req.amountDollars.isNaN() || req.amountDollars <= 0.0) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid amount")
            }

            val amountCents = (req.amountDollars * 100.0).roundToLong()
            if (amountCents < 100) {
                return@post call.respond(HttpStatusCode.BadRequest, "Minimum donation is $1")
            }

            val frequency = req.frequency.trim().lowercase()
            val isMonthly = frequency == "monthly"
            val isOneTime = frequency == "one-time" || frequency == "one_time" || frequency == "once"
            if (!isMonthly && !isOneTime) {
                return@post call.respond(HttpStatusCode.BadRequest, "Unsupported donation frequency")
            }

            val stripeCustomerId = getOrCreateStripeCustomer(authRepository, user, nowMillis())
            val params = buildCheckoutSessionParams(
                publicUrl = publicUrl,
                user = user,
                stripeCustomerId = stripeCustomerId,
                amountCents = amountCents,
                isMonthly = isMonthly,
            )
            val session = Session.create(params)

            call.respond(CreateDonationSessionResponse(session.url))
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

            processStripeWebhookEvent(authRepository, event)
            authRepository.recordStripeWebhookEvent(event.id, event.type, nowMillis())
            call.respond(SuccessResponse(success = true))
        }
    }
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
    amountCents: Long,
    isMonthly: Boolean,
): SessionCreateParams {
    val builder = SessionCreateParams.builder()
        .setMode(if (isMonthly) SessionCreateParams.Mode.SUBSCRIPTION else SessionCreateParams.Mode.PAYMENT)
        .setCustomer(stripeCustomerId)
        .setClientReferenceId(user.uuid.toString())
        .setSuccessUrl("$publicUrl/donations/success?session_id={CHECKOUT_SESSION_ID}")
        .setCancelUrl("$publicUrl/donations/canceled=1")
        .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
        .putMetadata("userUuid", user.uuid.toString())
        .putMetadata("source", "donation")
        .addLineItem(
            SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(
                    SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency("usd")
                        .setUnitAmount(amountCents)
                        .setProductData(
                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(if (isMonthly) "DCT Monthly Donation" else "DCT One-Time Donation")
                                .build()
                        )
                        .apply {
                            if (isMonthly) {
                                setRecurring(
                                    SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                        .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH)
                                        .build()
                                )
                            }
                        }
                        .build()
                )
                .build()
        )

    if (isMonthly) {
        builder.setSubscriptionData(
            SessionCreateParams.SubscriptionData.builder()
                .putMetadata("userUuid", user.uuid.toString())
                .putMetadata("source", "donation")
                .build()
        )
    } else {
        builder.setInvoiceCreation(
            SessionCreateParams.InvoiceCreation.builder()
                .setEnabled(true)
                .build()
        )
    }

    return builder.build()
}

private fun processStripeWebhookEvent(authRepository: AuthRepository, event: Event) {
    when (event.type) {
        "checkout.session.completed" -> {
            val session = event.stripeObject() as? Session ?: return
            if (session.mode != "subscription") return
            handleCheckoutSessionCompleted(authRepository, session)
        }

        "customer.subscription.created",
        "customer.subscription.updated" -> {
            val subscription = event.stripeObject() as? Subscription ?: return
            handleSubscriptionState(authRepository, subscription)
        }

        "customer.subscription.deleted" -> {
            val subscription = event.stripeObject() as? Subscription ?: return
            authRepository.deactivateStripePremium(subscription.id, nowMillis())
            linkStripeSubscription(authRepository, subscription)
        }

        "invoice.paid" -> {
            val invoice = event.stripeObject() as? Invoice ?: return
            val subscription = invoice.subscription?.let { Subscription.retrieve(it) } ?: return
            linkStripeSubscription(authRepository, subscription)
            val userUuid = stripeUserUuid(authRepository, subscription) ?: return
            authRepository.activateStripePremium(
                userUuid = userUuid,
                stripeSubscriptionId = subscription.id,
                subscriptionStatus = subscription.status,
                currentPeriodEnd = subscription.currentPeriodEndMillis(),
                now = nowMillis(),
            )
        }

        "invoice.payment_failed" -> {
            val invoice = event.stripeObject() as? Invoice ?: return
            val subscription = invoice.subscription?.let { Subscription.retrieve(it) } ?: return
            linkStripeSubscription(authRepository, subscription)
        }
    }
}

private fun handleCheckoutSessionCompleted(authRepository: AuthRepository, session: Session) {
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
}

private fun handleSubscriptionState(authRepository: AuthRepository, subscription: Subscription) {
    linkStripeSubscription(authRepository, subscription)
    val userUuid = stripeUserUuid(authRepository, subscription) ?: return

    when (subscription.status) {
        "active",
        "trialing" -> authRepository.activateStripePremium(
            userUuid = userUuid,
            stripeSubscriptionId = subscription.id,
            subscriptionStatus = subscription.status,
            currentPeriodEnd = subscription.currentPeriodEndMillis(),
            now = nowMillis(),
        )

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

private fun Subscription.currentPeriodEndMillis(): Long? = currentPeriodEnd?.times(1000L)

private fun String.toUuidOrNull(): UUID? =
    try {
        UUID.fromString(this)
    } catch (_: IllegalArgumentException) {
        null
    }
