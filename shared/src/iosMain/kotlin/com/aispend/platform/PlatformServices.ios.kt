package com.aispend.platform

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

actual fun platformSettings(): Settings = NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
