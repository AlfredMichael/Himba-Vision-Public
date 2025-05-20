package ie.tus.himbavision.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ie.tus.himbavision.R


/*
References: Schmetzer, M. (2024) Gotham Font Family Free Download, Free Fonts Family. Available at: https://freefontsfamily.net/gotham-font-family/ (Accessed: 9 September 2024).
 */
val gothamFonts = FontFamily(
    Font(R.font.gothamregular, FontWeight.Normal),
    Font(R.font.gothambold, FontWeight.Bold),
    Font(R.font.gothammedium, FontWeight.Medium),
    Font(R.font.gothamlight, FontWeight.Light),
    Font(R.font.gothamextralight, FontWeight.ExtraLight)
)



// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)