package org.advgnd.atrium.database

import io.ktor.server.sessions.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class ExposedSessionStorage(private val database: Database) : SessionStorage {
    init {
        transaction(database) {
            SchemaUtils.create(Sessions)
        }
    }

    override suspend fun write(id: String, value: String): Unit = withContext(Dispatchers.IO) {
        transaction(database) {
            Sessions.deleteWhere { Sessions.id eq id }
            Sessions.insert {
                it[Sessions.id] = id
                it[Sessions.sessionData] = value
            }
        }
    }

    override suspend fun read(id: String): String = withContext(Dispatchers.IO) {
        transaction(database) {
            Sessions.selectAll().where { Sessions.id eq id }
                .map { it[Sessions.sessionData] }
                .singleOrNull()
        } ?: throw NoSuchElementException("Session $id not found")
    }

    override suspend fun invalidate(id: String): Unit = withContext(Dispatchers.IO) {
        transaction(database) {
            Sessions.deleteWhere { Sessions.id eq id }
        }
    }
}
