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

@Serializable
data class DbInventoryItem(
    val id: String,
    val medicationName: String,
    val quantity: Int,
    val pricePerUnit: Double,
    val updatedAt: Long
)

@Serializable
data class DbPharmacyOrderItem(
    val medicationName: String,
    val quantity: Int,
    val pricePerUnit: Double
)

@Serializable
data class DbPharmacyOrder(
    val id: String,
    val visitId: String,
    val status: String,
    val amountPhonePe: Double,
    val amountCash: Double,
    val paymentStatus: String,
    val transactionId: String,
    val items: List<DbPharmacyOrderItem>,
    val createdAt: Long
)

@Serializable
data class PharmacyOrderItemRequest(
    val medicationName: String,
    val quantity: Int
)

class DatabaseManager(private val database: Database) {

    init {
        transaction(database) {
            SchemaUtils.create(
                Users, Patients, Visits,
                VisitSymptoms, VisitDiagnoses, VisitTreatments,
                VisitPrescriptions, VisitAttachments,
                Inventory, PharmacyOrders, PharmacyOrderItems
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

    fun getVisit(id: String): DbVisit? {
        return transaction(database) {
            Visits.selectAll().where { Visits.id eq id }
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
                .singleOrNull()
        }
    }

    fun updateInventoryItem(medicationName: String, quantity: Int, pricePerUnit: Double): DbInventoryItem {
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

    fun createPharmacyOrder(
        visitId: String,
        items: List<PharmacyOrderItemRequest>,
        amountPhonePe: Double,
        amountCash: Double
    ): DbPharmacyOrder {
        val orderId = UUID.randomUUID().toString()
        val transactionId = "TXN-" + UUID.randomUUID().toString().take(8).uppercase()
        val createdAt = System.currentTimeMillis()

        return transaction(database) {
            val processedItems = items.map { req ->
                val invItem = Inventory.selectAll().where { Inventory.medicationName eq req.medicationName }
                    .singleOrNull() ?: throw IllegalArgumentException("Medication ${req.medicationName} not found in inventory")
                
                val currentQty = invItem[Inventory.quantity]
                if (currentQty < req.quantity) {
                    throw IllegalArgumentException("Insufficient stock for ${req.medicationName}. Available: $currentQty, Requested: ${req.quantity}")
                }

                Inventory.update({ Inventory.medicationName eq req.medicationName }) {
                    it[Inventory.quantity] = currentQty - req.quantity
                    it[Inventory.updatedAt] = createdAt
                }

                DbPharmacyOrderItem(
                    medicationName = req.medicationName,
                    quantity = req.quantity,
                    pricePerUnit = invItem[Inventory.pricePerUnit]
                )
            }

            val requiredPayment = processedItems.sumOf { it.quantity * it.pricePerUnit }
            val totalPaid = amountPhonePe + amountCash
            val paymentStatus = if (totalPaid >= requiredPayment) "PAID" else "PENDING"

            PharmacyOrders.insert {
                it[PharmacyOrders.id] = orderId
                it[PharmacyOrders.visitId] = visitId
                it[PharmacyOrders.status] = "DISPENSED"
                it[PharmacyOrders.amountPhonePe] = amountPhonePe
                it[PharmacyOrders.amountCash] = amountCash
                it[PharmacyOrders.paymentStatus] = paymentStatus
                it[PharmacyOrders.transactionId] = transactionId
                it[PharmacyOrders.createdAt] = createdAt
            }

            processedItems.forEach { item ->
                PharmacyOrderItems.insert {
                    it[PharmacyOrderItems.id] = UUID.randomUUID().toString()
                    it[PharmacyOrderItems.orderId] = orderId
                    it[PharmacyOrderItems.medicationName] = item.medicationName
                    it[PharmacyOrderItems.quantity] = item.quantity
                    it[PharmacyOrderItems.pricePerUnit] = item.pricePerUnit
                }
            }

            DbPharmacyOrder(
                id = orderId,
                visitId = visitId,
                status = "DISPENSED",
                amountPhonePe = amountPhonePe,
                amountCash = amountCash,
                paymentStatus = paymentStatus,
                transactionId = transactionId,
                items = processedItems,
                createdAt = createdAt
            )
        }
    }

    fun getPharmacyOrdersForVisit(visitId: String): List<DbPharmacyOrder> {
        return transaction(database) {
            PharmacyOrders.selectAll().where { PharmacyOrders.visitId eq visitId }
                .orderBy(PharmacyOrders.createdAt to SortOrder.DESC)
                .map { row ->
                    val orderId = row[PharmacyOrders.id]
                    val items = PharmacyOrderItems.selectAll().where { PharmacyOrderItems.orderId eq orderId }
                        .map {
                            DbPharmacyOrderItem(
                                medicationName = it[PharmacyOrderItems.medicationName],
                                quantity = it[PharmacyOrderItems.quantity],
                                pricePerUnit = it[PharmacyOrderItems.pricePerUnit]
                            )
                        }

                    DbPharmacyOrder(
                        id = orderId,
                        visitId = row[PharmacyOrders.visitId],
                        status = row[PharmacyOrders.status],
                        amountPhonePe = row[PharmacyOrders.amountPhonePe],
                        amountCash = row[PharmacyOrders.amountCash],
                        paymentStatus = row[PharmacyOrders.paymentStatus],
                        transactionId = row[PharmacyOrders.transactionId],
                        items = items,
                        createdAt = row[PharmacyOrders.createdAt]
                    )
                }
        }
    }
}
