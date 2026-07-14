package org.advgnd.atrium

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

enum class AppScreen {
    LOGIN, REGISTER, PATIENTS, VISITS, INVENTORY
}

@Serializable
data class ClientPatient(
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
    val pharmacyPaymentStatus: PaymentStatus
)

@Serializable
data class ClientInventoryItem(
    val id: String,
    val medicationName: String,
    val quantity: Int,
    val pricePerUnit: Double,
    val updatedAt: Long
)

@Composable
fun App() {
    MaterialTheme {
        var currentScreen by remember { mutableStateOf(AppScreen.LOGIN) }
        var isLoggedIn by remember { mutableStateOf(false) }
        var infoMessage by remember { mutableStateOf("") }
        var validationErrors by remember { mutableStateOf(emptyMap<String, String>()) }
        val scope = rememberCoroutineScope()

        var loginEmail by remember { mutableStateOf("") }
        var loginPassword by remember { mutableStateOf("") }

        var regEmail by remember { mutableStateOf("") }
        var regPassword by remember { mutableStateOf("") }

        var patName by remember { mutableStateOf("") }
        var patDob by remember { mutableStateOf("") }
        var patGender by remember { mutableStateOf("") }
        var patContact by remember { mutableStateOf("") }
        var patEmail by remember { mutableStateOf("") }
        var patAddress by remember { mutableStateOf("") }
        var patientsList by remember { mutableStateOf(emptyList<ClientPatient>()) }
        var selectedPatientForVisits by remember { mutableStateOf<ClientPatient?>(null) }

        var visitType by remember { mutableStateOf("") }
        var visitCost by remember { mutableStateOf("") }
        var visitSymptomsRaw by remember { mutableStateOf("") }
        var visitDiagnosesRaw by remember { mutableStateOf("") }
        var visitTreatmentsRaw by remember { mutableStateOf("") }
        
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

        var invMedicationName by remember { mutableStateOf("") }
        var invQuantity by remember { mutableStateOf("") }
        var invPrice by remember { mutableStateOf("") }
        var inventoryItemsList by remember { mutableStateOf(emptyList<ClientInventoryItem>()) }

        fun fetchPatients() {
            scope.launch {
                try {
                    val response = ApiClient.client.get(ApiV1.Patients()) {
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
                        infoMessage = "Validation failed"
                    } else {
                        val bodyText = response.call.body<MessageResponse>()
                        infoMessage = bodyText.message
                    }
                } catch (e: Exception) {
                    infoMessage = "Error processing response: ${response.status}"
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(text = "Hospital Information System Backend Client", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoggedIn) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { currentScreen = AppScreen.PATIENTS; validationErrors = emptyMap(); infoMessage = ""; fetchPatients() }) { Text("Patients") }
                    Button(onClick = { currentScreen = AppScreen.VISITS; validationErrors = emptyMap(); infoMessage = "" }) { Text("Visits") }
                    Button(onClick = { currentScreen = AppScreen.INVENTORY; validationErrors = emptyMap(); infoMessage = ""; fetchInventory() }) { Text("Inventory") }
                    Button(onClick = {
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
                    }) { Text("Logout") }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (infoMessage.isNotEmpty()) {
                Text(text = infoMessage, color = Color.Red)
                Spacer(modifier = Modifier.height(8.dp))
            }

            when (currentScreen) {
                AppScreen.LOGIN -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Login Screen")
                        TextField(value = loginEmail, onValueChange = { loginEmail = it }, label = { Text("Email") })
                        validationErrors["email"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                        
                        TextField(value = loginPassword, onValueChange = { loginPassword = it }, label = { Text("Password") })
                        validationErrors["password"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }

                        Button(onClick = {
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
                                        infoMessage = "Login successful"
                                        fetchPatients()
                                        fetchInventory()
                                    } else {
                                        handleApiError(response)
                                    }
                                } catch (e: Exception) {
                                    infoMessage = e.message ?: "Connection error"
                                }
                            }
                        }) { Text("Login") }
                        Button(onClick = { currentScreen = AppScreen.REGISTER; validationErrors = emptyMap(); infoMessage = "" }) { Text("Go to Register") }
                    }
                }
                AppScreen.REGISTER -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Register Screen")
                        TextField(value = regEmail, onValueChange = { regEmail = it }, label = { Text("Email") })
                        validationErrors["email"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }

                        TextField(value = regPassword, onValueChange = { regPassword = it }, label = { Text("Password") })
                        validationErrors["password"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }

                        Button(onClick = {
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
                        }) { Text("Register") }
                        Button(onClick = { currentScreen = AppScreen.LOGIN; validationErrors = emptyMap(); infoMessage = "" }) { Text("Go to Login") }
                    }
                }
                AppScreen.PATIENTS -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Add Patient")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                TextField(value = patName, onValueChange = { patName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                                validationErrors["name"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                TextField(value = patDob, onValueChange = { patDob = it }, label = { Text("DOB (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                                validationErrors["dateOfBirth"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                TextField(value = patGender, onValueChange = { patGender = it }, label = { Text("Gender") }, modifier = Modifier.fillMaxWidth())
                                validationErrors["gender"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                TextField(value = patContact, onValueChange = { patContact = it }, label = { Text("Contact") }, modifier = Modifier.fillMaxWidth())
                                validationErrors["contactNumber"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                TextField(value = patEmail, onValueChange = { patEmail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                                validationErrors["email"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                TextField(value = patAddress, onValueChange = { patAddress = it }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
                                validationErrors["address"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = {
                            scope.launch {
                                try {
                                    validationErrors = emptyMap()
                                    infoMessage = ""
                                    val response = ApiClient.client.post(ApiV1.Patients()) {
                                        url { host = "localhost"; port = 8080 }
                                        contentType(ContentType.Application.Json)
                                        setBody(PatientRequest(patName, patDob, patGender, patContact, patEmail, patAddress))
                                    }
                                    if (response.status == HttpStatusCode.Created) {
                                        infoMessage = "Patient registered"
                                        fetchPatients()
                                        patName = ""; patDob = ""; patGender = ""; patContact = ""; patEmail = ""; patAddress = ""
                                    } else {
                                        handleApiError(response)
                                    }
                                } catch (e: Exception) {
                                    infoMessage = e.message ?: "Connection error"
                                }
                            }
                        }) { Text("Register Patient") }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Patients List")
                        LazyColumn(modifier = Modifier.height(200.dp)) {
                            items(patientsList) { patient ->
                                Row(modifier = Modifier.fillMaxWidth().clickable {
                                    selectedPatientForVisits = patient
                                    fetchVisitsForPatient(patient.id)
                                    fetchInventory()
                                    validationErrors = emptyMap()
                                    infoMessage = ""
                                    currentScreen = AppScreen.VISITS
                                }.padding(8.dp).border(1.dp, Color.Gray).padding(8.dp)) {
                                    Text("${patient.name} (${patient.gender}, ${patient.dateOfBirth}) ID: ${patient.id}")
                                }
                            }
                        }
                    }
                }
                AppScreen.VISITS -> {
                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrollState)) {
                        Text("Selected Patient: ${selectedPatientForVisits?.name ?: "None"}")
                        selectedPatientForVisits?.let { pat ->
                            Text("Record New Walk-in Visit")
                            
                            TextField(value = visitType, onValueChange = { visitType = it }, label = { Text("Visit Type") })
                            validationErrors["type"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }

                            TextField(value = visitCost, onValueChange = { visitCost = it }, label = { Text("Required Cost") })
                            validationErrors["requiredPaymentAmount"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }

                            TextField(value = visitSymptomsRaw, onValueChange = { visitSymptomsRaw = it }, label = { Text("Symptoms (comma-separated)") })
                            TextField(value = visitDiagnosesRaw, onValueChange = { visitDiagnosesRaw = it }, label = { Text("Diagnoses (comma-separated)") })
                            TextField(value = visitTreatmentsRaw, onValueChange = { visitTreatmentsRaw = it }, label = { Text("Treatments (comma-separated)") })

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Prescriptions (Link directly to Pharmacy Inventory)")
                            validationErrors["prescriptions"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                            
                            var drugNameDisplay by remember { mutableStateOf("Select Drug...") }
                            Column(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray).padding(8.dp)) {
                                Text(drugNameDisplay, style = MaterialTheme.typography.bodyLarge)
                                LazyColumn(modifier = Modifier.height(100.dp)) {
                                    items(inventoryItemsList) { item ->
                                        Text(
                                            text = "${item.medicationName} (Avail: ${item.quantity})",
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                selectedMedicationId = item.id
                                                drugNameDisplay = item.medicationName
                                            }.padding(4.dp)
                                        )
                                    }
                                }
                            }
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextField(value = prescQtyInput, onValueChange = { prescQtyInput = it }, label = { Text("Quantity") }, modifier = Modifier.weight(1f))
                                TextField(value = dosageInput, onValueChange = { dosageInput = it }, label = { Text("Dosage") }, modifier = Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextField(value = frequencyInput, onValueChange = { frequencyInput = it }, label = { Text("Frequency") }, modifier = Modifier.weight(1f))
                                TextField(value = durationInput, onValueChange = { durationInput = it }, label = { Text("Duration") }, modifier = Modifier.weight(1f))
                            }
                            Button(onClick = {
                                val qty = prescQtyInput.toIntOrNull() ?: 0
                                if (selectedMedicationId.isNotEmpty() && qty > 0) {
                                    prescriptionsList.add(Prescription(selectedMedicationId, qty, dosageInput, frequencyInput, durationInput))
                                    selectedMedicationId = ""
                                    prescQtyInput = ""
                                    dosageInput = ""
                                    frequencyInput = ""
                                    durationInput = ""
                                    drugNameDisplay = "Select Drug..."
                                }
                            }) { Text("Add Prescription") }
                            prescriptionsList.forEach { p ->
                                val name = inventoryItemsList.find { it.id == p.medicationId }?.medicationName ?: p.medicationId
                                Text("- $name x ${p.quantity}: ${p.dosage}, ${p.frequency}, ${p.duration}")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Attachments")
                            TextField(value = attachmentUrl, onValueChange = { attachmentUrl = it }, label = { Text("Attachment URL") })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { attachmentType = "IMAGE" }) { Text("IMAGE") }
                                Button(onClick = { attachmentType = "VIDEO" }) { Text("VIDEO") }
                            }
                            Button(onClick = {
                                if (attachmentUrl.isNotEmpty()) {
                                    attachmentsList.add(VisitAttachment(attachmentUrl, attachmentType))
                                    attachmentUrl = ""
                                }
                            }) { Text("Add Attachment") }
                            attachmentsList.forEach { a ->
                                Text("- ${a.url} (${a.mediaType})")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        validationErrors = emptyMap()
                                        infoMessage = ""
                                        val syms = visitSymptomsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        val diags = visitDiagnosesRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        val treats = visitTreatmentsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        val cost = visitCost.toDoubleOrNull() ?: 0.0

                                        val response = ApiClient.client.post(ApiV1.PatientVisits(id = pat.id)) {
                                            url { host = "localhost"; port = 8080 }
                                            contentType(ContentType.Application.Json)
                                            setBody(VisitRequest(visitType, cost, syms, diags, treats, prescriptionsList.toList(), attachmentsList.toList()))
                                        }
                                        if (response.status == HttpStatusCode.Created) {
                                            val savedVisit = response.body<ClientVisit>()
                                            infoMessage = "Visit saved successfully. ID: ${savedVisit.id}"
                                            fetchVisitsForPatient(pat.id)
                                            prescriptionsList.clear()
                                            attachmentsList.clear()
                                            visitType = ""; visitCost = ""; visitSymptomsRaw = ""; visitDiagnosesRaw = ""; visitTreatmentsRaw = ""
                                        } else {
                                            handleApiError(response)
                                        }
                                    } catch (e: Exception) {
                                        infoMessage = e.message ?: "Connection error"
                                    }
                                }
                            }) { Text("Record Visit") }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Visit History")
                            patientVisitsList.forEach { visit ->
                                Box(modifier = Modifier.fillMaxWidth().border(1.dp, Color.Gray).padding(8.dp)) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Visit ID: ${visit.id} | Type: ${visit.type}")
                                        Text("Symptoms: ${visit.symptoms.joinToString(", ")}")
                                        Text("Diagnoses: ${visit.diagnoses.joinToString(", ")}")
                                        Text("Treatments: ${visit.treatments.joinToString(", ")}")
                                        Text("Prescriptions:")
                                        visit.prescriptions.forEach { p ->
                                            val name = inventoryItemsList.find { it.id == p.medicationId }?.medicationName ?: p.medicationId
                                            Text("  - $name x ${p.quantity} (${p.dosage} | ${p.frequency} | ${p.duration}) [Dispensed: ${p.dispensed}]")
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(4.dp)) {
                                            Column {
                                                Text("Clinical Fee Billing", style = MaterialTheme.typography.labelLarge)
                                                Text("Fee: Rs. ${visit.requiredPaymentAmount} | Paid: PhonePe: Rs. ${visit.amountPhonePe}, Cash: Rs. ${visit.amountCash}")
                                                Text("Status: ${visit.paymentStatus}")
                                                if (visit.paymentStatus == PaymentStatus.PENDING) {
                                                    var payPhonePe by remember { mutableStateOf("") }
                                                    var payCash by remember { mutableStateOf("") }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        TextField(value = payPhonePe, onValueChange = { payPhonePe = it }, label = { Text("PhonePe") }, modifier = Modifier.weight(1f))
                                                        TextField(value = payCash, onValueChange = { payCash = it }, label = { Text("Cash") }, modifier = Modifier.weight(1f))
                                                    }
                                                    validationErrors["amountPhonePe"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                                                    validationErrors["amountCash"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                                                    validationErrors[""]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                                                    Button(onClick = {
                                                        scope.launch {
                                                            try {
                                                                validationErrors = emptyMap()
                                                                infoMessage = ""
                                                                val pP = payPhonePe.toDoubleOrNull() ?: 0.0
                                                                val pC = payCash.toDoubleOrNull() ?: 0.0
                                                                val resp = ApiClient.client.post(ApiV1.VisitPay(id = visit.id)) {
                                                                    url { host = "localhost"; port = 8080 }
                                                                    contentType(ContentType.Application.Json)
                                                                    setBody(PaymentRequest(pP, pC))
                                                                }
                                                                if (resp.status == HttpStatusCode.OK) {
                                                                    infoMessage = "Clinical fee paid successfully"
                                                                    fetchVisitsForPatient(pat.id)
                                                                } else {
                                                                    handleApiError(resp)
                                                                }
                                                            } catch (e: Exception) {
                                                                infoMessage = e.message ?: "Connection error"
                                                            }
                                                        }
                                                    }) { Text("Settle Fee") }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFDDDDDD)).padding(4.dp)) {
                                            Column {
                                                Text("Pharmacy Billing", style = MaterialTheme.typography.labelLarge)
                                                Text("Medications Cost: Rs. ${visit.pharmacyRequiredAmount} | Paid: PhonePe: Rs. ${visit.pharmacyAmountPhonePe}, Cash: Rs. ${visit.pharmacyAmountCash}")
                                                Text("Status: ${visit.pharmacyPaymentStatus}")
                                                if (visit.pharmacyPaymentStatus == PaymentStatus.PENDING && visit.pharmacyRequiredAmount > 0.0) {
                                                    var dispensePhonePe by remember { mutableStateOf("") }
                                                    var dispenseCash by remember { mutableStateOf("") }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        TextField(value = dispensePhonePe, onValueChange = { dispensePhonePe = it }, label = { Text("PhonePe") }, modifier = Modifier.weight(1f))
                                                        TextField(value = dispenseCash, onValueChange = { dispenseCash = it }, label = { Text("Cash") }, modifier = Modifier.weight(1f))
                                                    }
                                                    validationErrors["amountPhonePe"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                                                    validationErrors["amountCash"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                                                    validationErrors[""]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }
                                                    Button(onClick = {
                                                        scope.launch {
                                                            try {
                                                                validationErrors = emptyMap()
                                                                infoMessage = ""
                                                                val dP = dispensePhonePe.toDoubleOrNull() ?: 0.0
                                                                val dC = dispenseCash.toDoubleOrNull() ?: 0.0
                                                                val resp = ApiClient.client.post(ApiV1.VisitDispense(id = visit.id)) {
                                                                    url { host = "localhost"; port = 8080 }
                                                                    contentType(ContentType.Application.Json)
                                                                    setBody(PaymentRequest(dP, dC))
                                                                }
                                                                if (resp.status == HttpStatusCode.OK) {
                                                                    infoMessage = "Pharmacy items dispensed and paid"
                                                                    fetchVisitsForPatient(pat.id)
                                                                    fetchInventory()
                                                                } else {
                                                                    handleApiError(resp)
                                                                }
                                                            } catch (e: Exception) {
                                                                infoMessage = e.message ?: "Connection error"
                                                            }
                                                        }
                                                    }) { Text("Dispense & Settle Drugs") }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        } ?: Text("Please select a patient first from the Patients tab.")
                    }
                }
                AppScreen.INVENTORY -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Add/Update Stock")
                        TextField(value = invMedicationName, onValueChange = { invMedicationName = it }, label = { Text("Medication Name") })
                        validationErrors["medicationName"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }

                        TextField(value = invQuantity, onValueChange = { invQuantity = it }, label = { Text("Quantity") })
                        validationErrors["quantity"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }

                        TextField(value = invPrice, onValueChange = { invPrice = it }, label = { Text("Price Per Unit") })
                        validationErrors["pricePerUnit"]?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.bodySmall) }

                        Button(onClick = {
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
                                        infoMessage = "Stock updated"
                                        fetchInventory()
                                        invMedicationName = ""; invQuantity = ""; invPrice = ""
                                    } else {
                                        handleApiError(response)
                                    }
                                } catch (e: Exception) {
                                    infoMessage = e.message ?: "Connection error"
                                }
                            }
                        }) { Text("Update Stock") }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Stock Inventory List (Medication IDs displayed here)")
                        LazyColumn(modifier = Modifier.height(200.dp)) {
                            items(inventoryItemsList) { item ->
                                Box(modifier = Modifier.fillMaxWidth().border(1.dp, Color.LightGray).padding(8.dp)) {
                                    Text("${item.medicationName} | ID: ${item.id} | Qty: ${item.quantity} | Price: Rs. ${item.pricePerUnit}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
