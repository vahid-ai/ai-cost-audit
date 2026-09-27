package com.aispend.platform

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

actual fun platformSettings(): Settings = PreferencesSettings(Preferences.userRoot())
