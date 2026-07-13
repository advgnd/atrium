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
import io.ktor.server.resources.*
import io.ktor.server.resources.Resources
import io.ktor.server.resources.post
import io.ktor.server.resources.get
import org.advgnd.atrium.database.DatabaseManager
import org.advgnd.atrium.database.ExposedSessionStorage
import org.advgnd.atrium.*
import org.advgnd.atrium.model.UserSession
import org.advgnd.atrium.security.PasswordHasher
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Database

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    install(Resources)

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

        post<ApiV1.Register> {
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

        post<ApiV1.Login> {
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

        post<ApiV1.Logout> {
            call.sessions.clear<UserSession>()
            call.respond(HttpStatusCode.OK, MessageResponse("Logout successful"))
        }

        authenticate("auth-session") {
            get<ApiV1.Profile> {
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

            post<ApiV1.Patients> {
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

            get<ApiV1.Patients> {
                val patients = dbManager.getAllPatients()
                call.respond(HttpStatusCode.OK, patients)
            }

            get<ApiV1.PatientDetail> { route ->
                val patient = dbManager.getPatient(route.id)
                if (patient != null) {
                    call.respond(HttpStatusCode.OK, patient)
                } else {
                    call.respond(HttpStatusCode.NotFound, MessageResponse("Patient not found"))
                }
            }

            post<ApiV1.PatientVisits> { route ->
                try {
                    val req = call.receive<VisitRequest>()
                    val userSession = call.principal<UserSession>() ?: return@post call.respond(HttpStatusCode.Unauthorized, MessageResponse("Not authenticated"))
                    
                    val patient = dbManager.getPatient(route.id)
                    if (patient == null) {
                        call.respond(HttpStatusCode.NotFound, MessageResponse("Patient not found"))
                        return@post
                    }

                    val visit = dbManager.createVisit(
                        patientId = route.id,
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

            get<ApiV1.PatientVisits> { route ->
                val patient = dbManager.getPatient(route.id)
                if (patient == null) {
                    call.respond(HttpStatusCode.NotFound, MessageResponse("Patient not found"))
                    return@get
                }
                
                val visits = dbManager.getVisitsForPatient(route.id)
                call.respond(HttpStatusCode.OK, visits)
            }

            get<ApiV1.Visits> {
                val visits = dbManager.getAllVisits()
                call.respond(HttpStatusCode.OK, visits)
            }

            post<ApiV1.Inventory> {
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

            get<ApiV1.Inventory> {
                val inventory = dbManager.getAllInventory()
                call.respond(HttpStatusCode.OK, inventory)
            }

            post<ApiV1.VisitPharmacyOrders> { route ->
                try {
                    val req = call.receive<PharmacyOrderRequest>()
                    val visit = dbManager.getVisit(route.id)
                    if (visit == null) {
                        call.respond(HttpStatusCode.NotFound, MessageResponse("Visit not found"))
                        return@post
                    }

                    val order = dbManager.createPharmacyOrder(
                        visitId = route.id,
                        items = req.items,
                        amountPhonePe = req.amountPhonePe,
                        amountCash = req.amountCash
                    )
                    call.respond(HttpStatusCode.Created, order)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Failed to create pharmacy order"))
                }
            }

            get<ApiV1.VisitPharmacyOrders> { route ->
                val visit = dbManager.getVisit(route.id)
                if (visit == null) {
                    call.respond(HttpStatusCode.NotFound, MessageResponse("Visit not found"))
                    return@get
                }

                val orders = dbManager.getPharmacyOrdersForVisit(route.id)
                call.respond(HttpStatusCode.OK, orders)
            }
        }
    }
}
