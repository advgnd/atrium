package org.advgnd.atrium

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.http.content.staticFiles
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.server.request.receiveMultipart
import java.io.File
import java.util.UUID
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.http.*
import io.ktor.server.resources.*
import io.ktor.server.resources.Resources
import io.ktor.server.resources.post
import io.ktor.server.resources.get
import io.ktor.server.resources.put
import org.advgnd.atrium.database.DatabaseManager
import org.advgnd.atrium.database.ExposedSessionStorage
import org.advgnd.atrium.model.UserSession
import org.advgnd.atrium.security.PasswordHasher
import org.jetbrains.exposed.sql.Database
import dev.nesk.akkurate.Validator
import dev.nesk.akkurate.ValidationResult
import dev.nesk.akkurate.constraints.builders.*
import dev.nesk.akkurate.constraints.otherwise
import dev.nesk.akkurate.constraints.*
import org.advgnd.atrium.validation.accessors.*
import dev.nesk.akkurate.validatables.*
import java.time.LocalDate
import java.time.format.DateTimeParseException

val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
val contactRegex = Regex("^[0-9\\s\\-]+$")

val validateAuth = Validator<AuthRequest> {
    email.constrain { it.matches(emailRegex) } otherwise { "Invalid email address format" }
    password.hasLengthGreaterThanOrEqualTo(8) otherwise { "Password must be at least 8 characters long" }
    password.constrain { it.any { c -> c.isLetter() } && it.any { c -> c.isDigit() } } otherwise { "Password must contain both letters and digits" }
}

val validatePatient = Validator<PatientRequest> {
    name.isNotEmpty() otherwise { "Name cannot be empty" }
    name.isNotBlank() otherwise { "Name cannot be blank" }
    dateOfBirth.constrain {
        try {
            val dob = LocalDate.parse(it)
            !dob.isAfter(LocalDate.now())
        } catch (e: DateTimeParseException) {
            false
        }
    } otherwise { "Date of Birth must be a valid date in the past (YYYY-MM-DD)" }
    gender.constrain { it.equals("Male", ignoreCase = true) || it.equals("Female", ignoreCase = true) || it.equals("Other", ignoreCase = true) } otherwise {
        "Gender must be Male, Female, or Other"
    }
    contactNumber.constrain { it.matches(contactRegex) && it.replace(Regex("[^0-9]"), "").length >= 10 } otherwise {
        "Contact number must contain at least 10 digits"
    }
    email.constrain { it.isBlank() || it.matches(emailRegex) } otherwise { "Invalid email address format" }
}

val validateVisit = Validator<VisitRequest> {
    type.isNotEmpty() otherwise { "Visit type cannot be empty" }
    type.isNotBlank() otherwise { "Visit type cannot be blank" }
    requiredPaymentAmount.constrain { it >= 0.0 } otherwise { "Required payment amount must be non-negative" }
    prescriptions.constrain { list -> list.all { it.quantity > 0 } } otherwise { "Each prescription quantity must be at least 1" }
}

val validateInventoryUpdate = Validator<InventoryUpdateRequest> {
    medicationName.isNotEmpty() otherwise { "Medication name cannot be empty" }
    medicationName.isNotBlank() otherwise { "Medication name cannot be blank" }
    quantity.constrain { it >= 0 } otherwise { "Quantity must be non-negative" }
    pricePerUnit.constrain { it >= 0.0 } otherwise { "Price per unit must be non-negative" }
}

