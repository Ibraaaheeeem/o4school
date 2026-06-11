package com.haneef.school.ui.screens.assessments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AssessmentsScreen(route: String) {
    val section = route.substringAfter("assessments/")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "📝 Assessments", fontSize = 28.sp, color = Color(0xFF4A5FBF))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = section.replaceFirstChar { it.uppercase() }, fontSize = 20.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Manage exams, scoring, and academic reports.", fontSize = 14.sp, color = Color.Gray)
        }
    }
}
