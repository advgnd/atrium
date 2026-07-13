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
import org.advgnd.atrium.database.Prescription
import org.advgnd.atrium.database.VisitAttachment
import org.advgnd.atrium.database.PharmacyOrderItemRequest
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

@Serializable
data class PatientRequest(
    val name: String,
    val dateOfBirth: String,
    val gender: String,
    val contactNumber: String,
    val email: String,
    val address: String
)

@Serializable
data class VisitRequest(
    val type: String,
    val requiredPaymentAmount: Double,
    val symptoms: List<String> = emptyList(),
    val diagnoses: List<String> = emptyList(),
    val treatments: List<String> = emptyList(),
    val prescriptions: List<Prescription> = emptyList(),
    val attachments: List<VisitAttachment> = emptyList(),
    val amountPhonePe: Double = 0.0,
    val amountCash: Double = 0.0
)

@Serializable
data class InventoryUpdateRequest(
    val medicationName: String,
    val quantity: Int,
    val pricePerUnit: Double
)

@Serializable
data class PharmacyOrderRequest(
    val items: List<PharmacyOrderItemRequest>,
    val amountPhonePe: Double = 0.0,
    val amountCash: Double = 0.0
)

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
            call.respondText(sayHello("Ktor"))
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
                        call.respond(HttpStatusCode.OK, ProfileResponse(
                            userId = session.userId,
                            email = session.email,
                            roles = session.roles
                        ))
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, MessageResponse("Not authenticated"))
                    }
                }

                post("/patients") {
                    try {
                        val req = call.receive<PatientRequest>()
                        val patient = dbManager.createPatient(
                            name = req.name,
                            dateOfBirth = req.dateOfBirth,
                            gender = req.gender,
                            contactNumber = req.contactNumber,
                            email = req.email,
                            address = req.address
                        )
                        call.respond(HttpStatusCode.Created, patient)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Failed to add patient"))
                    }
                }

                get("/patients") {
                    val patients = dbManager.getAllPatients()
                    call.respond(HttpStatusCode.OK, patients)
                }

                get("/patients/{id}") {
                    val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, MessageResponse("Missing patient ID"))
                    val patient = dbManager.getPatient(id)
                    if (patient != null) {
                        call.respond(HttpStatusCode.OK, patient)
                    } else {
                        call.respond(HttpStatusCode.NotFound, MessageResponse("Patient not found"))
                    }
                }

                post("/patients/{id}/visits") {
                    val patientId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Missing patient ID"))
                    try {
                        val req = call.receive<VisitRequest>()
                        val userSession = call.principal<UserSession>() ?: return@post call.respond(HttpStatusCode.Unauthorized, MessageResponse("Not authenticated"))
                        
                        val patient = dbManager.getPatient(patientId)
                        if (patient == null) {
                            call.respond(HttpStatusCode.NotFound, MessageResponse("Patient not found"))
                            return@post
                        }

                        val visit = dbManager.createVisit(
                            patientId = patientId,
                            type = req.type,
                            requiredPaymentAmount = req.requiredPaymentAmount,
                            symptoms = req.symptoms,
                            diagnoses = req.diagnoses,
                            treatments = req.treatments,
                            prescriptions = req.prescriptions,
                            attachments = req.attachments,
                            amountPhonePe = req.amountPhonePe,
                            amountCash = req.amountCash,
                            createdBy = userSession.userId
                        )
                        call.respond(HttpStatusCode.Created, visit)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Failed to record clinic visit"))
                    }
                }

                get("/patients/{id}/visits") {
                    val patientId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, MessageResponse("Missing patient ID"))
                    val patient = dbManager.getPatient(patientId)
                    if (patient == null) {
                        call.respond(HttpStatusCode.NotFound, MessageResponse("Patient not found"))
                        return@get
                    }
                    
                    val visits = dbManager.getVisitsForPatient(patientId)
                    call.respond(HttpStatusCode.OK, visits)
                }

                get("/visits") {
                    val visits = dbManager.getAllVisits()
                    call.respond(HttpStatusCode.OK, visits)
                }

                post("/inventory") {
                    try {
                        val req = call.receive<InventoryUpdateRequest>()
                        val item = dbManager.updateInventoryItem(
                            medicationName = req.medicationName,
                            quantity = req.quantity,
                            pricePerUnit = req.pricePerUnit
                        )
                        call.respond(HttpStatusCode.OK, item)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Failed to update inventory"))
                    }
                }

                get("/inventory") {
                    val inventory = dbManager.getAllInventory()
                    call.respond(HttpStatusCode.OK, inventory)
                }

                post("/visits/{id}/pharmacy-orders") {
                    val visitId = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest, MessageResponse("Missing visit ID"))
                    try {
                        val req = call.receive<PharmacyOrderRequest>()
                        val visit = dbManager.getVisit(visitId)
                        if (visit == null) {
                            call.respond(HttpStatusCode.NotFound, MessageResponse("Visit not found"))
                            return@post
                        }

                        val order = dbManager.createPharmacyOrder(
                            visitId = visitId,
                            items = req.items,
                            amountPhonePe = req.amountPhonePe,
                            amountCash = req.amountCash
                        )
                        call.respond(HttpStatusCode.Created, order)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Failed to create pharmacy order"))
                    }
                }

                get("/visits/{id}/pharmacy-orders") {
                    val visitId = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest, MessageResponse("Missing visit ID"))
                    val visit = dbManager.getVisit(visitId)
                    if (visit == null) {
                        call.respond(HttpStatusCode.NotFound, MessageResponse("Visit not found"))
                        return@get
                    }

                    val orders = dbManager.getPharmacyOrdersForVisit(visitId)
                    call.respond(HttpStatusCode.OK, orders)
                }
            }
        }
    }
}
