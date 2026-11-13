package app.itivio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.itivio.ui.common.readableLineText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineContrastTest {
    @Test
    fun `mid-tone lines use black instead of unreadable white`() {
        assertEquals(Color.Black, readableLineText(Color(0xFFEB7405), null))
    }

    @Test
    fun `readable feed colors are kept and unreadable ones are replaced`() {
        assertEquals(Color.White, readableLineText(Color(0xFF333333), Color.White))
        assertEquals(Color.Black, readableLineText(Color.Yellow, Color.White))
        assertEquals(Color.White, readableLineText(Color.Black, Color.Black))
    }

    @Test
    fun `fallback text meets normal text contrast across the color cube`() {
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val background = Color(r, g, b)
            val foreground = readableLineText(background, null)
            val ratio = (maxOf(background.luminance(), foreground.luminance()) + 0.05f) /
                (minOf(background.luminance(), foreground.luminance()) + 0.05f)
            assertTrue("Contrast $ratio for $background", ratio >= 4.5f)
        }
    }
}
