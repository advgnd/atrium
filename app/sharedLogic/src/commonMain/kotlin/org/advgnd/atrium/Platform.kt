package org.advgnd.atrium

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform