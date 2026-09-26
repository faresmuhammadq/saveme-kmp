package com.fares.saveme

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform