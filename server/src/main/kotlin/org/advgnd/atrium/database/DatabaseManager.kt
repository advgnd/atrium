package org.advgnd.atrium.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

data class DbUser(
    val id: String,
    val email: String,
    val passwordHash: String,
    val roles: List<String>
)

class DatabaseManager(private val database: Database) {
    init {
        transaction(database) {
            SchemaUtils.create(Users)
        }
    }

    fun createUser(email: String, passwordHash: String, roles: List<String>): DbUser {
        val id = UUID.randomUUID().toString()
        val rolesStr = roles.joinToString(",")

        transaction(database) {
            Users.insert {
                it[Users.id] = id
                it[Users.email] = email
                it[Users.passwordHash] = passwordHash
                it[Users.roles] = rolesStr
            }
        }
        return DbUser(id, email, passwordHash, roles)
    }

    fun findUserByEmail(email: String): DbUser? {
        return transaction(database) {
            Users.selectAll().where { Users.email eq email }
                .map {
                    val rolesStr = it[Users.roles]
                    val roles = if (rolesStr.isEmpty()) emptyList() else rolesStr.split(",")
                    DbUser(
                        id = it[Users.id],
                        email = it[Users.email],
                        passwordHash = it[Users.passwordHash],
                        roles = roles
                    )
                }
                .singleOrNull()
        }
    }
}
