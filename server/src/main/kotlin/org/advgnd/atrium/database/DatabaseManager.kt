package org.advgnd.atrium.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlinx.serialization.Serializable

data class DbUser(
    val id: String,
    val email: String,
    val passwordHash: String,
    val roles: List<String>
)

@Serializable
data class DbPatient(
    val id: String,
    val name: String,
    val dateOfBirth: String,
    val gender: String,
    val contactNumber: String,
    val email: String,
    val address: String,
    val createdAt: Long
)

@Serializable
data class Prescription(
    val medicationName: String,
    val dosage: String,
    val frequency: String,
    val duration: String
)

@Serializable
data class VisitAttachment(
    val url: String,
    val mediaType: String
)

@Serializable
data class DbVisit(
    val id: String,
    val patientId: String,
    val type: String,
    val requiredPaymentAmount: Double,
    val amountPhonePe: Double,
    val amountCash: Double,
    val paymentStatus: String,
    val transactionId: String,
    val symptoms: List<String>,
    val diagnoses: List<String>,
    val treatments: List<String>,
    val prescriptions: List<Prescription>,
    val attachments: List<VisitAttachment>,
    val createdBy: String,
    val createdAt: Long
)

class DatabaseManager(private val database: Database) {

    init {
        transaction(database) {
            SchemaUtils.create(
                Users, Patients, Visits,
                VisitSymptoms, VisitDiagnoses, VisitTreatments,
                VisitPrescriptions, VisitAttachments
            )
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

    fun createPatient(
        name: String,
        dateOfBirth: String,
        gender: String,
        contactNumber: String,
        email: String,
        address: String
    ): DbPatient {
        val id = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()

        transaction(database) {
            Patients.insert {
                it[Patients.id] = id
                it[Patients.name] = name
                it[Patients.dateOfBirth] = dateOfBirth
                it[Patients.gender] = gender
                it[Patients.contactNumber] = contactNumber
                it[Patients.email] = email
                it[Patients.address] = address
                it[Patients.createdAt] = createdAt
            }
        }
        return DbPatient(id, name, dateOfBirth, gender, contactNumber, email, address, createdAt)
    }

    fun getPatient(id: String): DbPatient? {
        return transaction(database) {
            Patients.selectAll().where { Patients.id eq id }
                .map {
                    DbPatient(
                        id = it[Patients.id],
                        name = it[Patients.name],
                        dateOfBirth = it[Patients.dateOfBirth],
                        gender = it[Patients.gender],
                        contactNumber = it[Patients.contactNumber],
                        email = it[Patients.email],
                        address = it[Patients.address],
                        createdAt = it[Patients.createdAt]
                    )
                }
                .singleOrNull()
        }
    }

    fun getAllPatients(): List<DbPatient> {
        return transaction(database) {
            Patients.selectAll().map {
                DbPatient(
                    id = it[Patients.id],
                    name = it[Patients.name],
                    dateOfBirth = it[Patients.dateOfBirth],
                    gender = it[Patients.gender],
                    contactNumber = it[Patients.contactNumber],
                    email = it[Patients.email],
                    address = it[Patients.address],
                    createdAt = it[Patients.createdAt]
                )
            }
        }
    }

    fun createVisit(
        patientId: String,
        type: String,
        requiredPaymentAmount: Double,
        symptoms: List<String>,
        diagnoses: List<String>,
        treatments: List<String>,
        prescriptions: List<Prescription>,
        attachments: List<VisitAttachment>,
        amountPhonePe: Double,
        amountCash: Double,
        createdBy: String
    ): DbVisit {
        val visitId = UUID.randomUUID().toString()
        val transactionId = "TXN-" + UUID.randomUUID().toString().take(8).uppercase()
        val createdAt = System.currentTimeMillis()

        val totalPaid = amountPhonePe + amountCash
        val paymentStatus = if (totalPaid >= requiredPaymentAmount) "PAID" else "PENDING"

        transaction(database) {
            Visits.insert {
                it[Visits.id] = visitId
                it[Visits.patientId] = patientId
                it[Visits.type] = type
                it[Visits.requiredPaymentAmount] = requiredPaymentAmount
                it[Visits.amountPhonePe] = amountPhonePe
                it[Visits.amountCash] = amountCash
                it[Visits.paymentStatus] = paymentStatus
                it[Visits.transactionId] = transactionId
                it[Visits.createdBy] = createdBy
                it[Visits.createdAt] = createdAt
            }

            symptoms.forEach { sym ->
                VisitSymptoms.insert {
                    it[VisitSymptoms.id] = UUID.randomUUID().toString()
                    it[VisitSymptoms.visitId] = visitId
                    it[VisitSymptoms.symptom] = sym
                }
            }

            diagnoses.forEach { diag ->
                VisitDiagnoses.insert {
                    it[VisitDiagnoses.id] = UUID.randomUUID().toString()
                    it[VisitDiagnoses.visitId] = visitId
                    it[VisitDiagnoses.diagnosis] = diag
                }
            }

            treatments.forEach { treat ->
                VisitTreatments.insert {
                    it[VisitTreatments.id] = UUID.randomUUID().toString()
                    it[VisitTreatments.visitId] = visitId
                    it[VisitTreatments.treatment] = treat
                }
            }

            prescriptions.forEach { presc ->
                VisitPrescriptions.insert {
                    it[VisitPrescriptions.id] = UUID.randomUUID().toString()
                    it[VisitPrescriptions.visitId] = visitId
                    it[VisitPrescriptions.medicationName] = presc.medicationName
                    it[VisitPrescriptions.dosage] = presc.dosage
                    it[VisitPrescriptions.frequency] = presc.frequency
                    it[VisitPrescriptions.duration] = presc.duration
                }
            }

            attachments.forEach { att ->
                VisitAttachments.insert {
                    it[VisitAttachments.id] = UUID.randomUUID().toString()
                    it[VisitAttachments.visitId] = visitId
                    it[VisitAttachments.url] = att.url
                    it[VisitAttachments.mediaType] = att.mediaType
                }
            }
        }

        return DbVisit(
            id = visitId,
            patientId = patientId,
            type = type,
            requiredPaymentAmount = requiredPaymentAmount,
            amountPhonePe = amountPhonePe,
            amountCash = amountCash,
            paymentStatus = paymentStatus,
            transactionId = transactionId,
            symptoms = symptoms,
            diagnoses = diagnoses,
            treatments = treatments,
            prescriptions = prescriptions,
            attachments = attachments,
            createdBy = createdBy,
            createdAt = createdAt
        )
    }

    fun getVisitsForPatient(patientId: String): List<DbVisit> {
        return transaction(database) {
            Visits.selectAll().where { Visits.patientId eq patientId }
                .orderBy(Visits.createdAt to SortOrder.DESC)
                .map { row ->
                    val visitId = row[Visits.id]

                    val symptoms = VisitSymptoms.selectAll().where { VisitSymptoms.visitId eq visitId }
                        .map { it[VisitSymptoms.symptom] }

                    val diagnoses = VisitDiagnoses.selectAll().where { VisitDiagnoses.visitId eq visitId }
                        .map { it[VisitDiagnoses.diagnosis] }

                    val treatments = VisitTreatments.selectAll().where { VisitTreatments.visitId eq visitId }
                        .map { it[VisitTreatments.treatment] }

                    val prescriptions = VisitPrescriptions.selectAll().where { VisitPrescriptions.visitId eq visitId }
                        .map {
                            Prescription(
                                medicationName = it[VisitPrescriptions.medicationName],
                                dosage = it[VisitPrescriptions.dosage],
                                frequency = it[VisitPrescriptions.frequency],
                                duration = it[VisitPrescriptions.duration]
                            )
                        }

                    val attachments = VisitAttachments.selectAll().where { VisitAttachments.visitId eq visitId }
                        .map {
                            VisitAttachment(
                                url = it[VisitAttachments.url],
                                mediaType = it[VisitAttachments.mediaType]
                            )
                        }

                    DbVisit(
                        id = visitId,
                        patientId = row[Visits.patientId],
                        type = row[Visits.type],
                        requiredPaymentAmount = row[Visits.requiredPaymentAmount],
                        amountPhonePe = row[Visits.amountPhonePe],
                        amountCash = row[Visits.amountCash],
                        paymentStatus = row[Visits.paymentStatus],
                        transactionId = row[Visits.transactionId],
                        symptoms = symptoms,
                        diagnoses = diagnoses,
                        treatments = treatments,
                        prescriptions = prescriptions,
                        attachments = attachments,
                        createdBy = row[Visits.createdBy],
                        createdAt = row[Visits.createdAt]
                    )
                }
        }
    }

    fun getAllVisits(): List<DbVisit> {
        return transaction(database) {
            Visits.selectAll()
                .orderBy(Visits.createdAt to SortOrder.DESC)
                .map { row ->
                    val visitId = row[Visits.id]

                    val symptoms = VisitSymptoms.selectAll().where { VisitSymptoms.visitId eq visitId }
                        .map { it[VisitSymptoms.symptom] }

                    val diagnoses = VisitDiagnoses.selectAll().where { VisitDiagnoses.visitId eq visitId }
                        .map { it[VisitDiagnoses.diagnosis] }

                    val treatments = VisitTreatments.selectAll().where { VisitTreatments.visitId eq visitId }
                        .map { it[VisitTreatments.treatment] }

                    val prescriptions = VisitPrescriptions.selectAll().where { VisitPrescriptions.visitId eq visitId }
                        .map {
                            Prescription(
                                medicationName = it[VisitPrescriptions.medicationName],
                                dosage = it[VisitPrescriptions.dosage],
                                frequency = it[VisitPrescriptions.frequency],
                                duration = it[VisitPrescriptions.duration]
                            )
                        }

                    val attachments = VisitAttachments.selectAll().where { VisitAttachments.visitId eq visitId }
                        .map {
                            VisitAttachment(
                                url = it[VisitAttachments.url],
                                mediaType = it[VisitAttachments.mediaType]
                            )
                        }

                    DbVisit(
                        id = visitId,
                        patientId = row[Visits.patientId],
                        type = row[Visits.type],
                        requiredPaymentAmount = row[Visits.requiredPaymentAmount],
                        amountPhonePe = row[Visits.amountPhonePe],
                        amountCash = row[Visits.amountCash],
                        paymentStatus = row[Visits.paymentStatus],
                        transactionId = row[Visits.transactionId],
                        symptoms = symptoms,
                        diagnoses = diagnoses,
                        treatments = treatments,
                        prescriptions = prescriptions,
                        attachments = attachments,
                        createdBy = row[Visits.createdBy],
                        createdAt = row[Visits.createdAt]
                    )
                }
        }
    }
}
