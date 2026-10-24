package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MileTracker Pro", appName)
  }

  @Test
  fun `verify 15 distinct handcrafted theme presets exist`() {
    val presets = com.example.ui.theme.ThemePresets.ALL_PRESETS
    assertEquals(15, presets.size)

    val expectedIds = listOf(
        "OCEAN", "COBALT", "CYBER", "SUNSET", "FOREST",
        "NORDIC", "SAKURA", "MIDNIGHT", "SOLAR", "LAVENDER",
        "CRIMSON", "DESERT", "PACIFIC", "MINT", "ROYAL"
    )
    assertEquals(expectedIds, presets.map { it.id })

    // Verify lookup by id
    val cobalt = com.example.ui.theme.ThemePresets.getPreset("COBALT")
    assertEquals("Cobalt Indigo", cobalt.name)

    val cyber = com.example.ui.theme.ThemePresets.getPreset("CYBER")
    assertEquals("Cyberpunk Neon", cyber.name)
  }

  @Test
  fun `verify theme persistence and safe zoom bounds`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefsManager = com.example.data.local.PreferencesManager(context)

    prefsManager.setThemePreset("CYBER")
    assertEquals("CYBER", prefsManager.userProfile.value.themePreset)

    // Test extreme zoom values are clamped safely to avoid layout breakage
    prefsManager.setDisplayZoom(fontPoints = 28, zoomPercent = 160)
    val fontScale = prefsManager.userProfile.value.fontScale
    org.junit.Assert.assertTrue("Font scale should be capped safely: $fontScale", fontScale <= 1.55f)
  }
}
