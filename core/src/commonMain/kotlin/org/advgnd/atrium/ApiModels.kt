package org.advgnd.atrium

import kotlinx.serialization.Serializable
import dev.nesk.akkurate.annotations.Validate

@Serializable
enum class PaymentStatus {
    PAID, PENDING
}

@Validate
@Serializable
data class AuthRequest(val email: String, val password: String)

@Serializable
data class ValidationError(val path: String, val message: String)

@Serializable
data class ValidationErrorsResponse(val errors: List<ValidationError>)

@Serializable
data class MessageResponse(val message: String)


@Serializable
data class ProfileResponse(val userId: String, val email: String, val roles: List<String>)

@Validate
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

@Validate
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

@Validate
@Serializable
data class InventoryUpdateRequest(
    val medicationName: String,
    val quantity: Int,
    val pricePerUnit: Double
)

@Validate
@Serializable
data class PaymentRequest(
    val amountPhonePe: Double,
    val amountCash: Double
)
