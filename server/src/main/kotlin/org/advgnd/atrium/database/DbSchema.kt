package org.advgnd.atrium.database

import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val id = text("id")
    val email = text("email").uniqueIndex()
    val passwordHash = text("password_hash")
    val roles = text("roles")
    override val primaryKey = PrimaryKey(id)
}

object Sessions : Table("sessions") {
    val id = text("id")
    val sessionData = text("session_data")
    override val primaryKey = PrimaryKey(id)
}
