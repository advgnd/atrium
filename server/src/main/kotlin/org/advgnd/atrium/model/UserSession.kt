package org.advgnd.atrium.model

import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val userId: String,
    val email: String,
    val roles: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
