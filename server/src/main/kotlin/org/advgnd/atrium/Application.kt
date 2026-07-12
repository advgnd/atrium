package org.advgnd.atrium

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.http.*
import org.advgnd.atrium.database.DatabaseManager
import org.advgnd.atrium.database.ExposedSessionStorage
import org.advgnd.atrium.model.UserSession
import org.advgnd.atrium.security.PasswordHasher
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database

@Serializable
data class AuthRequest(val email: String, val password: String)

@Serializable
data class MessageResponse(val message: String)

@Serializable
data class ProfileResponse(val userId: String, val email: String, val roles: List<String>)

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    val dbUrl = "jdbc:sqlite:atrium.db"
    val database = Database.connect(dbUrl, driver = "org.sqlite.JDBC")
    val dbManager = DatabaseManager(database)

    install(Sessions) {
        cookie<UserSession>("user_session", ExposedSessionStorage(database)) {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 3600
            cookie.httpOnly = true
            cookie.secure = false
        }
    }

    install(Authentication) {
        session<UserSession>("auth-session") {
            validate { session ->
                session
            }
        }
    }

    routing {
        get("/") {
            call.respondText("Ktor")
        }

        route("/api/v1") {
            post("/auth/register") {
                try {
                    val req = call.receive<AuthRequest>()
                    if (req.email.isBlank() || req.password.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, MessageResponse("Email and password are required"))
                        return@post
                    }

                    val existingUser = dbManager.findUserByEmail(req.email)
                    if (existingUser != null) {
                        call.respond(HttpStatusCode.BadRequest, MessageResponse("User already exists"))
                        return@post
                    }

                    val passwordHash = PasswordHasher.hash(req.password)
                    dbManager.createUser(req.email, passwordHash, listOf("USER"))

                    call.respond(HttpStatusCode.Created, MessageResponse("User registered successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Registration failed"))
                }
            }

            post("/auth/login") {
                try {
                    val req = call.receive<AuthRequest>()
                    val user = dbManager.findUserByEmail(req.email)
                    if (user == null || !PasswordHasher.verify(req.password, user.passwordHash)) {
                        call.respond(HttpStatusCode.Unauthorized, MessageResponse("Invalid credentials"))
                        return@post
                    }

                    val userSession = UserSession(
                        userId = user.id,
                        email = user.email,
                        roles = user.roles
                    )

                    call.sessions.set(userSession)
                    call.respond(HttpStatusCode.OK, MessageResponse("Login successful"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.Unauthorized, MessageResponse(e.message ?: "Invalid credentials"))
                }
            }

            post("/auth/logout") {
                call.sessions.clear<UserSession>()
                call.respond(HttpStatusCode.OK, MessageResponse("Logout successful"))
            }

            authenticate("auth-session") {
                get("/user/profile") {
                    val session = call.principal<UserSession>()
                    if (session != null) {
                        call.respond(
                            HttpStatusCode.OK, ProfileResponse(
                                userId = session.userId,
                                email = session.email,
                                roles = session.roles
                            )
                        )
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, MessageResponse("Not authenticated"))
                    }
                }
            }
        }
    }
}
