package com.aispend.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.aispend.ui.App

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "AI Spend") {
        App()
    }
}
