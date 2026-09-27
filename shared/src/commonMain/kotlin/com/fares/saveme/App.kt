package com.fares.saveme

import androidx.compose.runtime.Composable
import com.fares.saveme.designsystem.theme.AppTheme
import com.fares.saveme.navigation.AppNavHost

@Composable
fun App() {
    AppTheme {
        AppNavHost()
    }
}
