@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package org.advgnd.atrium

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.ktor.client.call.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.advgnd.atrium.Prescription
import org.advgnd.atrium.VisitAttachment
import org.advgnd.atrium.PaymentStatus
import org.advgnd.atrium.ValidationErrorsResponse
import org.advgnd.atrium.ValidationError
import org.advgnd.atrium.VisitTypeDto
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.io.File
import java.awt.FileDialog
import java.awt.Frame
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

enum class AppScreen {
    LOGIN, REGISTER, PATIENTS, VISITS, INVENTORY
}

enum class RightVisitsMode {
    LIST, DETAIL
}

@Serializable
data class ClientPatient(
    val id: String,
    val name: String,
    val dateOfBirth: String,
    val gender: String,
    val contactNumber: String,
    val village: String,
    val address: String?,
    val createdAt: Long
)

@Serializable
data class ClientVisit(
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
    val pharmacyPaymentStatus: PaymentStatus,
    val notes: String
)

@Serializable
data class ClientInventoryItem(
    val id: String,
    val medicationName: String,
    val quantity: Int,
    val pricePerUnit: Double,
    val updatedAt: Long
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App() {
    AtriumTheme(darkTheme = true) {
        var currentScreen by remember { mutableStateOf(AppScreen.LOGIN) }
        var isLoggedIn by remember { mutableStateOf(false) }
        var infoMessage by remember { mutableStateOf("") }
        var validationErrors by remember { mutableStateOf(emptyMap<String, String>()) }
        val scope = rememberCoroutineScope()

        var loginEmail by remember { mutableStateOf("") }
        var loginPassword by remember { mutableStateOf("") }
        var loginPasswordVisible by remember { mutableStateOf(false) }

        var regEmail by remember { mutableStateOf("") }
        var regPassword by remember { mutableStateOf("") }
        var regPasswordVisible by remember { mutableStateOf(false) }

        var patName by remember { mutableStateOf("") }
        var patDob by remember { mutableStateOf("") }
        var patGender by remember { mutableStateOf("") }
        var patContact by remember { mutableStateOf("") }
        var patVillage by remember { mutableStateOf("") }
        var patAddress by remember { mutableStateOf("") }
        var patientsList by remember { mutableStateOf(emptyList<ClientPatient>()) }
        var selectedPatientForVisits by remember { mutableStateOf<ClientPatient?>(null) }
        var editingPatientId by remember { mutableStateOf<String?>(null) }

        var visitType by remember { mutableStateOf("") }
        var visitCost by remember { mutableStateOf("") }
        var visitNotes by remember { mutableStateOf("") }
        var editingVisitId by remember { mutableStateOf<String?>(null) }
        var rightVisitsMode by remember { mutableStateOf(RightVisitsMode.LIST) }
        var selectedVisitDetail by remember { mutableStateOf<ClientVisit?>(null) }

        val visitSymptoms = remember { mutableStateListOf<String>() }
        val visitDiagnoses = remember { mutableStateListOf<String>() }
        val visitTreatments = remember { mutableStateListOf<String>() }

        var symptomInput by remember { mutableStateOf("") }
        var diagnosisInput by remember { mutableStateOf("") }
        var treatmentInput by remember { mutableStateOf("") }

        var selectedMedicationId by remember { mutableStateOf("") }
        var prescQtyInput by remember { mutableStateOf("") }
        var dosageInput by remember { mutableStateOf("") }
        var frequencyInput by remember { mutableStateOf("") }
        var durationInput by remember { mutableStateOf("") }
        val prescriptionsList = remember { mutableStateListOf<Prescription>() }

        var attachmentUrl by remember { mutableStateOf("") }
        var attachmentType by remember { mutableStateOf("IMAGE") }
        val attachmentsList = remember { mutableStateListOf<VisitAttachment>() }

        var patientVisitsList by remember { mutableStateOf(emptyList<ClientVisit>()) }
        var searchedVisit by remember { mutableStateOf<ClientVisit?>(null) }

        var invMedicationName by remember { mutableStateOf("") }
        var invQuantity by remember { mutableStateOf("") }
        var invPrice by remember { mutableStateOf("") }
        var inventoryItemsList by remember { mutableStateOf(emptyList<ClientInventoryItem>()) }
        var visitTypesList by remember { mutableStateOf(emptyList<VisitTypeDto>()) }

        fun fetchVisitTypes() {
            scope.launch {
                try {
                    val response = ApiClient.client.get(ApiV1.Visits.VisitTypes()) {
                        url { host = "localhost"; port = 8080 }
                    }
                    if (response.status == HttpStatusCode.OK) {
                        visitTypesList = response.body()
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        fun fetchPatients(query: String = "") {
            scope.launch {
                try {
                    val response = ApiClient.client.get(ApiV1.Patients(q = query.ifEmpty { null })) {
                        url { host = "localhost"; port = 8080 }
                    }
                    if (response.status == HttpStatusCode.OK) {
                        patientsList = response.body()
                    } else {
                        infoMessage = "Failed to load patients"
                    }
                } catch (e: Exception) {
                    infoMessage = e.message ?: "Connection error"
                }
            }
        }

        fun fetchInventory() {
            scope.launch {
                try {
                    val response = ApiClient.client.get(ApiV1.Inventory()) {
                        url { host = "localhost"; port = 8080 }
                    }
                    if (response.status == HttpStatusCode.OK) {
                        inventoryItemsList = response.body()
                    } else {
                        infoMessage = "Failed to load inventory"
                    }
                } catch (e: Exception) {
                    infoMessage = e.message ?: "Connection error"
                }
            }
        }

        fun fetchVisitsForPatient(patientId: String) {
            scope.launch {
                try {
                    val response = ApiClient.client.get(ApiV1.PatientVisits(id = patientId)) {
                        url { host = "localhost"; port = 8080 }
                    }
                    if (response.status == HttpStatusCode.OK) {
                        patientVisitsList = response.body()
                    } else {
                        infoMessage = "Failed to load visits"
                    }
                } catch (e: Exception) {
                    infoMessage = e.message ?: "Connection error"
                }
            }
        }

        fun handleApiError(response: io.ktor.client.statement.HttpResponse) {
            scope.launch {
                try {
                    if (response.status == HttpStatusCode.BadRequest) {
                        val bodyText = response.call.body<ValidationErrorsResponse>()
                        validationErrors = bodyText.errors.associate { it.path to it.message }
                        infoMessage = "Please resolve the field errors highlighted below."
                    } else {
                        val bodyText = response.call.body<MessageResponse>()
                        infoMessage = bodyText.message
                    }
                } catch (e: Exception) {
                    infoMessage = "Error processing response: ${response.status}"
                }
            }
        }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (isLoggedIn) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight().border(width = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Icon(imageVector = Icons.Default.MedicalServices, contentDescription = "HIS Clinic", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.weight(1f))

                        NavigationRailItem(
                            selected = currentScreen == AppScreen.PATIENTS,
                            onClick = { currentScreen = AppScreen.PATIENTS; validationErrors = emptyMap(); infoMessage = ""; fetchPatients() },
                            icon = { Icon(Icons.Default.People, contentDescription = "Patients") },
                            label = { Text("Patients") }
                        )
                        NavigationRailItem(
                            selected = currentScreen == AppScreen.VISITS,
                            onClick = { currentScreen = AppScreen.VISITS; validationErrors = emptyMap(); infoMessage = ""; fetchVisitTypes() },
                            icon = { Icon(Icons.Default.Assignment, contentDescription = "Visits") },
                            label = { Text("Visits") }
                        )
                        NavigationRailItem(
                            selected = currentScreen == AppScreen.INVENTORY,
                            onClick = { currentScreen = AppScreen.INVENTORY; validationErrors = emptyMap(); infoMessage = ""; fetchInventory() },
                            icon = { Icon(Icons.Default.Inventory, contentDescription = "Inventory") },
                            label = { Text("Inventory") }
                        )

                        Spacer(modifier = Modifier.weight(1f))
                        NavigationRailItem(
                            selected = false,
                            onClick = {
                                scope.launch {
                                    try {
                                        val response = ApiClient.client.post(ApiV1.Logout()) {
                                            url { host = "localhost"; port = 8080 }
                                        }
                                        if (response.status == HttpStatusCode.OK) {
                                            isLoggedIn = false
                                            currentScreen = AppScreen.LOGIN
                                            infoMessage = "Logged out successfully"
                                            validationErrors = emptyMap()
                                        }
                                    } catch (e: Exception) {
                                        infoMessage = e.message ?: "Connection error"
                                    }
                                }
                            },
                            icon = { Icon(Icons.Default.ExitToApp, contentDescription = "Logout") },
                            label = { Text("Logout") }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    if (infoMessage.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            Text(text = infoMessage, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
                        }
                    }

                    Crossfade(targetState = currentScreen, animationSpec = tween(durationMillis = 300)) { screen ->
                        when (screen) {
                            AppScreen.LOGIN -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    ElevatedCard(
                                        modifier = Modifier.width(420.dp).padding(16.dp),
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(32.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Login", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                                            Text("Welcome Back", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                            Text("Please log in to your clinic account", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = loginEmail,
                                                onValueChange = { loginEmail = it },
                                                label = { Text("Email Address") },
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                isError = validationErrors.containsKey("email")
                                            )
                                            validationErrors["email"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                                            OutlinedTextField(
                                                value = loginPassword,
                                                onValueChange = { loginPassword = it },
                                                label = { Text("Password") },
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                visualTransformation = if (loginPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = if (loginPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = "Toggle Visibility",
                                                        modifier = Modifier.clickable { loginPasswordVisible = !loginPasswordVisible }
                                                    )
                                                },
                                                isError = validationErrors.containsKey("password")
                                            )
                                            validationErrors["password"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            validationErrors = emptyMap()
                                                            infoMessage = ""
                                                            val response = ApiClient.client.post(ApiV1.Login()) {
                                                                url { host = "localhost"; port = 8080 }
                                                                contentType(ContentType.Application.Json)
                                                                setBody(AuthRequest(loginEmail, loginPassword))
                                                            }
                                                            if (response.status == HttpStatusCode.OK) {
                                                                isLoggedIn = true
                                                                currentScreen = AppScreen.PATIENTS
                                                                infoMessage = ""
                                                                fetchPatients()
                                                                fetchInventory()
                                                            } else {
                                                                handleApiError(response)
                                                            }
                                                        } catch (e: Exception) {
                                                            infoMessage = e.message ?: "Connection error"
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(24.dp),
                                                modifier = Modifier.fillMaxWidth().height(50.dp)
                                            ) {
                                                Text("Sign In", fontWeight = FontWeight.SemiBold)
                                            }

                                            TextButton(onClick = { currentScreen = AppScreen.REGISTER; validationErrors = emptyMap(); infoMessage = "" }) {
                                                Text("Don't have an account? Sign Up")
                                            }
                                        }
                                    }
                                }
                            }
                            AppScreen.REGISTER -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    ElevatedCard(
                                        modifier = Modifier.width(420.dp).padding(16.dp),
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(32.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(imageVector = Icons.Default.AppRegistration, contentDescription = "Register", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                                            Text("Create Account", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                            Text("Get started by registering a new account", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = regEmail,
                                                onValueChange = { regEmail = it },
                                                label = { Text("Email Address") },
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                isError = validationErrors.containsKey("email")
                                            )
                                            validationErrors["email"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                                            OutlinedTextField(
                                                value = regPassword,
                                                onValueChange = { regPassword = it },
                                                label = { Text("Password") },
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                visualTransformation = if (regPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = if (regPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                        contentDescription = "Toggle Visibility",
                                                        modifier = Modifier.clickable { regPasswordVisible = !regPasswordVisible }
                                                    )
                                                },
                                                isError = validationErrors.containsKey("password")
                                            )
                                            validationErrors["password"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                                            Spacer(modifier = Modifier.height(16.dp))
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            validationErrors = emptyMap()
                                                            infoMessage = ""
                                                            val response = ApiClient.client.post(ApiV1.Register()) {
                                                                url { host = "localhost"; port = 8080 }
                                                                contentType(ContentType.Application.Json)
                                                                setBody(AuthRequest(regEmail, regPassword))
                                                            }
                                                            if (response.status == HttpStatusCode.Created) {
                                                                currentScreen = AppScreen.LOGIN
                                                                infoMessage = "Registration successful"
                                                            } else {
                                                                handleApiError(response)
                                                            }
                                                        } catch (e: Exception) {
                                                            infoMessage = e.message ?: "Connection error"
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(24.dp),
                                                modifier = Modifier.fillMaxWidth().height(50.dp)
                                            ) {
                                                Text("Sign Up", fontWeight = FontWeight.SemiBold)
                                            }

                                            TextButton(onClick = { currentScreen = AppScreen.LOGIN; validationErrors = emptyMap(); infoMessage = "" }) {
                                                Text("Already have an account? Sign In")
                                            }
                                        }
                                    }
                                }
                            }
                            AppScreen.PATIENTS -> {
                                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    ElevatedCard(
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                                            Text(if (editingPatientId != null) "Edit Patient Profile" else "Add Patient Profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(16.dp))

                                            OutlinedTextField(value = patName, onValueChange = { patName = it }, label = { Text("Full Name") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), singleLine = true, isError = validationErrors.containsKey("name"))
                                            validationErrors["name"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                            Spacer(modifier = Modifier.height(8.dp))

                                            Text("Gender", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                                listOf("Male", "Female", "Other").forEach { genderOption ->
                                                    FilterChip(
                                                        selected = patGender == genderOption,
                                                        onClick = { patGender = genderOption },
                                                        label = { Text(genderOption) },
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                }
                                            }
                                            validationErrors["gender"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                            Spacer(modifier = Modifier.height(8.dp))

                                            OutlinedTextField(value = patContact, onValueChange = { patContact = it }, label = { Text("Contact Number") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), singleLine = true, isError = validationErrors.containsKey("contactNumber"))
                                            validationErrors["contactNumber"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                            Spacer(modifier = Modifier.height(8.dp))

                                            OutlinedTextField(
                                                value = patVillage,
                                                onValueChange = { patVillage = it },
                                                label = { Text("Village") },
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                isError = validationErrors.containsKey("village")
                                            )
                                            validationErrors["village"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                            Spacer(modifier = Modifier.height(8.dp))

                                            OutlinedTextField(value = patAddress, onValueChange = { patAddress = it }, label = { Text("Address (Optional)") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), singleLine = true, isError = validationErrors.containsKey("address"))
                                            validationErrors["address"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                            Spacer(modifier = Modifier.height(8.dp))

                                            OutlinedTextField(
                                                value = patDob,
                                                onValueChange = { patDob = it },
                                                label = { Text("Year of Birth (Optional)") },
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                isError = validationErrors.containsKey("dateOfBirth"),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                            validationErrors["dateOfBirth"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                            Spacer(modifier = Modifier.height(24.dp))

                                            if (editingPatientId == null) {
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                validationErrors = emptyMap()
                                                                infoMessage = ""
                                                                val response = ApiClient.client.post(ApiV1.Patients()) {
                                                                    url { host = "localhost"; port = 8080 }
                                                                    contentType(ContentType.Application.Json)
                                                                    setBody(PatientRequest(patName, patDob, patGender, patContact, patVillage, patAddress))
                                                                }
                                                                if (response.status == HttpStatusCode.Created) {
                                                                    infoMessage = ""
                                                                    fetchPatients()
                                                                    patName = ""; patDob = ""; patGender = ""; patContact = ""; patVillage = ""; patAddress = ""
                                                                } else {
                                                                    handleApiError(response)
                                                                }
                                                            } catch (e: Exception) {
                                                                infoMessage = e.message ?: "Connection error"
                                                            }
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(24.dp),
                                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.PersonAdd, contentDescription = "Add Patient")
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Register Patient")
                                                }
                                            } else {
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Button(
                                                        onClick = {
                                                            scope.launch {
                                                                 try {
                                                                     validationErrors = emptyMap()
                                                                     infoMessage = ""
                                                                     val response = ApiClient.client.put(ApiV1.PatientDetail(id = editingPatientId!!)) {
                                                                         url { host = "localhost"; port = 8080 }
                                                                         contentType(ContentType.Application.Json)
                                                                         setBody(PatientRequest(patName, patDob, patGender, patContact, patVillage, patAddress))
                                                                     }
                                                                     if (response.status == HttpStatusCode.OK) {
                                                                         infoMessage = "Patient details updated successfully"
                                                                         fetchPatients()
                                                                         patName = ""; patDob = ""; patGender = ""; patContact = ""; patVillage = ""; patAddress = ""
                                                                         editingPatientId = null
                                                                     } else {
                                                                         handleApiError(response)
                                                                     }
                                                                 } catch (e: Exception) {
                                                                     infoMessage = e.message ?: "Connection error"
                                                                     scope.launch { fetchPatients() }
                                                                 }
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(24.dp),
                                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                                    ) {
                                                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Update Patient")
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Update Patient")
                                                    }

                                                    OutlinedButton(
                                                        onClick = {
                                                            patName = ""; patDob = ""; patGender = ""; patContact = ""; patVillage = ""; patAddress = ""
                                                            editingPatientId = null
                                                            validationErrors = emptyMap()
                                                            infoMessage = ""
                                                        },
                                                        shape = RoundedCornerShape(24.dp),
                                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                                    ) {
                                                        Text("Cancel / New Patient")
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    ElevatedCard(
                                        modifier = Modifier.weight(1.2f).fillMaxHeight(),
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(24.dp)) {
                                            Text("Registered Patients", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(16.dp))

                                            var patientSearchQuery by remember { mutableStateOf("") }
                                            OutlinedTextField(
                                                value = patientSearchQuery,
                                                onValueChange = {
                                                    patientSearchQuery = it
                                                    fetchPatients(it)
                                                },
                                                placeholder = { Text("Search by name, phone, email, address...") },
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Patients") },
                                                trailingIcon = {
                                                    if (patientSearchQuery.isNotEmpty()) {
                                                        IconButton(onClick = { patientSearchQuery = ""; fetchPatients("") }) {
                                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                                            )

                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                items(patientsList) { patient ->
                                                    OutlinedCard(
                                                        modifier = Modifier.fillMaxWidth().clickable {
                                                            selectedPatientForVisits = patient
                                                            fetchVisitsForPatient(patient.id)
                                                            fetchInventory()
                                                            fetchVisitTypes()
                                                            validationErrors = emptyMap()
                                                            infoMessage = ""
                                                            currentScreen = AppScreen.VISITS
                                                        },
                                                        shape = RoundedCornerShape(12.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(patient.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                                Text("${patient.gender} | DOB: ${patient.dateOfBirth}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                Text("Contact: ${patient.contactNumber} | Village: ${patient.village}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    patName = patient.name
                                                                    patDob = patient.dateOfBirth
                                                                    patGender = patient.gender
                                                                    patContact = patient.contactNumber
                                                                    patVillage = patient.village
                                                                    patAddress = patient.address ?: ""
                                                                    editingPatientId = patient.id
                                                                    validationErrors = emptyMap()
                                                                    infoMessage = ""
                                                                }
                                                            ) {
                                                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Profile Details", tint = MaterialTheme.colorScheme.primary)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            AppScreen.VISITS -> {
                                 val scrollState = rememberScrollState()
                                 Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                     ElevatedCard(
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                         Column(modifier = Modifier.padding(24.dp).verticalScroll(scrollState)) {
                                            Text(if (editingVisitId != null) "Edit Clinic Visit" else "New Clinic Visit", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Patient: ${selectedPatientForVisits?.name ?: "None"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                            selectedPatientForVisits?.let { pat ->
                                                var visitTypeDropdownExpanded by remember { mutableStateOf(false) }

                                                Text("Visit Type", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    OutlinedCard(
                                                        modifier = Modifier.fillMaxWidth().clickable { visitTypeDropdownExpanded = !visitTypeDropdownExpanded },
                                                        shape = RoundedCornerShape(12.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(if (visitType.isEmpty()) "Select Visit Type..." else visitType)
                                                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Expand Visit Types")
                                                        }
                                                    }
                                                    DropdownMenu(
                                                        expanded = visitTypeDropdownExpanded,
                                                        onDismissRequest = { visitTypeDropdownExpanded = false }
                                                    ) {
                                                        visitTypesList.forEach { typeDto ->
                                                            DropdownMenuItem(
                                                                text = { Text(typeDto.name) },
                                                                onClick = {
                                                                    visitType = typeDto.name
                                                                    visitCost = typeDto.defaultCost.toInt().toString()
                                                                    visitTypeDropdownExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                                validationErrors["type"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                                Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(value = visitCost, onValueChange = { visitCost = it }, label = { Text("Required Cost (Rs.)") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), singleLine = true, isError = validationErrors.containsKey("requiredPaymentAmount"))
                                            validationErrors["requiredPaymentAmount"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text("Symptoms List", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = symptomInput,
                                                    onValueChange = { symptomInput = it },
                                                    placeholder = { Text("Add Symptom") },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f),
                                                    singleLine = true
                                                )
                                                Button(
                                                    onClick = {
                                                        if (symptomInput.trim().isNotEmpty()) {
                                                            visitSymptoms.add(symptomInput.trim())
                                                            symptomInput = ""
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.height(56.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Add")
                                                }
                                            }
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                visitSymptoms.forEach { sym ->
                                                    ListItem(
                                                        headlineContent = { Text(sym) },
                                                        trailingContent = {
                                                            IconButton(onClick = { visitSymptoms.remove(sym) }) {
                                                                Icon(Icons.Default.Close, contentDescription = "Delete")
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text("Diagnoses List", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = diagnosisInput,
                                                    onValueChange = { diagnosisInput = it },
                                                    placeholder = { Text("Add Diagnosis") },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f),
                                                    singleLine = true
                                                )
                                                Button(
                                                    onClick = {
                                                        if (diagnosisInput.trim().isNotEmpty()) {
                                                            visitDiagnoses.add(diagnosisInput.trim())
                                                            diagnosisInput = ""
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.height(56.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Add")
                                                }
                                            }
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                visitDiagnoses.forEach { diag ->
                                                    ListItem(
                                                        headlineContent = { Text(diag) },
                                                        trailingContent = {
                                                            IconButton(onClick = { visitDiagnoses.remove(diag) }) {
                                                                Icon(Icons.Default.Close, contentDescription = "Delete")
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(12.dp))

                                            Text("Treatments List", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = treatmentInput,
                                                    onValueChange = { treatmentInput = it },
                                                    placeholder = { Text("Add Treatment") },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f),
                                                    singleLine = true
                                                )
                                                Button(
                                                    onClick = {
                                                        if (treatmentInput.trim().isNotEmpty()) {
                                                            visitTreatments.add(treatmentInput.trim())
                                                            treatmentInput = ""
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.height(56.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Add")
                                                }
                                            }
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                visitTreatments.forEach { treat ->
                                                    ListItem(
                                                        headlineContent = { Text(treat) },
                                                        trailingContent = {
                                                            IconButton(onClick = { visitTreatments.remove(treat) }) {
                                                                Icon(Icons.Default.Close, contentDescription = "Delete")
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text("Link Prescriptions (Pharmacy Items)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Spacer(modifier = Modifier.height(8.dp))

                                                var drugSelectorExpanded by remember { mutableStateOf(false) }
                                                var drugLabel by remember { mutableStateOf("Select Drug...") }
                                                Box(modifier = Modifier.fillMaxWidth()) {
                                                    OutlinedCard(
                                                        modifier = Modifier.fillMaxWidth().clickable { drugSelectorExpanded = !drugSelectorExpanded },
                                                        shape = RoundedCornerShape(12.dp)
                                                    ) {
                                                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                            Text(drugLabel)
                                                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search Drug")
                                                        }
                                                    }
                                                    DropdownMenu(expanded = drugSelectorExpanded, onDismissRequest = { drugSelectorExpanded = false }) {
                                                        inventoryItemsList.forEach { item ->
                                                            DropdownMenuItem(
                                                                text = { Text("${item.medicationName} (Qty: ${item.quantity})") },
                                                                onClick = {
                                                                    selectedMedicationId = item.id
                                                                    drugLabel = item.medicationName
                                                                    drugSelectorExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedTextField(value = prescQtyInput, onValueChange = { prescQtyInput = it }, label = { Text("Qty") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                                    OutlinedTextField(value = dosageInput, onValueChange = { dosageInput = it }, label = { Text("Dosage") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(2f), singleLine = true)
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    OutlinedTextField(value = frequencyInput, onValueChange = { frequencyInput = it }, label = { Text("Freq") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f), singleLine = true)
                                                    OutlinedTextField(value = durationInput, onValueChange = { durationInput = it }, label = { Text("Duration") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f), singleLine = true)
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Button(
                                                    onClick = {
                                                        val qty = prescQtyInput.toIntOrNull() ?: 0
                                                        if (selectedMedicationId.isNotEmpty() && qty > 0) {
                                                            prescriptionsList.add(Prescription(selectedMedicationId, qty, dosageInput, frequencyInput, durationInput))
                                                            selectedMedicationId = ""
                                                            prescQtyInput = ""
                                                            dosageInput = ""
                                                            frequencyInput = ""
                                                            durationInput = ""
                                                            drugLabel = "Select Drug..."
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Add to Prescription List")
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    prescriptionsList.forEach { p ->
                                                        val name = inventoryItemsList.find { it.id == p.medicationId }?.medicationName ?: p.medicationId
                                                        ListItem(
                                                            headlineContent = { Text("$name x ${p.quantity}") },
                                                            supportingContent = { Text("${p.dosage} | ${p.frequency} | ${p.duration}") },
                                                            trailingContent = {
                                                                IconButton(onClick = { prescriptionsList.remove(p) }) {
                                                                    Icon(Icons.Default.Close, contentDescription = "Remove Item")
                                                                }
                                                            }
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(16.dp))
                                                Text("Visit Notes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                OutlinedTextField(
                                                    value = visitNotes,
                                                    onValueChange = { visitNotes = it },
                                                    placeholder = { Text("Enter doctor notes...") },
                                                    shape = RoundedCornerShape(16.dp),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    minLines = 3,
                                                    maxLines = 6
                                                )
                                                Spacer(modifier = Modifier.height(16.dp))

                                                Text("Visit Attachments", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                var isUploading by remember { mutableStateOf(false) }
                                                Button(
                                                    onClick = {
                                                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                            try {
                                                                val fileDialog = FileDialog(null as Frame?, "Select Attachment File", FileDialog.LOAD)
                                                                fileDialog.isVisible = true
                                                                val directory = fileDialog.directory
                                                                val filename = fileDialog.file
                                                                if (directory != null && filename != null) {
                                                                    isUploading = true
                                                                    val file = File(directory, filename)
                                                                    val fileBytes = file.readBytes()

                                                                    val response = ApiClient.client.submitFormWithBinaryData(
                                                                        url = "http://localhost:8080/api/v1/upload",
                                                                        formData = formData {
                                                                            append("file", fileBytes, Headers.build {
                                                                                val contentType = when (file.extension.lowercase()) {
                                                                                    "png" -> "image/png"
                                                                                    "jpg", "jpeg" -> "image/jpeg"
                                                                                    "gif" -> "image/gif"
                                                                                    "mp4" -> "video/mp4"
                                                                                    "webm" -> "video/webm"
                                                                                    "pdf" -> "application/pdf"
                                                                                    else -> "application/octet-stream"
                                                                                }
                                                                                append(HttpHeaders.ContentType, contentType)
                                                                                append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                                                                            })
                                                                        }
                                                                    )

                                                                    if (response.status == HttpStatusCode.OK) {
                                                                        val responseData: Map<String, String> = response.body()
                                                                        val url = responseData["url"] ?: ""
                                                                        val mediaType = when (file.extension.lowercase()) {
                                                                            "mp4", "webm" -> "VIDEO"
                                                                            else -> "IMAGE"
                                                                        }
                                                                        if (url.isNotEmpty()) {
                                                                            attachmentsList.add(VisitAttachment(url, mediaType))
                                                                        }
                                                                    } else {
                                                                        infoMessage = "Upload failed: ${response.status}"
                                                                    }
                                                                }
                                                            } catch (e: Exception) {
                                                                infoMessage = e.message ?: "Upload connection error"
                                                            } finally {
                                                                isUploading = false
                                                            }
                                                        }
                                                    },
                                                    enabled = !isUploading,
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    if (isUploading) {
                                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Uploading...")
                                                    } else {
                                                        Icon(Icons.Default.UploadFile, contentDescription = "Upload File")
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Upload Image/Video Attachment")
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    attachmentsList.forEach { a ->
                                                        ListItem(
                                                            headlineContent = { Text(a.url.substringAfterLast("/")) },
                                                            supportingContent = { Text(a.mediaType) },
                                                            trailingContent = {
                                                                IconButton(onClick = { attachmentsList.remove(a) }) {
                                                                    Icon(Icons.Default.Close, contentDescription = "Remove Attachment")
                                                                }
                                                            }
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(24.dp))
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            try {
                                                                validationErrors = emptyMap()
                                                                infoMessage = ""
                                                                val cost = visitCost.toDoubleOrNull() ?: 0.0

                                                                val requestBody = VisitRequest(
                                                                    type = visitType,
                                                                    requiredPaymentAmount = cost,
                                                                    symptoms = visitSymptoms.toList(),
                                                                    diagnoses = visitDiagnoses.toList(),
                                                                    treatments = visitTreatments.toList(),
                                                                    prescriptions = prescriptionsList.toList(),
                                                                    attachments = attachmentsList.toList(),
                                                                    notes = visitNotes
                                                                )

                                                                val response = if (editingVisitId == null) {
                                                                    ApiClient.client.post(ApiV1.PatientVisits(id = pat.id)) {
                                                                        url { host = "localhost"; port = 8080 }
                                                                        contentType(ContentType.Application.Json)
                                                                        setBody(requestBody)
                                                                    }
                                                                } else {
                                                                    ApiClient.client.put(ApiV1.VisitDetail(id = editingVisitId!!)) {
                                                                        url { host = "localhost"; port = 8080 }
                                                                        contentType(ContentType.Application.Json)
                                                                        setBody(requestBody)
                                                                    }
                                                                }

                                                                if (response.status == HttpStatusCode.Created || response.status == HttpStatusCode.OK) {
                                                                    fetchVisitsForPatient(pat.id)
                                                                    prescriptionsList.clear()
                                                                    attachmentsList.clear()
                                                                    visitSymptoms.clear()
                                                                    visitDiagnoses.clear()
                                                                    visitTreatments.clear()
                                                                    visitType = ""
                                                                    visitCost = ""
                                                                    visitNotes = ""
                                                                    editingVisitId = null
                                                                } else {
                                                                    handleApiError(response)
                                                                }
                                                            } catch (e: Exception) {
                                                                infoMessage = e.message ?: "Connection error"
                                                            }
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(24.dp),
                                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                                ) {
                                                    Text(if (editingVisitId != null) "Update Visit Details" else "Record Visit Details", fontWeight = FontWeight.Bold)
                                                }

                                                if (editingVisitId != null) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    OutlinedButton(
                                                        onClick = {
                                                            prescriptionsList.clear()
                                                            attachmentsList.clear()
                                                            visitSymptoms.clear()
                                                            visitDiagnoses.clear()
                                                            visitTreatments.clear()
                                                            visitType = ""
                                                            visitCost = ""
                                                            visitNotes = ""
                                                            editingVisitId = null
                                                            validationErrors = emptyMap()
                                                            infoMessage = ""
                                                        },
                                                        shape = RoundedCornerShape(24.dp),
                                                        modifier = Modifier.fillMaxWidth().height(48.dp)
                                                    ) {
                                                        Text("Cancel Edit", fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            } ?: Text("Please select a patient from the Patients list to start recording visits.")
                                        }
                                    }

                                    ElevatedCard(
                                        modifier = Modifier.weight(1.2f).fillMaxHeight(),
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                        if (rightVisitsMode == RightVisitsMode.DETAIL && selectedVisitDetail != null) {
                                            val detailVisit = selectedVisitDetail!!
                                            val detailScroll = rememberScrollState()
                                            Column(modifier = Modifier.padding(24.dp).verticalScroll(detailScroll)) {
                                                 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                     Text("Visit Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                                     IconButton(onClick = { rightVisitsMode = RightVisitsMode.LIST; selectedVisitDetail = null }) {
                                                         Icon(Icons.Default.Close, contentDescription = "Close details")
                                                     }
                                                 }
                                                 Spacer(modifier = Modifier.height(8.dp))
                                                 Text("Visit ID: ${detailVisit.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                 Text("Type: ${detailVisit.type}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                 Text("Date: ${java.time.Instant.ofEpochMilli(detailVisit.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}", style = MaterialTheme.typography.bodySmall)
                                                 Spacer(modifier = Modifier.height(16.dp))

                                                 if (detailVisit.symptoms.isNotEmpty()) {
                                                     Text("Symptoms:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                                     Column(modifier = Modifier.fillMaxWidth()) {
                                                         detailVisit.symptoms.forEach { sym ->
                                                             ListItem(headlineContent = { Text(sym) })
                                                         }
                                                     }
                                                 }
                                                 if (detailVisit.diagnoses.isNotEmpty()) {
                                                     Text("Diagnoses:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                                     Column(modifier = Modifier.fillMaxWidth()) {
                                                         detailVisit.diagnoses.forEach { diag ->
                                                             ListItem(headlineContent = { Text(diag) })
                                                         }
                                                     }
                                                 }
                                                 if (detailVisit.treatments.isNotEmpty()) {
                                                     Text("Treatments:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                                     Column(modifier = Modifier.fillMaxWidth()) {
                                                         detailVisit.treatments.forEach { treat ->
                                                             ListItem(headlineContent = { Text(treat) })
                                                         }
                                                     }
                                                 }
                                                 if (detailVisit.notes.isNotEmpty()) {
                                                     Text("Notes:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                                     Text(detailVisit.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp))
                                                 }

                                                 if (detailVisit.attachments.isNotEmpty()) {
                                                     Text("Attachments:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                                     Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                         detailVisit.attachments.forEach { a ->
                                                             ListItem(
                                                                 headlineContent = { Text(a.url.substringAfterLast("/")) },
                                                                 supportingContent = { Text(a.mediaType) },
                                                                 trailingContent = {
                                                                     IconButton(onClick = {
                                                                         try {
                                                                             val fullUrl = if (a.url.startsWith("/")) "http://localhost:8080${a.url}" else a.url
                                                                             java.awt.Desktop.getDesktop().browse(java.net.URI(fullUrl))
                                                                         } catch (e: Exception) {}
                                                                     }) {
                                                                         Icon(Icons.Default.OpenInNew, contentDescription = "Open natively")
                                                                     }
                                                                 }
                                                             )
                                                             InlineAttachmentViewer(a)
                                                         }
                                                     }
                                                 }

                                                 if (detailVisit.prescriptions.isNotEmpty()) {
                                                     Text("Prescriptions:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                                     Column(modifier = Modifier.fillMaxWidth()) {
                                                         detailVisit.prescriptions.forEach { p ->
                                                             val name = inventoryItemsList.find { it.id == p.medicationId }?.medicationName ?: p.medicationId
                                                             ListItem(
                                                                 headlineContent = { Text("$name x ${p.quantity}") },
                                                                 supportingContent = { Text("${p.dosage} | ${p.frequency} | ${p.duration} [Dispensed: ${p.dispensed}]") }
                                                             )
                                                         }
                                                     }
                                                 }

                                                 Spacer(modifier = Modifier.height(16.dp))

                                                 OutlinedCard(
                                                     colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                                     shape = RoundedCornerShape(8.dp)
                                                 ) {
                                                     Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                         Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                             Text("Clinical Invoice", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                             val badgeColor = if (detailVisit.paymentStatus == PaymentStatus.PAID) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                                                             Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(badgeColor).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                                 Text(detailVisit.paymentStatus.name, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                                             }
                                                         }
                                                         Text("Fee: Rs. ${detailVisit.requiredPaymentAmount} | Paid: PhonePe: Rs. ${detailVisit.amountPhonePe}, Cash: Rs. ${detailVisit.amountCash}", style = MaterialTheme.typography.bodySmall)

                                                         if (detailVisit.paymentStatus == PaymentStatus.PENDING) {
                                                             var payPhonePe by remember(detailVisit.id) { mutableStateOf("") }
                                                             var payCash by remember(detailVisit.id) { mutableStateOf("") }
                                                             Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                                 OutlinedTextField(value = payPhonePe, onValueChange = { payPhonePe = it }, label = { Text("PhonePe") }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                                                 OutlinedTextField(value = payCash, onValueChange = { payCash = it }, label = { Text("Cash") }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                                             }
                                                             Button(
                                                                 onClick = {
                                                                     scope.launch {
                                                                         try {
                                                                             val pP = payPhonePe.toDoubleOrNull() ?: 0.0
                                                                             val pC = payCash.toDoubleOrNull() ?: 0.0
                                                                             val resp = ApiClient.client.post(ApiV1.VisitPay(id = detailVisit.id)) {
                                                                                 url { host = "localhost"; port = 8080 }
                                                                                 contentType(ContentType.Application.Json)
                                                                                 setBody(PaymentRequest(pP, pC))
                                                                             }
                                                                             if (resp.status == HttpStatusCode.OK) {
                                                                                 val updatedVisit: ClientVisit = resp.body()
                                                                                 selectedVisitDetail = updatedVisit
                                                                                 selectedPatientForVisits?.let { fetchVisitsForPatient(it.id) }
                                                                             }
                                                                         } catch (e: Exception) {}
                                                                     }
                                                                 },
                                                                 shape = RoundedCornerShape(8.dp),
                                                                 modifier = Modifier.padding(top = 4.dp)
                                                             ) {
                                                                 Text("Settle Clinical Fee")
                                                             }
                                                         }
                                                     }
                                                 }

                                                 if (detailVisit.pharmacyRequiredAmount > 0.0) {
                                                     Spacer(modifier = Modifier.height(12.dp))
                                                     OutlinedCard(
                                                         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                                         shape = RoundedCornerShape(8.dp)
                                                     ) {
                                                         Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                             Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                 Text("Pharmacy Invoice", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                                 val badgeColor = if (detailVisit.pharmacyPaymentStatus == PaymentStatus.PAID) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                                                                 Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(badgeColor).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                                     Text(detailVisit.pharmacyPaymentStatus.name, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                                                 }
                                                             }
                                                             Text("Drugs Cost: Rs. ${detailVisit.pharmacyRequiredAmount} | Paid: PhonePe: Rs. ${detailVisit.pharmacyAmountPhonePe}, Cash: Rs. ${detailVisit.pharmacyAmountCash}", style = MaterialTheme.typography.bodySmall)

                                                             if (detailVisit.pharmacyPaymentStatus == PaymentStatus.PENDING) {
                                                                 var dispensePhonePe by remember(detailVisit.id) { mutableStateOf("") }
                                                                 var dispenseCash by remember(detailVisit.id) { mutableStateOf("") }
                                                                 Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                                     OutlinedTextField(value = dispensePhonePe, onValueChange = { dispensePhonePe = it }, label = { Text("PhonePe") }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                                                     OutlinedTextField(value = dispenseCash, onValueChange = { dispenseCash = it }, label = { Text("Cash") }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                                                 }
                                                                 Button(
                                                                     onClick = {
                                                                         scope.launch {
                                                                             try {
                                                                                 val dP = dispensePhonePe.toDoubleOrNull() ?: 0.0
                                                                                 val dC = dispenseCash.toDoubleOrNull() ?: 0.0
                                                                                 val resp = ApiClient.client.post(ApiV1.VisitDispense(id = detailVisit.id)) {
                                                                                     url { host = "localhost"; port = 8080 }
                                                                                     contentType(ContentType.Application.Json)
                                                                                     setBody(PaymentRequest(dP, dC))
                                                                                 }
                                                                                 if (resp.status == HttpStatusCode.OK) {
                                                                                     val updatedVisit: ClientVisit = resp.body()
                                                                                     selectedVisitDetail = updatedVisit
                                                                                     selectedPatientForVisits?.let { fetchVisitsForPatient(it.id) }
                                                                                     fetchInventory()
                                                                                 }
                                                                             } catch (e: Exception) {}
                                                                         }
                                                                     },
                                                                     shape = RoundedCornerShape(8.dp),
                                                                     modifier = Modifier.padding(top = 4.dp)
                                                                 ) {
                                                                     Text("Dispense & Settle Drugs")
                                                                 }
                                                             }
                                                         }
                                                     }
                                                 }

                                                 Spacer(modifier = Modifier.height(16.dp))
                                                 Button(
                                                     onClick = { rightVisitsMode = RightVisitsMode.LIST; selectedVisitDetail = null },
                                                     modifier = Modifier.fillMaxWidth(),
                                                     shape = RoundedCornerShape(24.dp)
                                                 ) {
                                                     Text("Back to History List", fontWeight = FontWeight.Bold)
                                                 }
                                             }
                                        } else {
                                            Column(modifier = Modifier.padding(24.dp)) {
                                            Text("Visit History & Invoices", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(16.dp))

                                            var visitSearchId by remember { mutableStateOf("") }
                                            var showSearchError by remember { mutableStateOf(false) }

                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = visitSearchId,
                                                    onValueChange = { visitSearchId = it; showSearchError = false },
                                                    placeholder = { Text("Quick View by Visit ID") },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f),
                                                    singleLine = true,
                                                    trailingIcon = {
                                                        if (visitSearchId.isNotEmpty()) {
                                                            IconButton(onClick = { visitSearchId = "" }) {
                                                                Icon(Icons.Default.Close, contentDescription = "Clear")
                                                            }
                                                        }
                                                    },
                                                    isError = showSearchError
                                                )
                                                Button(
                                                    onClick = {
                                                        if (visitSearchId.isNotEmpty()) {
                                                            scope.launch {
                                                                try {
                                                                    val response = ApiClient.client.get(ApiV1.VisitDetail(id = visitSearchId)) {
                                                                        url { host = "localhost"; port = 8080 }
                                                                    }
                                                                    if (response.status == HttpStatusCode.OK) {
                                                                        val result: ClientVisit = response.body()
														selectedVisitDetail = result
														rightVisitsMode = RightVisitsMode.DETAIL
                                                                        showSearchError = false
                                                                    } else {
                                                                        showSearchError = true
                                                                    }
                                                                } catch (e: Exception) {
                                                                    showSearchError = true
                                                                }
                                                            }
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.height(56.dp)
                                                ) {
                                                    Icon(Icons.Default.Search, contentDescription = "Search ID")
                                                }
                                            }
                                            if (showSearchError) {
                                                Text("Visit ID not found. Try again.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                                            }

                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                items(patientVisitsList) { visit ->
                                                    OutlinedCard(
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.fillMaxWidth().clickable {
                                                            selectedVisitDetail = visit
                                                            rightVisitsMode = RightVisitsMode.DETAIL
                                                        }
                                                    ) {
                                                         Row(
                                                             modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                                             horizontalArrangement = Arrangement.SpaceBetween,
                                                             verticalAlignment = Alignment.CenterVertically
                                                         ) {
                                                             Column(modifier = Modifier.weight(1f)) {
                                                                 Text("Walk-in: ${visit.type}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                                 val date = java.time.Instant.ofEpochMilli(visit.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                                                 Text("Date: $date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                 Spacer(modifier = Modifier.height(4.dp))
                                                                 Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                     val clinicalColor = if (visit.paymentStatus == PaymentStatus.PAID) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                                                                     Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(clinicalColor).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                                         Text("Clinical: ${visit.paymentStatus.name}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                                                     }
                                                                     if (visit.pharmacyRequiredAmount > 0.0) {
                                                                         val pharmacyColor = if (visit.pharmacyPaymentStatus == PaymentStatus.PAID) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                                                                         Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(pharmacyColor).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                                                             Text("Pharmacy: ${visit.pharmacyPaymentStatus.name}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                                                         }
                                                                     }
                                                                 }
                                                             }
                                                             Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                 IconButton(onClick = {
                                                                     editingVisitId = visit.id
                                                                     visitType = visit.type
                                                                     visitCost = visit.requiredPaymentAmount.toInt().toString()
                                                                     visitNotes = visit.notes

                                                                     visitSymptoms.clear()
                                                                     visitSymptoms.addAll(visit.symptoms)

                                                                     visitDiagnoses.clear()
                                                                     visitDiagnoses.addAll(visit.diagnoses)

                                                                     visitTreatments.clear()
                                                                     visitTreatments.addAll(visit.treatments)

                                                                     prescriptionsList.clear()
                                                                     prescriptionsList.addAll(visit.prescriptions)

                                                                                 attachmentsList.clear()
                                                                     attachmentsList.addAll(visit.attachments)

                                                                     validationErrors = emptyMap()
                                                                     infoMessage = ""
                                                                     rightVisitsMode = RightVisitsMode.LIST
                                                                 }) {
                                                                     Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Visit Details", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                                 }
                                                                 Text("ID: ${visit.id.take(8)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                             }
                                                         }
                                                    }
                                                }
                                            }
                                        }
                                        }
                                    }
                                }
                            }
                            AppScreen.INVENTORY -> {
                                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    ElevatedCard(
                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                                            Text("Inventory Stock Update", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(16.dp))

                                            OutlinedTextField(value = invMedicationName, onValueChange = { invMedicationName = it }, label = { Text("Medication Name") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), singleLine = true, isError = validationErrors.containsKey("medicationName"))
                                            validationErrors["medicationName"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                            Spacer(modifier = Modifier.height(8.dp))

                                            OutlinedTextField(value = invQuantity, onValueChange = { invQuantity = it }, label = { Text("Quantity") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), singleLine = true, isError = validationErrors.containsKey("quantity"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                            validationErrors["quantity"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                            Spacer(modifier = Modifier.height(8.dp))

                                            OutlinedTextField(value = invPrice, onValueChange = { invPrice = it }, label = { Text("Price Per Unit (Rs.)") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), singleLine = true, isError = validationErrors.containsKey("pricePerUnit"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                            validationErrors["pricePerUnit"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                            Spacer(modifier = Modifier.height(24.dp))

                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            validationErrors = emptyMap()
                                                            infoMessage = ""
                                                            val qty = invQuantity.toIntOrNull() ?: 0
                                                            val pr = invPrice.toDoubleOrNull() ?: 0.0
                                                            val response = ApiClient.client.post(ApiV1.Inventory()) {
                                                                url { host = "localhost"; port = 8080 }
                                                                contentType(ContentType.Application.Json)
                                                                setBody(InventoryUpdateRequest(invMedicationName, qty, pr))
                                                            }
                                                            if (response.status == HttpStatusCode.OK) {
                                                                infoMessage = ""
                                                                fetchInventory()
                                                                invMedicationName = ""; invQuantity = ""; invPrice = ""
                                                            } else {
                                                                handleApiError(response)
                                                            }
                                                        } catch (e: Exception) {
                                                            infoMessage = e.message ?: "Connection error"
                                                        }
                                                    }
                                                },
                                                shape = RoundedCornerShape(24.dp),
                                                modifier = Modifier.fillMaxWidth().height(48.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Update Inventory")
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Update Stock Catalog")
                                            }
                                        }
                                    }

                                    ElevatedCard(
                                        modifier = Modifier.weight(1.5f).fillMaxHeight(),
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(24.dp)) {
                                            Text("Pharmacy Inventory Catalog", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.height(16.dp))

                                            LazyVerticalGrid(
                                                columns = GridCells.Fixed(2),
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                items(inventoryItemsList) { item ->
                                                    OutlinedCard(shape = RoundedCornerShape(12.dp)) {
                                                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Text(item.medicationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                            Text("Stock: ${item.quantity} units", style = MaterialTheme.typography.bodyMedium, color = if (item.quantity > 5) Color(0xFF81C784) else Color(0xFFE57373))
                                                            Text("Rate: Rs. ${item.pricePerUnit} / unit", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InlineAttachmentViewer(a: VisitAttachment) {
    val fullUrl = if (a.url.startsWith("/")) "http://localhost:8080${a.url}" else a.url
    if (a.mediaType == "IMAGE") {
        var imageBitmap by remember(fullUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
        LaunchedEffect(fullUrl) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val bytes: ByteArray = ApiClient.client.get(fullUrl).body()
                    imageBitmap = org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        imageBitmap?.let {
            androidx.compose.foundation.Image(
                bitmap = it,
                contentDescription = "Attachment Image",
                modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.outline)
            )
        } ?: Text("Loading Image...", style = MaterialTheme.typography.bodySmall)
    } else if (a.mediaType == "VIDEO") {
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().height(120.dp).clickable {
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI(fullUrl))
                } catch (e: Exception) {}
            },
            shape = RoundedCornerShape(8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.PlayCircle, contentDescription = "Play Video", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Play Video: ${a.url.substringAfterLast("/")}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
