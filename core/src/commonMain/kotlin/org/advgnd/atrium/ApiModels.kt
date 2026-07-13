package org.advgnd.atrium

import kotlinx.serialization.Serializable

@Serializable
enum class PaymentStatus {
    PAID, PENDING
}

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
data class Prescription(
    val medicationId: String,
    val quantity: Int,
    val dosage: String,
    val frequency: String,
    val duration: String,
    val dispensed: Boolean = false
)

@Serializable
data class VisitAttachment(
    val url: String,
    val mediaType: String
)

@Serializable
data class VisitRequest(
    val type: String,
    val requiredPaymentAmount: Double,
    val symptoms: List<String> = emptyList(),
    val diagnoses: List<String> = emptyList(),
    val treatments: List<String> = emptyList(),
    val prescriptions: List<Prescription> = emptyList(),
    val attachments: List<VisitAttachment> = emptyList()
)


@Serializable
data class InventoryUpdateRequest(
    val medicationName: String,
    val quantity: Int,
    val pricePerUnit: Double
)

@Serializable
data class PaymentRequest(
    val amountPhonePe: Double,
    val amountCash: Double
)
