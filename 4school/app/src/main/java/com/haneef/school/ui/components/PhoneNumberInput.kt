package com.haneef.school.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class CountryCode(
    val code: String,
    val name: String,
    val flag: String,
    val dialCode: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneNumberInput(
    label: String,
    phoneNumber: String,
    countryCode: String = "+234",
    onPhoneNumberChange: (String) -> Unit,
    onCountryCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Phone number"
) {
    var expanded by remember { mutableStateOf(false) }
    
    val countryCodes = listOf(
        CountryCode("+234", "Nigeria", "🇳🇬", "+234"),
        CountryCode("+1", "United States", "🇺🇸", "+1"),
        CountryCode("+44", "United Kingdom", "🇬🇧", "+44"),
        CountryCode("+233", "Ghana", "🇬🇭", "+233"),
        CountryCode("+254", "Kenya", "🇰🇪", "+254"),
        CountryCode("+27", "South Africa", "🇿🇦", "+27"),
        CountryCode("+91", "India", "🇮🇳", "+91"),
        CountryCode("+86", "China", "🇨🇳", "+86"),
        CountryCode("+33", "France", "🇫🇷", "+33"),
        CountryCode("+49", "Germany", "🇩🇪", "+49")
    )
    
    val selectedCountry = countryCodes.find { it.dialCode == countryCode } ?: countryCodes.first()
    
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4A5FBF),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Country Code Selector
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.width(130.dp)
            ) {
                OutlinedTextField(
                    value = "${selectedCountry.flag} ${selectedCountry.dialCode}",
                    onValueChange = { },
                    textStyle = TextStyle(fontSize = 16.sp),
                    modifier = Modifier
                        .width(130.dp)
                        .menuAnchor(),
                    readOnly = true,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(8.dp),

                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    countryCodes.forEach { country ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = country.flag,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${country.name} ${country.dialCode}",
                                        fontSize = 14.sp
                                    )
                                }
                            },
                            onClick = {
                                onCountryCodeChange(country.dialCode)
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            // Phone Number Input
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = onPhoneNumberChange,
                modifier = Modifier.weight(1f),
                placeholder = { 
                    Text(
                        text = placeholder,
                        color = Color.Gray
                    ) 
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4A5FBF),
                    unfocusedBorderColor = Color.LightGray
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
        
        // Display combined phone number
        if (phoneNumber.isNotEmpty()) {
            Text(
                text = "Complete number: $countryCode $phoneNumber",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressInput(
    addressLine1: String,
    addressLine2: String,
    city: String,
    state: String,
    country: String,
    onAddressLine1Change: (String) -> Unit,
    onAddressLine2Change: (String) -> Unit,
    onCityChange: (String) -> Unit,
    onStateChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var countryExpanded by remember { mutableStateOf(false) }
    
    val countries = listOf(
        "Nigeria", "Ghana", "Kenya", "South Africa", "United States", "United Kingdom",
        "Canada", "Australia", "Germany", "France", "India", "China"
    )
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Address Line 1 Field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "ADDRESS LINE 1",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4A5FBF),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            OutlinedTextField(
                value = addressLine1,
                onValueChange = onAddressLine1Change,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter street address", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4A5FBF),
                    unfocusedBorderColor = Color.LightGray
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
        
        // Address Line 2 Field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "ADDRESS LINE 2",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4A5FBF),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            OutlinedTextField(
                value = addressLine2,
                onValueChange = onAddressLine2Change,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Apartment, suite, etc. (optional)", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4A5FBF),
                    unfocusedBorderColor = Color.LightGray
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
        
        // City Field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "CITY",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4A5FBF),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            OutlinedTextField(
                value = city,
                onValueChange = onCityChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your city", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4A5FBF),
                    unfocusedBorderColor = Color.LightGray
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // State Field
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "STATE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4A5FBF),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = state,
                    onValueChange = onStateChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter your state", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4A5FBF),
                        unfocusedBorderColor = Color.LightGray
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
            
            // Country Field
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "COUNTRY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4A5FBF),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                ExposedDropdownMenuBox(
                    expanded = countryExpanded,
                    onExpandedChange = { countryExpanded = !countryExpanded }
                ) {
                    OutlinedTextField(
                        value = country,
                        onValueChange = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        readOnly = true,
                        placeholder = { Text("Select country", color = Color.Gray) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF4A5FBF),
                            unfocusedBorderColor = Color.LightGray
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    
                    ExposedDropdownMenu(
                        expanded = countryExpanded,
                        onDismissRequest = { countryExpanded = false }
                    ) {
                        countries.forEach { countryName ->
                            DropdownMenuItem(
                                text = { Text(countryName) },
                                onClick = {
                                    onCountryChange(countryName)
                                    countryExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
// Utility function to get complete phone number
fun getCompletePhoneNumber(countryCode: String, phoneNumber: String): String {
    return if (phoneNumber.isNotEmpty()) {
        "$countryCode $phoneNumber"
    } else {
        ""
    }
}

// Utility function to parse complete phone number back to components
fun parsePhoneNumber(completeNumber: String): Pair<String, String> {
    val parts = completeNumber.trim().split(" ", limit = 2)
    return if (parts.size == 2) {
        Pair(parts[0], parts[1])
    } else {
        Pair("+234", completeNumber)
    }
}