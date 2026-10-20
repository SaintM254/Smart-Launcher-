package com.saintm.smartlauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.saintm.smartlauncher.R

private val InterFamily = FontFamily(Font(R.font.inter, FontWeight.Normal))
private val PoppinsFamily = FontFamily(Font(R.font.poppins, FontWeight.Normal))
private val MontserratFamily = FontFamily(Font(R.font.montserrat, FontWeight.Normal))
private val PlayfairFamily = FontFamily(Font(R.font.playfair_display, FontWeight.Normal))
private val LoraFamily = FontFamily(Font(R.font.lora, FontWeight.Normal))

@Composable
fun SmartTheme(content: @Composable () -> Unit) {
    androidx.tv.material3.MaterialTheme(content = content)
}

fun fontFamilyFor(key: String): FontFamily = when (key) {
    "inter" -> InterFamily
    "poppins" -> PoppinsFamily
    "montserrat" -> MontserratFamily
    "playfair" -> PlayfairFamily
    "lora" -> LoraFamily
    else -> FontFamily.Default
}

fun fontDisplayName(key: String): String = when (key) {
    "inter" -> "Inter"
    "poppins" -> "Poppins"
    "montserrat" -> "Montserrat"
    "playfair" -> "Playfair Display"
    "lora" -> "Lora"
    else -> "System font"
}
