package org.advgnd.atrium

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
expect fun AtriumTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
)