val validatePayment = Validator<PaymentRequest> {
    amountPhonePe.constrain { it >= 0.0 } otherwise { "PhonePe amount must be non-negative" }
    amountCash.constrain { it >= 0.0 } otherwise { "Cash amount must be non-negative" }
    constrain { it.amountPhonePe >= 0.0 && it.amountCash >= 0.0 && (it.amountPhonePe + it.amountCash) > 0.0 } otherwise {
        "At least one payment amount must be greater than zero"
    }
}

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
        staticFiles("/uploads", File("uploads"))

        post<ApiV1.Upload> {
            try {
                val multipart = call.receiveMultipart()
                var fileBytes: ByteArray? = null
                var fileName = ""
                
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        fileName = part.originalFileName ?: "file"
                        fileBytes = part.streamProvider().readBytes()
                    }
                    part.dispose()
                }
                
                if (fileBytes != null) {
                    val uploadsDir = File("uploads")
                    if (!uploadsDir.exists()) {
                        uploadsDir.mkdirs()
                    }
                    val ext = File(fileName).extension
                    val cleanExt = if (ext.isNotEmpty()) ".$ext" else ""
                    val uniqueName = "${UUID.randomUUID()}$cleanExt"
                    val destinationFile = File(uploadsDir, uniqueName)
                    destinationFile.writeBytes(fileBytes!!)
                    
                    val relativeUrl = "/uploads/$uniqueName"
                    call.respond(HttpStatusCode.OK, mapOf("url" to relativeUrl, "fileName" to fileName))
                } else {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse("No file uploaded"))
                }
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, MessageResponse(e.message ?: "Upload failed"))
            }
        }

        get("/") {
            call.respondText(sayHello("Ktor"))
        }

        post<ApiV1.Register> {
            try {
                val req = call.receive<AuthRequest>()
                val validation = validateAuth(req)
                if (validation is ValidationResult.Failure) {
                    val errors = validation.violations.map { ValidationError(it.path.joinToString("."), it.message) }
                    call.respond(HttpStatusCode.BadRequest, ValidationErrorsResponse(errors))
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
                val validation = validateAuth(req)
                if (validation is ValidationResult.Failure) {
                    val errors = validation.violations.map { ValidationError(it.path.joinToString("."), it.message) }
                    call.respond(HttpStatusCode.BadRequest, ValidationErrorsResponse(errors))
                    return@post
                }

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
                    val validation = validatePatient(req)
                    if (validation is ValidationResult.Failure) {
                        val errors = validation.violations.map { ValidationError(it.path.joinToString("."), it.message) }
                        call.respond(HttpStatusCode.BadRequest, ValidationErrorsResponse(errors))
                        return@post
                    }

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

            put<ApiV1.PatientDetail> { route ->
                try {
                    val req = call.receive<PatientRequest>()
                    val validation = validatePatient(req)
                    if (validation is ValidationResult.Failure) {
                        val errors = validation.violations.map { ValidationError(it.path.joinToString("."), it.message) }
                        call.respond(HttpStatusCode.BadRequest, ValidationErrorsResponse(errors))
                        return@put
                    }
                    val updated = dbManager.updatePatient(
                        route.id, req.name, req.dateOfBirth, req.gender, req.contactNumber, req.email, req.address
                    )
                    if (updated != null) {
                        call.respond(HttpStatusCode.OK, updated)
                    } else {
                        call.respond(HttpStatusCode.NotFound, MessageResponse("Patient not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Invalid request"))
                }
            }

            post<ApiV1.PatientVisits> { route ->
                try {
                    val req = call.receive<VisitRequest>()
                    val validation = validateVisit(req)
                    if (validation is ValidationResult.Failure) {
                        val errors = validation.violations.map { ValidationError(it.path.joinToString("."), it.message) }
                        call.respond(HttpStatusCode.BadRequest, ValidationErrorsResponse(errors))
                        return@post
                    }

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
                        createdBy = userSession.userId,
                        notes = req.notes
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

            get<ApiV1.Visits.VisitTypes> {
                val types = listOf(
                    VisitTypeDto("General ENT Consultation", 500.0),
                    VisitTypeDto("Diagnostic Nasal Endoscopy", 1500.0),
                    VisitTypeDto("Ear Wax Removal (Micro-suction)", 400.0),
                    VisitTypeDto("Hearing Test (Audiometry)", 800.0),
                    VisitTypeDto("Tympanoplasty Follow-up", 300.0),
                    VisitTypeDto("Allergy Skin Prick Test", 1200.0)
                )
                call.respond(HttpStatusCode.OK, types)
            }

            get<ApiV1.VisitDetail> { route ->
                val visit = dbManager.getVisit(route.id)
                if (visit != null) {
                    call.respond(HttpStatusCode.OK, visit)
                } else {
                    call.respond(HttpStatusCode.NotFound, MessageResponse("Visit not found"))
                }
            }

            put<ApiV1.VisitDetail> { route ->
                try {
                    val req = call.receive<VisitRequest>()
                    val validation = validateVisit(req)
                    if (validation is ValidationResult.Failure) {
                        val errors = validation.violations.map { ValidationError(it.path.joinToString("."), it.message) }
                        call.respond(HttpStatusCode.BadRequest, ValidationErrorsResponse(errors))
                        return@put
                    }
                    val updated = dbManager.updateVisitDetails(
                        route.id, req.type, req.requiredPaymentAmount, req.symptoms, req.diagnoses, req.treatments, req.prescriptions, req.attachments, req.notes
                    )
                    if (updated != null) {
                        call.respond(HttpStatusCode.OK, updated)
                    } else {
                        call.respond(HttpStatusCode.NotFound, MessageResponse("Visit not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Invalid request"))
                }
            }

            post<ApiV1.Inventory> {
                try {
                    val req = call.receive<InventoryUpdateRequest>()
                    val validation = validateInventoryUpdate(req)
                    if (validation is ValidationResult.Failure) {
                        val errors = validation.violations.map { ValidationError(it.path.joinToString("."), it.message) }
                        call.respond(HttpStatusCode.BadRequest, ValidationErrorsResponse(errors))
                        return@post
                    }

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

            post<ApiV1.VisitPay> { route ->
                try {
                    val req = call.receive<PaymentRequest>()
                    val validation = validatePayment(req)
                    if (validation is ValidationResult.Failure) {
                        val errors = validation.violations.map { ValidationError(it.path.joinToString("."), it.message) }
                        call.respond(HttpStatusCode.BadRequest, ValidationErrorsResponse(errors))
                        return@post
                    }

                    val visit = dbManager.payVisit(
                        visitId = route.id,
                        amountPhonePe = req.amountPhonePe,
                        amountCash = req.amountCash
                    )
                    call.respond(HttpStatusCode.OK, visit)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Payment failed"))
                }
            }

            post<ApiV1.VisitDispense> { route ->
                try {
                    val req = call.receive<PaymentRequest>()
                    val validation = validatePayment(req)
                    if (validation is ValidationResult.Failure) {
                        val errors = validation.violations.map { ValidationError(it.path.joinToString("."), it.message) }
                        call.respond(HttpStatusCode.BadRequest, ValidationErrorsResponse(errors))
                        return@post
                    }

                    val visit = dbManager.dispensePharmacy(
                        visitId = route.id,
                        amountPhonePe = req.amountPhonePe,
                        amountCash = req.amountCash
                    )
                    call.respond(HttpStatusCode.OK, visit)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Dispensing failed"))
                }
            }
        }
    }
}
