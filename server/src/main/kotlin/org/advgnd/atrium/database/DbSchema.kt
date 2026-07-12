package org.advgnd.atrium.database

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ReferenceOption

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

object Patients : Table("patients") {
    val id = text("id")
    val name = text("name")
    val dateOfBirth = text("date_of_birth")
    val gender = text("gender")
    val contactNumber = text("contact_number")
    val email = text("email")
    val address = text("address")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Visits : Table("visits") {
    val id = text("id")
    val patientId = text("patient_id").references(Patients.id)
    val type = text("type")
    val requiredPaymentAmount = double("required_payment_amount")
    val amountPhonePe = double("amount_phone_pe")
    val amountCash = double("amount_cash")
    val paymentStatus = text("payment_status")
    val transactionId = text("transaction_id")
    val createdBy = text("created_by").references(Users.id)
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

object VisitSymptoms : Table("visit_symptoms") {
    val id = text("id")
    val visitId = text("visit_id").references(Visits.id, onDelete = ReferenceOption.CASCADE)
    val symptom = text("symptom")
    override val primaryKey = PrimaryKey(id)
}

object VisitDiagnoses : Table("visit_diagnoses") {
    val id = text("id")
    val visitId = text("visit_id").references(Visits.id, onDelete = ReferenceOption.CASCADE)
    val diagnosis = text("diagnosis")
    override val primaryKey = PrimaryKey(id)
}

object VisitTreatments : Table("visit_treatments") {
    val id = text("id")
    val visitId = text("visit_id").references(Visits.id, onDelete = ReferenceOption.CASCADE)
    val treatment = text("treatment")
    override val primaryKey = PrimaryKey(id)
}

object VisitPrescriptions : Table("visit_prescriptions") {
    val id = text("id")
    val visitId = text("visit_id").references(Visits.id, onDelete = ReferenceOption.CASCADE)
    val medicationName = text("medication_name")
    val dosage = text("dosage")
    val frequency = text("frequency")
    val duration = text("duration")
    override val primaryKey = PrimaryKey(id)
}

object VisitAttachments : Table("visit_attachments") {
    val id = text("id")
    val visitId = text("visit_id").references(Visits.id, onDelete = ReferenceOption.CASCADE)
    val url = text("url")
    val mediaType = text("media_type")
    override val primaryKey = PrimaryKey(id)
}
