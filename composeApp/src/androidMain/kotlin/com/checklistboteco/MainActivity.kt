package com.checklistboteco

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.checklistboteco.data.database.AndroidDatabaseDriverFactory
import com.checklistboteco.platform.DeviceIdentity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceIdentity.initialize(this)
        enableEdgeToEdge()
        setContent {
            App(databaseDriverFactory = AndroidDatabaseDriverFactory(applicationContext))
        }
    }
}
