package com.openlens.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlens.app.ui.theme.OpenLensColors

/** Basic processing state. Refinement (edge-trace / scan-sweep) comes later. */
@Composable
fun ScanningScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(OpenLensColors.Bg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = OpenLensColors.Accent)
        Text(
            text = "Scanning…",
            color = OpenLensColors.TextHi,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}
