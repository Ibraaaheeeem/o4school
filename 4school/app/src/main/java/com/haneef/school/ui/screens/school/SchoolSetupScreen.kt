package com.haneef.school.ui.screens.school

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haneef.school.data.local.PreferencesManager
import com.haneef.school.data.models.SchoolData
import com.haneef.school.viewmodel.SchoolViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

// TODO: Replace with your Cloudinary cloud name and unsigned upload preset
private const val CLOUDINARY_CLOUD_NAME = "your_cloud_name"
private const val CLOUDINARY_UPLOAD_PRESET = "your_upload_preset"

private val currencyOptions = listOf(
    "NGN" to "NGN - Nigerian Naira 🇳🇬",
    "USD" to "USD - US Dollar 🇺🇸",
    "GBP" to "GBP - British Pound 🇬🇧",
    "EUR" to "EUR - Euro 🇪🇺",
    "GHS" to "GHS - Ghana Cedi 🇬🇭",
    "KES" to "KES - Kenyan Shilling 🇰🇪",
    "ZAR" to "ZAR - South African Rand 🇿🇦",
    "XOF" to "XOF - West African CFA Franc 🌍",
    "EGP" to "EGP - Egyptian Pound 🇪🇬"
)
private val languageOptions = listOf(
    "en" to "English", "fr" to "French", "ar" to "Arabic",
    "sw" to "Swahili", "yo" to "Yoruba", "ig" to "Igbo", "ha" to "Hausa",
    "es" to "Spanish", "pt" to "Portuguese"
)
private val timezoneOptions = listOf(
    "Africa/Lagos" to "Africa/Lagos (GMT+01:00)",
    "Africa/Accra" to "Africa/Accra (GMT+00:00)",
    "Africa/Nairobi" to "Africa/Nairobi (GMT+03:00)",
    "Africa/Johannesburg" to "Africa/Johannesburg (GMT+02:00)",
    "Africa/Cairo" to "Africa/Cairo (GMT+02:00)",
    "Africa/Casablanca" to "Africa/Casablanca (GMT+01:00)",
    "Africa/Addis_Ababa" to "Africa/Addis_Ababa (GMT+03:00)",
    "Europe/London" to "Europe/London (GMT+00:00)",
    "Europe/Paris" to "Europe/Paris (GMT+01:00)",
    "America/New_York" to "America/New_York (GMT-05:00)",
    "Asia/Dubai" to "Asia/Dubai (GMT+04:00)",
    "UTC" to "UTC (GMT+00:00)"
)
private val colorPresets = listOf(
    0xFF4A5FBF, 0xFF2E7D32, 0xFF9C27B0, 0xFFD32F2F,
    0xFFFF6F00, 0xFF0277BD, 0xFF00695C, 0xFF37474F,
    0xFF1E293B, 0xFF6D4C41, 0xFF4527A0, 0xFFC62828
).map { Color(it) }

private fun parseHexColor(hex: String): Color? = try {
    val h = if (hex.startsWith("#")) hex else "#$hex"
    Color(android.graphics.Color.parseColor(h))
} catch (_: Exception) { null }

