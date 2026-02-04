package com.ravert.guitar_trainer

import com.ravert.guitar_trainer.db.DatabaseFactory
import com.ravert.guitar_trainer.db.LibraryRepository
import com.ravert.guitar_trainer.routing.configureAdminRoutes
import com.ravert.guitar_trainer.routing.configureImportRoutes
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
import io.ktor.serialization.kotlinx.json.json

import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

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
        allowHost("https://guitar-trainer.onrender.com", schemes = listOf("https"))
        allowHost("localhost:8080", schemes = listOf("http"))
        allowHost("127.0.0.1:8080", schemes = listOf("http"))
        allowHost("0.0.0.0:8080")

        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Delete)

        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)

        allowCredentials = false
    }

    DatabaseFactory.init()
    val repo = LibraryRepository()

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


    configureAdminRoutes(httpClient, repo)
    configureImportRoutes(httpClient, repo)

    routing {
        get("/") {
            call.respondText("Ktor: ${Greeting().greet()}")
        }
    }
}