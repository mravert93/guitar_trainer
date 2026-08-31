package com.ravert.guitar_trainer

import com.ravert.guitar_trainer.db.BetaFeedbackRepository
import com.ravert.guitar_trainer.db.AuthRepository
import com.ravert.guitar_trainer.db.DatabaseFactory
import com.ravert.guitar_trainer.db.FavoritesRepository
import com.ravert.guitar_trainer.db.LibraryRepository
import com.ravert.guitar_trainer.db.MonthlyTabDownloadLinksRepository
import com.ravert.guitar_trainer.db.TabRequestsRepository
import com.ravert.guitar_trainer.cloudinary.CloudinaryService
import com.ravert.guitar_trainer.routing.configureAdminRoutes
import com.ravert.guitar_trainer.routing.configureAuthRoutes
import com.ravert.guitar_trainer.routing.configureBetaFeedbackRoutes
import com.ravert.guitar_trainer.routing.configureDonationRouting
import com.ravert.guitar_trainer.routing.configureEarlyAccessRoutes
import com.ravert.guitar_trainer.routing.configureFavoriteRoutes
import com.ravert.guitar_trainer.routing.configureImportRoutes
import com.ravert.guitar_trainer.routing.configureNewestTabsRoutes
import com.ravert.guitar_trainer.routing.configureMonthlyTabDownloadLinkRoutes
import com.ravert.guitar_trainer.routing.configureTabRequestRoutes
import com.ravert.guitar_trainer.routing.configureYoutubeMemberRoutes
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.net.URI

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ServerContentNegotiation) {
        json(Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true })
        json(Json { prettyPrint = true; isLenient = true; ignoreUnknownKeys = true },
            contentType = ContentType.parse("text/javascript")
        )
    }


    // CORS
    install(CORS) {
        // For dev: allow your web app origin
        allowedFrontendOrigins().forEach { origin ->
            allowHost(origin.host, schemes = listOf(origin.scheme))
        }

        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Delete)

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)

        allowCredentials = true
    }

    DatabaseFactory.init()
    val repo = LibraryRepository()
    val betaFeedbackRepo = BetaFeedbackRepository()
    val authRepository = AuthRepository()
    val favoritesRepository = FavoritesRepository()
    val tabRequestsRepository = TabRequestsRepository()
    val monthlyTabDownloadLinksRepository = MonthlyTabDownloadLinksRepository()
    val cloudinaryService = CloudinaryService.fromEnvironment()

    val httpClient = HttpClient(CIO) {
        followRedirects = true
        expectSuccess = false

        engine { requestTimeout = 30_000 }

        install(ClientContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
            // For Apple returning text/javascript (optional)
            json(Json { ignoreUnknownKeys = true; isLenient = true },
                contentType = ContentType.parse("text/javascript")
            )
        }
    }


    configureAuthRoutes(authRepository, httpClient)
    configureAdminRoutes(httpClient, repo, authRepository)
    configureNewestTabsRoutes(repo)
    configureMonthlyTabDownloadLinkRoutes(monthlyTabDownloadLinksRepository)
    configureEarlyAccessRoutes(authRepository, repo, cloudinaryService, httpClient)
    configureImportRoutes(httpClient, repo, authRepository)
    configureDonationRouting(authRepository)
    configureFavoriteRoutes(authRepository, favoritesRepository)
    configureTabRequestRoutes(authRepository, tabRequestsRepository)
    configureYoutubeMemberRoutes(authRepository, httpClient)
    configureBetaFeedbackRoutes(betaFeedbackRepo)

    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
    }
}

private data class CorsOrigin(val scheme: String, val host: String)

private fun allowedFrontendOrigins(): List<CorsOrigin> {
    val defaultOrigins = listOf(
        "https://dc-guitar.com",
        "https://www.dc-guitar.com",
        "https://guitar-trainer-static-site.onrender.com",
        "https://dc-frontend-q87t.onrender.com",
        "https://dc-frontend-yy6w.onrender.com",
        "http://localhost:8080",
        "http://127.0.0.1:8080",
        "http://localhost:5173",
        "http://0.0.0.0:8080",
    )

    return (defaultOrigins + frontendOriginsFromEnv())
        .mapNotNull(::parseCorsOrigin)
        .distinct()
}

private fun frontendOriginsFromEnv(): List<String> =
    listOf("FRONTEND_ORIGINS", "FRONTEND_ORIGIN", "APP_PUBLIC_URL")
        .flatMap { envName ->
            System.getenv(envName)
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        }

private fun parseCorsOrigin(value: String): CorsOrigin? {
    val uri = try {
        URI(value)
    } catch (_: Exception) {
        return null
    }

    val scheme = uri.scheme ?: return null
    val host = uri.host ?: return null
    val hostWithPort = if (uri.port == -1) host else "$host:${uri.port}"
    return CorsOrigin(scheme = scheme, host = hostWithPort)
}
