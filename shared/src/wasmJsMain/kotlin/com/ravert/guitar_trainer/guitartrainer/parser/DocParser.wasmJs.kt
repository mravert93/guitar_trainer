package com.ravert.guitar_trainer.guitartrainer.parser

import io.ktor.client.*
import io.ktor.client.engine.js.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

private val httpClient = HttpClient(Js)

actual suspend fun fetchDocHtml(url: String): String {
    return httpClient.get(url).bodyAsText()
}