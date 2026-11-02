package br.com.condominio.cobranca.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Visual do aplicativo. Para trocar a cor principal, mude só os valores abaixo.
 * Todas as telas usam estas cores através do MaterialTheme.
 */
private val Cores = lightColorScheme(
    primary = Color(0xFF0F6B63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F0EC),
    onPrimaryContainer = Color(0xFF08403B),
    secondary = Color(0xFF4A635F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE8E5),
    onSecondaryContainer = Color(0xFF18302C),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE9CC),
    onTertiaryContainer = Color(0xFF5A2A00),
    background = Color(0xFFF4F8F7),
    onBackground = Color(0xFF18201F),
    surface = Color.White,
    onSurface = Color(0xFF18201F),
    surfaceVariant = Color(0xFFE3EBE9),
    onSurfaceVariant = Color(0xFF4B5856),
    outline = Color(0xFF8A9795),
    outlineVariant = Color(0xFFD0DAD8),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val Tipografia = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun TemaCobranca(conteudo: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Cores,
        typography = Tipografia,
        shapes = Formas,
        content = conteudo,
    )
}
