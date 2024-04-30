package com.example.mathlibguide

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform