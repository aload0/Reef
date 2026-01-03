package dev.pranav.reef.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import dev.pranav.reef.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

object Typography {


    val DMSerif = FontFamily(
        Font(googleFont = GoogleFont("DM Serif Text"), fontProvider = provider)
    )
}
