package com.ravert.guitar_trainer

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform