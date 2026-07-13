package org.advgnd.atrium.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import org.advgnd.atrium.Prescription
import org.advgnd.atrium.VisitAttachment
import org.advgnd.atrium.PaymentStatus

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
data class DbVisit(
    val id: String,
    val patientId: String,
    val type: String,
    val requiredPaymentAmount: Double,
    val amountPhonePe: Double,
    val amountCash: Double,
    val paymentStatus: PaymentStatus,
    val symptoms: List<String>,
    val diagnoses: List<String>,
    val treatments: List<String>,
    val prescriptions: List<Prescription>,
    val attachments: List<VisitAttachment>,
    val createdBy: String,
    val createdAt: Long,
    val pharmacyRequiredAmount: Double,
    val pharmacyAmountPhonePe: Double,
    val pharmacyAmountCash: Double,
    val pharmacyPaymentStatus: PaymentStatus
)

@Serializable
data class DbInventoryItem(
    val id: String,
    val medicationName: String,
    val quantity: Int,
    val pricePerUnit: Double,
    val updatedAt: Long
)

class DatabaseManager(private val database: Database) {

    init {
        transaction(database) {
            SchemaUtils.create(
                Users, Patients, Visits,
                VisitSymptoms, VisitDiagnoses, VisitTreatments,
                VisitPrescriptions, VisitAttachments,
                Inventory
            )
        }
    }

