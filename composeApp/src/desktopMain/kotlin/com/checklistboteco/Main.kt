package com.checklistboteco

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.checklistboteco.data.database.DesktopDatabaseDriverFactory

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Checklist Boteco",
        state = rememberWindowState(width = 400.dp, height = 700.dp)
    ) {
        App(databaseDriverFactory = DesktopDatabaseDriverFactory())
    }
}
