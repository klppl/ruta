package com.ruta.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Heading("ruta")
            Body(
                "A clean, minimal social browser. Wraps social websites in an isolated WebView " +
                    "shell with built-in ad/tracker blocking, multi-account profiles and in-feed " +
                    "media download.",
            )

            Heading("Filter lists")
            Body(
                "Network and cosmetic blocking use EasyList and EasyPrivacy, downloaded at runtime " +
                    "and cached locally. These lists are community-maintained data assets, dual " +
                    "licensed under GPLv3 and CC BY-SA 3.0.",
            )
            Body("• EasyList — https://easylist.to")
            Body("• EasyPrivacy — https://easylist.to")

            Heading("Open source")
            Body(
                "Built with Jetpack Compose, Material 3, AndroidX WebKit, Hilt, OkHttp and " +
                    "kotlinx.serialization. The filter-list parser and content scripts are an " +
                    "original clean-room implementation.",
            )

            Heading("Privacy")
            Body(
                "No analytics, no accounts, no cloud sync. Cookies and storage are isolated per " +
                    "profile and excluded from device backups.",
            )
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun Body(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
}
