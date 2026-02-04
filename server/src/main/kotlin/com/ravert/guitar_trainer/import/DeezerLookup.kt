package com.ravert.guitar_trainer.import

import io.ktor.client.HttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

suspend fun deezerLookupTrack(
    http: HttpClient,
    songName: String,
    artistName: String
): DeezerTrack? {
    val query = """track:"$songName" artist:"$artistName""""

    val resp: DeezerSearchResponse = http.get("https://api.deezer.com/search") {
        parameter("q", query)      // Ktor encodes for you
        parameter("limit", "1")
        accept(ContentType.Application.Json)
    }.body()

    return resp.data.firstOrNull()
}
