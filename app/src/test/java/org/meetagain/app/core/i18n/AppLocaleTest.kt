package org.meetagain.app.core.i18n

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocaleTest {
    @Test
    fun `a supported language is kept without its region`() {
        assertEquals("de", AppLocale.current(Locale.forLanguageTag("de-AT")))
        assertEquals("zh", AppLocale.current(Locale.forLanguageTag("zh-Hans-CN")))
    }

    @Test
    fun `any other language falls back to English`() {
        assertEquals("en", AppLocale.current(Locale.forLanguageTag("it-IT")))
    }
}
