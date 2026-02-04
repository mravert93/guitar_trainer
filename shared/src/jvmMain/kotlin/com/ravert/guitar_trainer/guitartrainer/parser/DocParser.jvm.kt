package com.ravert.guitar_trainer.guitartrainer.parser

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

private val httpClient = HttpClient(CIO)

actual suspend fun fetchDocHtml(url: String): String {
    return httpClient.get(url).bodyAsText()
}