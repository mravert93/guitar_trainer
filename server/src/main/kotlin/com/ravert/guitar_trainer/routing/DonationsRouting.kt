package com.ravert.guitar_trainer.routing

import com.stripe.Stripe
import com.stripe.model.checkout.Session
import com.stripe.param.checkout.SessionCreateParams
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
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

fun Application.configureDonationRouting() {
    val secretKey = System.getenv("STRIPE_SECRET_KEY")
    val publicUrl = System.getenv("APP_PUBLIC_URL")

    Stripe.apiKey = secretKey

    routing {
        post("/stripe/create-checkout-session") {
            val req = call.receive<CreateDonationSessionRequest>()

            if (req.amountDollars.isNaN() || req.amountDollars <= 0.0) {
                return@post call.respond(HttpStatusCode.BadRequest, "Invalid amount")
            }

            val amountCents = (req.amountDollars * 100.0).roundToLong()
            if (amountCents < 100) {
                return@post call.respond(HttpStatusCode.BadRequest, "Minimum donation is $1")
            }

            val params = if (req.frequency == "weekly") {
                SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl("$publicUrl/donations/success?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl("$publicUrl/donations/canceled=1")
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .addLineItem(
                        SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(
                                SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("usd")
                                    .setUnitAmount(amountCents)
                                    .setRecurring(
                                        SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                            .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.WEEK)
                                            .build()
                                    )
                                    .setProductData(
                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("Support DCT Donation")
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .putMetadata("type", "donation")
                    .build()
            } else if (req.frequency == "monthly") {
                SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl("$publicUrl/donations/success?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl("$publicUrl/donations/canceled=1")
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .addLineItem(
                        SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(
                                SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("usd")
                                    .setUnitAmount(amountCents)
                                    .setRecurring(
                                        SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                            .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH)
                                            .build()
                                    )
                                    .setProductData(
                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("Support DCT Donation")
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .putMetadata("type", "donation")
                    .build()
            } else if (req.frequency == "quarterly") {
                SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl("$publicUrl/donations/success?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl("$publicUrl/donations/canceled=1")
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .addLineItem(
                        SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(
                                SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("usd")
                                    .setUnitAmount(amountCents)
                                    .setRecurring(
                                        SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                            .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH)
                                            .setIntervalCount(3L)
                                            .build()
                                    )
                                    .setProductData(
                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("Support DCT Donation")
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .putMetadata("type", "donation")
                    .build()
            } else if (req.frequency == "annual") {
                SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setSuccessUrl("$publicUrl/donations/success?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl("$publicUrl/donations/canceled=1")
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .addLineItem(
                        SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(
                                SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("usd")
                                    .setUnitAmount(amountCents)
                                    .setRecurring(
                                        SessionCreateParams.LineItem.PriceData.Recurring.builder()
                                            .setInterval(SessionCreateParams.LineItem.PriceData.Recurring.Interval.YEAR)
                                            .build()
                                    )
                                    .setProductData(
                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("Support DCT Donation")
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .putMetadata("type", "donation")
                    .build()
            } else {
                SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl("$publicUrl/donations/success?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl("$publicUrl/donations/canceled=1")
                    .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                    .addLineItem(
                        SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(
                                SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("usd")
                                    .setUnitAmount(amountCents)
                                    .setProductData(
                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName("Support DCT Donation")
                                            .build()
                                    )
                                    .build()
                            )
                            .build()
                    )
                    .putMetadata("type", "donation")
                    .build()
            }

            val session = Session.create(params)

            call.respond(CreateDonationSessionResponse(session.url))
        }
    }

}