    private fun generateUserFacingId(table: Table, column: Column<String>, createdAtColumn: Column<Long>): String {
        val startMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
        val maxVal = table.select(column).where { createdAtColumn greaterEq startMillis }
            .map { it[column].toIntOrNull() ?: -1 }
            .maxOrNull()

        val nextSeq = if (maxVal != null && maxVal >= 0) maxVal + 1 else 0
        return nextSeq.toString()
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
                    DbUser(
                        id = it[Users.id],
                        email = it[Users.email],
                        passwordHash = it[Users.passwordHash],
                        roles = it[Users.roles].split(",")
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

        return DbPatient(
            id = id,
            name = name,
            dateOfBirth = dateOfBirth,
            gender = gender,
            contactNumber = contactNumber,
            email = email,
            address = address,
            createdAt = createdAt
        )
    }

    fun getPatient(id: String): DbPatient? {
        return transaction(database) {
            Patients.selectAll().where { Patients.id eq id }
                .map { row ->
                    DbPatient(
                        id = row[Patients.id],
                        name = row[Patients.name],
                        dateOfBirth = row[Patients.dateOfBirth],
                        gender = row[Patients.gender],
                        contactNumber = row[Patients.contactNumber],
                        email = row[Patients.email],
                        address = row[Patients.address],
                        createdAt = row[Patients.createdAt]
                    )
                }.singleOrNull()
        }
    }

    fun getAllPatients(): List<DbPatient> {
        return transaction(database) {
            Patients.selectAll()
                .orderBy(Patients.createdAt to SortOrder.DESC)
                .map { row ->
                    DbPatient(
                        id = row[Patients.id],
                        name = row[Patients.name],
                        dateOfBirth = row[Patients.dateOfBirth],
                        gender = row[Patients.gender],
                        contactNumber = row[Patients.contactNumber],
                        email = row[Patients.email],
                        address = row[Patients.address],
                        createdAt = row[Patients.createdAt]
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
        createdBy: String
    ): DbVisit {
        val visitId = UUID.randomUUID().toString()
        val createdAt = System.currentTimeMillis()

        val userFacingId = transaction(database) {
            val patientRow = Patients.selectAll().where { Patients.id eq patientId }
                .singleOrNull() ?: throw IllegalArgumentException("Patient not found")
            val patientUuid = patientRow[Patients.id]

            val ufid = generateUserFacingId(Visits, Visits.userFacingId, Visits.createdAt)
            Visits.insert {
                it[Visits.id] = visitId
                it[Visits.userFacingId] = ufid
                it[Visits.patientId] = patientUuid
                it[Visits.type] = type
                it[Visits.requiredPaymentAmount] = requiredPaymentAmount
                it[Visits.amountPhonePe] = 0.0
                it[Visits.amountCash] = 0.0
                it[Visits.paymentStatus] = PaymentStatus.PENDING
                it[Visits.createdBy] = createdBy
                it[Visits.createdAt] = createdAt
                it[Visits.pharmacyAmountPhonePe] = 0.0
                it[Visits.pharmacyAmountCash] = 0.0
                it[Visits.pharmacyPaymentStatus] = PaymentStatus.PENDING
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
                val invRow = Inventory.selectAll().where { Inventory.id eq presc.medicationId }
                    .singleOrNull() ?: throw IllegalArgumentException("Medication ID ${presc.medicationId} not found in inventory")

                VisitPrescriptions.insert {
                    it[VisitPrescriptions.id] = UUID.randomUUID().toString()
                    it[VisitPrescriptions.visitId] = visitId
                    it[VisitPrescriptions.medicationId] = presc.medicationId
                    it[VisitPrescriptions.quantity] = presc.quantity
                    it[VisitPrescriptions.dosage] = presc.dosage
                    it[VisitPrescriptions.frequency] = presc.frequency
                    it[VisitPrescriptions.duration] = presc.duration
                    it[VisitPrescriptions.dispensed] = false
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
            ufid
        }

        return getVisit(visitId) ?: throw IllegalStateException("Visit creation failed")
    }

    fun getVisitsForPatient(patientId: String): List<DbVisit> {
        return transaction(database) {
            val patientRow = Patients.selectAll().where { Patients.id eq patientId }
                .singleOrNull() ?: return@transaction emptyList()
            val patientUuid = patientRow[Patients.id]

            Visits.selectAll().where { Visits.patientId eq patientUuid }
                .orderBy(Visits.createdAt to SortOrder.DESC)
                .map { row ->
                    val visitId = row[Visits.id]
                    mapRowToDbVisit(row, visitId, patientId)
                }
        }
    }

    fun getVisit(idOrUserFacingId: String): DbVisit? {
        return transaction(database) {
            val startMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
            val visitRow = Visits.selectAll().where {
                (Visits.id eq idOrUserFacingId) or
                ((Visits.userFacingId eq idOrUserFacingId) and (Visits.createdAt greaterEq startMillis))
            }.singleOrNull() ?: return@transaction null
            
            val visitId = visitRow[Visits.id]
            val patientRow = Patients.selectAll().where { Patients.id eq visitRow[Visits.patientId] }.singleOrNull()
            val patientUuid = patientRow?.get(Patients.id) ?: ""

            mapRowToDbVisit(visitRow, visitId, patientUuid)
        }
    }

    fun getAllVisits(): List<DbVisit> {
        return transaction(database) {
            Visits.selectAll()
                .orderBy(Visits.createdAt to SortOrder.DESC)
                .map { row ->
                    val visitId = row[Visits.id]
                    val patientRow = Patients.selectAll().where { Patients.id eq row[Visits.patientId] }.singleOrNull()
                    val patientUuid = patientRow?.get(Patients.id) ?: ""
                    mapRowToDbVisit(row, visitId, patientUuid)
                }
        }
    }

    private fun mapRowToDbVisit(row: ResultRow, visitId: String, patientId: String): DbVisit {
        val symptoms = VisitSymptoms.selectAll().where { VisitSymptoms.visitId eq visitId }
            .map { it[VisitSymptoms.symptom] }

        val diagnoses = VisitDiagnoses.selectAll().where { VisitDiagnoses.visitId eq visitId }
            .map { it[VisitDiagnoses.diagnosis] }

        val treatments = VisitTreatments.selectAll().where { VisitTreatments.visitId eq visitId }
            .map { it[VisitTreatments.treatment] }

        val prescriptions = VisitPrescriptions.selectAll().where { VisitPrescriptions.visitId eq visitId }
            .map {
                Prescription(
                    medicationId = it[VisitPrescriptions.medicationId],
                    quantity = it[VisitPrescriptions.quantity],
                    dosage = it[VisitPrescriptions.dosage],
                    frequency = it[VisitPrescriptions.frequency],
                    duration = it[VisitPrescriptions.duration],
                    dispensed = it[VisitPrescriptions.dispensed]
                )
            }

        val attachments = VisitAttachments.selectAll().where { VisitAttachments.visitId eq visitId }
            .map {
                VisitAttachment(
                    url = it[VisitAttachments.url],
                    mediaType = it[VisitAttachments.mediaType]
                )
            }

        val pharmacyRequired = VisitPrescriptions.join(Inventory, JoinType.INNER, additionalConstraint = { VisitPrescriptions.medicationId eq Inventory.id })
            .select(VisitPrescriptions.quantity, Inventory.pricePerUnit)
            .where { VisitPrescriptions.visitId eq visitId }
            .sumOf { it[VisitPrescriptions.quantity] * it[Inventory.pricePerUnit] }

        return DbVisit(
            id = row[Visits.userFacingId],
            patientId = patientId,
            type = row[Visits.type],
            requiredPaymentAmount = row[Visits.requiredPaymentAmount],
            amountPhonePe = row[Visits.amountPhonePe],
            amountCash = row[Visits.amountCash],
            paymentStatus = row[Visits.paymentStatus],
            symptoms = symptoms,
            diagnoses = diagnoses,
            treatments = treatments,
            prescriptions = prescriptions,
            attachments = attachments,
            createdBy = row[Visits.createdBy],
            createdAt = row[Visits.createdAt],
            pharmacyRequiredAmount = pharmacyRequired,
            pharmacyAmountPhonePe = row[Visits.pharmacyAmountPhonePe],
            pharmacyAmountCash = row[Visits.pharmacyAmountCash],
            pharmacyPaymentStatus = row[Visits.pharmacyPaymentStatus]
        )
    }

    fun updateInventoryItem(
        medicationName: String,
        quantity: Int,
        pricePerUnit: Double
    ): DbInventoryItem {
        val now = System.currentTimeMillis()
        transaction(database) {
            val existing = Inventory.selectAll().where { Inventory.medicationName eq medicationName }.singleOrNull()
            if (existing != null) {
                Inventory.update({ Inventory.medicationName eq medicationName }) {
                    it[Inventory.quantity] = quantity
                    it[Inventory.pricePerUnit] = pricePerUnit
                    it[Inventory.updatedAt] = now
                }
            } else {
                Inventory.insert {
                    it[Inventory.id] = UUID.randomUUID().toString()
                    it[Inventory.medicationName] = medicationName
                    it[Inventory.quantity] = quantity
                    it[Inventory.pricePerUnit] = pricePerUnit
                    it[Inventory.updatedAt] = now
                }
            }
        }
        return transaction(database) {
            Inventory.selectAll().where { Inventory.medicationName eq medicationName }
                .map {
                    DbInventoryItem(
                        id = it[Inventory.id],
                        medicationName = it[Inventory.medicationName],
                        quantity = it[Inventory.quantity],
                        pricePerUnit = it[Inventory.pricePerUnit],
                        updatedAt = it[Inventory.updatedAt]
                    )
                }
                .single()
        }
    }

    fun getAllInventory(): List<DbInventoryItem> {
        return transaction(database) {
            Inventory.selectAll().map {
                DbInventoryItem(
                    id = it[Inventory.id],
                    medicationName = it[Inventory.medicationName],
                    quantity = it[Inventory.quantity],
                    pricePerUnit = it[Inventory.pricePerUnit],
                    updatedAt = it[Inventory.updatedAt]
                )
            }
        }
    }

    fun payVisit(visitId: String, amountPhonePe: Double, amountCash: Double): DbVisit {
        return transaction(database) {
            val startMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
            val visitRow = Visits.selectAll().where {
                (Visits.id eq visitId) or
                ((Visits.userFacingId eq visitId) and (Visits.createdAt greaterEq startMillis))
            }.singleOrNull() ?: throw IllegalArgumentException("Visit not found")

            val visitUuid = visitRow[Visits.id]
            val newPhonePe = visitRow[Visits.amountPhonePe] + amountPhonePe
            val newCash = visitRow[Visits.amountCash] + amountCash
            val totalPaid = newPhonePe + newCash
            val status = if (totalPaid >= visitRow[Visits.requiredPaymentAmount]) PaymentStatus.PAID else PaymentStatus.PENDING

            Visits.update({ Visits.id eq visitUuid }) {
                it[Visits.amountPhonePe] = newPhonePe
                it[Visits.amountCash] = newCash
                it[Visits.paymentStatus] = status
            }
            mapRowToDbVisit(Visits.selectAll().where { Visits.id eq visitUuid }.single(), visitUuid, visitRow[Visits.patientId])
        }
    }

    fun dispensePharmacy(visitId: String, amountPhonePe: Double, amountCash: Double): DbVisit {
        return transaction(database) {
            val startMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
            val visitRow = Visits.selectAll().where {
                (Visits.id eq visitId) or
                ((Visits.userFacingId eq visitId) and (Visits.createdAt greaterEq startMillis))
            }.singleOrNull() ?: throw IllegalArgumentException("Visit not found")

            val visitUuid = visitRow[Visits.id]

            val unDispensedPrescriptions = VisitPrescriptions.selectAll().where {
                (VisitPrescriptions.visitId eq visitUuid) and (VisitPrescriptions.dispensed eq false)
            }.toList()

            unDispensedPrescriptions.forEach { prescRow ->
                val medId = prescRow[VisitPrescriptions.medicationId]
                val qty = prescRow[VisitPrescriptions.quantity]
                
                val invItem = Inventory.selectAll().where { Inventory.id eq medId }.singleOrNull()
                    ?: throw IllegalArgumentException("Medication ID $medId not found in inventory")
                
                val currentQty = invItem[Inventory.quantity]
                if (currentQty < qty) {
                    throw IllegalArgumentException("Insufficient stock for ${invItem[Inventory.medicationName]}. Available: $currentQty, Requested: $qty")
                }

                Inventory.update({ Inventory.id eq medId }) {
                    it[Inventory.quantity] = currentQty - qty
                    it[Inventory.updatedAt] = System.currentTimeMillis()
                }

                VisitPrescriptions.update({ VisitPrescriptions.id eq prescRow[VisitPrescriptions.id] }) {
                    it[VisitPrescriptions.dispensed] = true
                }
            }

            val newPhonePe = visitRow[Visits.pharmacyAmountPhonePe] + amountPhonePe
            val newCash = visitRow[Visits.pharmacyAmountCash] + amountCash
            val totalPaid = newPhonePe + newCash

            val pharmacyRequired = VisitPrescriptions.join(Inventory, JoinType.INNER, additionalConstraint = { VisitPrescriptions.medicationId eq Inventory.id })
                .select(VisitPrescriptions.quantity, Inventory.pricePerUnit)
                .where { VisitPrescriptions.visitId eq visitUuid }
                .sumOf { it[VisitPrescriptions.quantity] * it[Inventory.pricePerUnit] }

            val status = if (totalPaid >= pharmacyRequired) PaymentStatus.PAID else PaymentStatus.PENDING

            Visits.update({ Visits.id eq visitUuid }) {
                it[Visits.pharmacyAmountPhonePe] = newPhonePe
                it[Visits.pharmacyAmountCash] = newCash
                it[Visits.pharmacyPaymentStatus] = status
            }

            mapRowToDbVisit(Visits.selectAll().where { Visits.id eq visitUuid }.single(), visitUuid, visitRow[Visits.patientId])
        }
    }
}
