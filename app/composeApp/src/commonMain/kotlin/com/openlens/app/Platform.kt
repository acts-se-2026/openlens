package com.openlens.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