private suspend fun uploadToCloudinary(context: Context, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bytes = stream.readBytes()
            stream.close()
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("gif") -> "gif"
                mime.contains("webp") -> "webp"
                else -> "jpg"
            }
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "upload.$ext", bytes.toRequestBody(mime.toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", CLOUDINARY_UPLOAD_PRESET)
                .build()
            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/image/upload")
                .post(body)
                .build()
            val response = OkHttpClient().newCall(request).execute()
            if (response.isSuccessful) {
                JSONObject(response.body?.string() ?: return@withContext null).getString("secure_url")
            } else null
        } catch (_: Exception) { null }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolSetupScreen(route: String = "", modifier: Modifier = Modifier) {
    val schoolViewModel: SchoolViewModel = koinViewModel()
    val preferencesManager: PreferencesManager = koinInject()
    val schoolUiState by schoolViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val accessToken = preferencesManager.getAccessToken()
    val schoolId = preferencesManager.getSchoolId()
    val currentSchoolData = schoolUiState.currentSchoolData
    LaunchedEffect(schoolId){
        if (!schoolId.isNullOrEmpty() && !accessToken.isNullOrEmpty())
            schoolViewModel.getCurrentSchoolData(schoolId!!, accessToken!!)
    }


    var schoolName by remember {mutableStateOf(currentSchoolData?.name ?: "")}
    var schoolSlug by remember { mutableStateOf(currentSchoolData?.slug ?: "") }
    var schoolMotto by remember { mutableStateOf(currentSchoolData?.schoolMotto ?: "") }
    var website by remember { mutableStateOf(currentSchoolData?.website ?: "") }
    var bannerUrl by remember { mutableStateOf(currentSchoolData?.bannerUrl ?: "") }
    var logoUrl by remember { mutableStateOf(currentSchoolData?.logoUrl ?: "") }
    var primaryColor by remember { mutableStateOf(currentSchoolData?.primaryColor ?: "") }
    var secondaryColor by remember { mutableStateOf(currentSchoolData?.secondaryColor ?: "") }
    var addressLine1 by remember { mutableStateOf(currentSchoolData?.addressLine1 ?: "") }
    var addressLine2 by remember { mutableStateOf(currentSchoolData?.addressLine2 ?: "") }
    var city by remember { mutableStateOf(currentSchoolData?.city ?: "") }
    var state by remember { mutableStateOf(currentSchoolData?.state ?: "") }
    var postalCode by remember { mutableStateOf(currentSchoolData?.postalCode ?: "") }
    var country by remember { mutableStateOf(currentSchoolData?.country ?: "") }
    var adminName by remember { mutableStateOf(currentSchoolData?.adminName ?: "") }
    var adminEmail by remember { mutableStateOf(currentSchoolData?.adminEmail ?: "") }
    var adminPhone by remember { mutableStateOf(currentSchoolData?.adminPhone ?: "") }
    var currency by remember { mutableStateOf(currentSchoolData?.currency?: "") }
    var language by remember { mutableStateOf(currentSchoolData?.language ?: "") }
    var timezone by remember { mutableStateOf(currentSchoolData?.timezone ?: "") }
    var admissionPrefix by remember { mutableStateOf(currentSchoolData?.admissionPrefix ?: "") }
    var staffIdPrefix by remember { mutableStateOf(currentSchoolData?.staffIdPrefix ?: "") }

    LaunchedEffect(currentSchoolData) {
        currentSchoolData?.let { data ->
            schoolName = data.name ?: ""
            schoolSlug = data.slug ?: ""
            schoolMotto = data.schoolMotto ?: ""
            website = data.website ?: ""
            bannerUrl = data.bannerUrl ?: ""
            logoUrl = data.logoUrl ?: ""
            primaryColor = data.primaryColor ?: ""
            secondaryColor = data.secondaryColor ?: ""
            addressLine1 = data.addressLine1 ?: ""
            addressLine2 = data.addressLine2 ?: ""
            city = data.city ?: ""
            state = data.state ?: ""
            postalCode = data.postalCode ?: ""
            country = data.country ?: ""
            adminName = data.adminName ?: ""
            adminEmail = data.adminEmail ?: ""
            adminPhone = data.adminPhone ?: ""
            currency = data.currency ?: ""
            language = data.language ?: ""
            timezone = data.timezone ?: ""
            admissionPrefix = data.admissionPrefix ?: ""
            staffIdPrefix = data.staffIdPrefix ?: ""
        }
    }

    // Upload states
    var isLogoUploading by remember { mutableStateOf(false) }
    var isBannerUploading by remember { mutableStateOf(false) }
    var logoUploadError by remember { mutableStateOf<String?>(null) }
    var bannerUploadError by remember { mutableStateOf<String?>(null) }

    // Dropdown expanded states
    var currencyExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    var timezoneExpanded by remember { mutableStateOf(false) }
    var isPrimaryColorDialogOpen by remember { mutableStateOf(false) }
    var isSecondaryColorDialogOpen by remember { mutableStateOf(false) }

    val formFingerprint = remember(
        schoolName,
        schoolSlug,
        schoolMotto,
        website,
        bannerUrl,
        logoUrl,
        primaryColor,
        secondaryColor,
        addressLine1,
        addressLine2,
        city,
        state,
        postalCode,
        country,
        adminName,
        adminEmail,
        adminPhone,
        currency,
        language,
        timezone,
        admissionPrefix,
        staffIdPrefix
    ) {
        listOf(
            schoolName,
            schoolSlug,
            schoolMotto,
            website,
            bannerUrl,
            logoUrl,
            primaryColor,
            secondaryColor,
            addressLine1,
            addressLine2,
            city,
            state,
            postalCode,
            country,
            adminName,
            adminEmail,
            adminPhone,
            currency,
            language,
            timezone,
            admissionPrefix,
            staffIdPrefix
        ).joinToString("||")
    }
    var baselineFingerprint by remember { mutableStateOf<String?>(null) }
    val hasFormChanges = baselineFingerprint != null && baselineFingerprint != formFingerprint
    val canSave = !schoolUiState.isLoaded && accessToken != null && schoolId != null && hasFormChanges

    // Image pickers
    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                isLogoUploading = true
                logoUploadError = null
                val url = uploadToCloudinary(context, it)
                if (url != null) logoUrl = url
                else logoUploadError = "Upload failed. Check Cloudinary config."
                isLogoUploading = false
            }
        }
    }

    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                isBannerUploading = true
                bannerUploadError = null
                val url = uploadToCloudinary(context, it)
                if (url != null) bannerUrl = url
                else bannerUploadError = "Upload failed. Check Cloudinary config."
                isBannerUploading = false
            }
        }
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(schoolUiState.isUpdateSuccessful) {
        if (schoolUiState.isUpdateSuccessful) {
            baselineFingerprint = formFingerprint
            kotlinx.coroutines.delay(3000)
            schoolViewModel.clearMessages()
        }
    }

    LaunchedEffect(formFingerprint) {
        if (baselineFingerprint == null) {
            baselineFingerprint = formFingerprint
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F7FA))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        
    ) {
        

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "School Profile",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Text(
                text = "Define the foundational identity and contact details for your school.",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                lineHeight = 20.sp
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFFE0F2FE), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalance,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "Institutional Identity",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }

                OutlinedTextField(
                    value = schoolName,
                    onValueChange = { schoolName = it },
                    label = { Text("School Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    enabled = schoolUiState.isLoaded
                )

                OutlinedTextField(
                    
                    value = schoolSlug,
                    onValueChange = { schoolSlug = it },
                    label = { Text("School Slug") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = schoolMotto,
                    onValueChange = { schoolMotto = it },
                    label = { Text("School Motto") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

            }
        }

        // ── School Logo ────────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "School Logo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (logoUrl.isNotBlank()) {
                            Text(text = "✓", fontSize = 24.sp, color = Color(0xFF2E7D32))
                        } else {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(28.dp))
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Button(
                            onClick = { logoPickerLauncher.launch("image/*") },
                            enabled = !isLogoUploading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isLogoUploading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Uploading...", color = Color.White, fontSize = 13.sp)
                            } else {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (logoUrl.isNotBlank()) "Replace Logo" else "Upload Logo", color = Color.White, fontSize = 13.sp)
                            }
                        }
                        if (logoUploadError != null) {
                            Text(text = logoUploadError!!, fontSize = 11.sp, color = Color(0xFFD32F2F))
                        }
                    }
                }
                if (logoUrl.isNotBlank()) {
                    OutlinedTextField(value = logoUrl, onValueChange = { logoUrl = it }, label = { Text("Logo URL") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF4A5FBF), unfocusedBorderColor = Color(0xFFE2E8F0), focusedTextColor = Color(0xFF1E293B), unfocusedTextColor = Color(0xFF64748B)))
                }
                Text(text = "Recommended: 512×512px. Max 512 KB.", fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
        }

        // ── School Banner ──────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "School Banner", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (bannerUrl.isNotBlank()) {
                        Text(text = "Banner uploaded ✓", fontSize = 13.sp, color = Color(0xFF2E7D32))
                    } else {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(28.dp))
                    }
                }
                Button(
                    onClick = { bannerPickerLauncher.launch("image/*") },
                    enabled = !isBannerUploading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isBannerUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Uploading...", color = Color.White, fontSize = 13.sp)
                    } else {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (bannerUrl.isNotBlank()) "Replace Banner" else "Upload Banner", color = Color.White, fontSize = 13.sp)
                    }
                }
                if (bannerUploadError != null) {
                    Text(text = bannerUploadError!!, fontSize = 11.sp, color = Color(0xFFD32F2F))
                }
                if (bannerUrl.isNotBlank()) {
                    OutlinedTextField(value = bannerUrl, onValueChange = { bannerUrl = it }, label = { Text("Banner URL") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF4A5FBF), unfocusedBorderColor = Color(0xFFE2E8F0), focusedTextColor = Color(0xFF1E293B), unfocusedTextColor = Color(0xFF64748B)))
                }
                Text(text = "Recommended: 1024×256px. Max 2 MB.", fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFFE0F2FE), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "Campus Location",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }

                OutlinedTextField(
                    value = addressLine1,
                    onValueChange = { addressLine1 = it },
                    label = { Text("Address Line 1") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = addressLine2,
                    onValueChange = { addressLine2 = it },
                    label = { Text("Address Line 2") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = state,
                    onValueChange = { state = it },
                    label = { Text("State") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = postalCode,
                    onValueChange = { postalCode = it },
                    label = { Text("Postal Code") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("Country") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Contact Channels",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                )

                OutlinedTextField(
                    value = adminName,
                    onValueChange = { adminName = it },
                    label = { Text("Admin Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = adminEmail,
                    onValueChange = { adminEmail = it },
                    label = { Text("Admin Email") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedTextField(
                    value = adminPhone,
                    onValueChange = { adminPhone = it },
                    label = { Text("Admin Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color(0xFFE2E8F0),
                        focusedTextColor = Color(0xFF1E293B),
                        unfocusedTextColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Localization & Identifiers",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                )

                // Currency dropdown
                ExposedDropdownMenuBox(expanded = currencyExpanded, onExpandedChange = { currencyExpanded = it }) {
                    OutlinedTextField(
                        value = currencyOptions.find { it.first == currency }?.second ?: currency.ifBlank { "Select currency" },
                        onValueChange = {}, readOnly = true,
                        label = { Text("Currency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF4A5FBF), unfocusedBorderColor = Color(0xFFE2E8F0), focusedTextColor = Color(0xFF1E293B), unfocusedTextColor = Color(0xFF64748B))
                    )
                    ExposedDropdownMenu(expanded = currencyExpanded, onDismissRequest = { currencyExpanded = false }) {
                        currencyOptions.forEach { (code, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { currency = code; currencyExpanded = false })
                        }
                    }
                }

                // Language dropdown
                ExposedDropdownMenuBox(expanded = languageExpanded, onExpandedChange = { languageExpanded = it }) {
                    val langLabel = languageOptions.find { it.first == language }?.let { "${it.first} – ${it.second}" } ?: language.ifBlank { "Select language" }
                    OutlinedTextField(
                        value = langLabel,
                        onValueChange = {}, readOnly = true,
                        label = { Text("Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF4A5FBF), unfocusedBorderColor = Color(0xFFE2E8F0), focusedTextColor = Color(0xFF1E293B), unfocusedTextColor = Color(0xFF64748B))
                    )
                    ExposedDropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                        languageOptions.forEach { (code, name) ->
                            DropdownMenuItem(text = { Text("$code – $name") }, onClick = { language = code; languageExpanded = false })
                        }
                    }
                }

                // Timezone dropdown
                ExposedDropdownMenuBox(expanded = timezoneExpanded, onExpandedChange = { timezoneExpanded = it }) {
                    OutlinedTextField(
                        value = timezoneOptions.find { it.first == timezone }?.second ?: timezone.ifBlank { "Select timezone" },
                        onValueChange = {}, readOnly = true,
                        label = { Text("Timezone") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timezoneExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF4A5FBF), unfocusedBorderColor = Color(0xFFE2E8F0), focusedTextColor = Color(0xFF1E293B), unfocusedTextColor = Color(0xFF64748B))
                    )
                    ExposedDropdownMenu(expanded = timezoneExpanded, onDismissRequest = { timezoneExpanded = false }) {
                        timezoneOptions.forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { timezone = value; timezoneExpanded = false })
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = admissionPrefix,
                        onValueChange = { admissionPrefix = it },
                        label = { Text("Admission Prefix") },
                        placeholder = { Text("ADM") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4A5FBF),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF64748B)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    OutlinedTextField(
                        value = staffIdPrefix,
                        onValueChange = { staffIdPrefix = it },
                        label = { Text("Staff ID Prefix") },
                        placeholder = { Text("STF") },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4A5FBF),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF64748B)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }

        // ── Brand Colors (moved to end) ───────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier.size(24.dp).background(Color(0xFFFCE4EC), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Palette, contentDescription = null, tint = Color(0xFFC2185B), modifier = Modifier.size(14.dp))
                    }
                    Text(text = "Brand Colors", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                }
                ColorPickerField(
                    label = "Primary Color",
                    hexValue = primaryColor,
                    onOpenDialog = { isPrimaryColorDialogOpen = true }
                )
                ColorPickerField(
                    label = "Secondary Color",
                    hexValue = secondaryColor,
                    onOpenDialog = { isSecondaryColorDialogOpen = true }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    schoolName = ""
                    schoolSlug = ""
                    schoolMotto = ""
                    website = ""
                    bannerUrl = ""
                    logoUrl = ""
                    primaryColor = ""
                    secondaryColor = ""
                    addressLine1 = ""
                    addressLine2 = ""
                    city = ""
                    state = ""
                    postalCode = ""
                    country = ""
                    adminName = ""
                    adminEmail = ""
                    adminPhone = ""
                    currency = ""
                    language = ""
                    timezone = ""
                    admissionPrefix = ""
                    staffIdPrefix = ""
                    schoolViewModel.clearMessages()
                },
                modifier = Modifier.weight(1f),
                enabled = !schoolUiState.isLoading
            ) {
                Text("Clear Form")
            }

            Button(
                onClick = {
                    if (accessToken != null && schoolId != null) {
                        val request = SchoolData(
                            name = schoolName.takeIf { it.isNotBlank() },
                            slug = schoolSlug.takeIf { it.isNotBlank() },
                            addressLine1 = addressLine1.takeIf { it.isNotBlank() },
                            addressLine2 = addressLine2.takeIf { it.isNotBlank() },
                            adminEmail = adminEmail.takeIf { it.isNotBlank() },
                            adminName = adminName.takeIf { it.isNotBlank() },
                            adminPhone = adminPhone.takeIf { it.isNotBlank() },
                            bannerUrl = bannerUrl.takeIf { it.isNotBlank() },
                            city = city.takeIf { it.isNotBlank() },
                            country = country.takeIf { it.isNotBlank() },
                            currency = currency.takeIf { it.isNotBlank() },
                            language = language.takeIf { it.isNotBlank() },
                            logoUrl = logoUrl.takeIf { it.isNotBlank() },
                            primaryColor = primaryColor.takeIf { it.isNotBlank() },
                            schoolMotto = schoolMotto.takeIf { it.isNotBlank() },
                            secondaryColor = secondaryColor.takeIf { it.isNotBlank() },
                            state = state.takeIf { it.isNotBlank() },
                            status = null,
                            timezone = timezone.takeIf { it.isNotBlank() },
                            website = website.takeIf { it.isNotBlank() },
                            admissionPrefix = admissionPrefix.takeIf { it.isNotBlank() },
                            staffIdPrefix = staffIdPrefix.takeIf { it.isNotBlank() },
                            postalCode = postalCode.takeIf { it.isNotBlank() }
                        )

                        schoolViewModel.updateSchoolData(schoolId, accessToken, request)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A5FBF))
            ) {
                if (schoolUiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving...", color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Save",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    if (isPrimaryColorDialogOpen) {
        BrandColorPickerDialog(
            title = "Select Primary Color",
            currentHex = primaryColor,
            onDismiss = { isPrimaryColorDialogOpen = false },
            onConfirm = {
                primaryColor = it
                isPrimaryColorDialogOpen = false
            }
        )
    }

    if (isSecondaryColorDialogOpen) {
        BrandColorPickerDialog(
            title = "Select Secondary Color",
            currentHex = secondaryColor,
            onDismiss = { isSecondaryColorDialogOpen = false },
            onConfirm = {
                secondaryColor = it
                isSecondaryColorDialogOpen = false
            }
        )
    }
}

@Composable
private fun ColorPickerField(
    label: String,
    hexValue: String,
    onOpenDialog: () -> Unit
) {
    val parsedColor = parseHexColor(hexValue)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, fontSize = 13.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(parsedColor ?: Color(0xFFE2E8F0))
                    .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenDialog)
            )
            OutlinedTextField(
                value = hexValue,
                onValueChange = {},
                label = { Text("Hex code") },
                placeholder = { Text("#4A5FBF") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                readOnly = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4A5FBF),
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedTextColor = Color(0xFF1E293B),
                    unfocusedTextColor = Color(0xFF64748B)
                )
            )
        }
    }
}

@Composable
private fun BrandColorPickerDialog(
    title: String,
    currentHex: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val initialHex = currentHex.takeIf { it.isNotBlank() } ?: run {
        val fallback = colorPresets.first()
        String.format(
            "#%02X%02X%02X",
            (fallback.red * 255).toInt(),
            (fallback.green * 255).toInt(),
            (fallback.blue * 255).toInt()
        )
    }
    var selectedHex by remember(currentHex) { mutableStateOf(initialHex) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Choose a color")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colorPresets.take(6).forEach { color ->
                        val hex = String.format(
                            "#%02X%02X%02X",
                            (color.red * 255).toInt(),
                            (color.green * 255).toInt(),
                            (color.blue * 255).toInt()
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedHex == hex) 3.dp else 1.dp,
                                    color = if (selectedHex == hex) Color(0xFF1E293B) else Color(0xFFCBD5E1),
                                    shape = CircleShape
                                )
                                .clickable { selectedHex = hex }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colorPresets.drop(6).forEach { color ->
                        val hex = String.format(
                            "#%02X%02X%02X",
                            (color.red * 255).toInt(),
                            (color.green * 255).toInt(),
                            (color.blue * 255).toInt()
                        )
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedHex == hex) 3.dp else 1.dp,
                                    color = if (selectedHex == hex) Color(0xFF1E293B) else Color(0xFFCBD5E1),
                                    shape = CircleShape
                                )
                                .clickable { selectedHex = hex }
                        )
                    }
                }
                OutlinedTextField(
                    value = selectedHex,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Selected hex") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedHex) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
