package com.checklistboteco.data.remote

import com.checklistboteco.BuildConfig

actual object BackendEnvironment {
    actual val baseUrl: String
        get() = BuildConfig.CHECKLIST_API_BASE_URL
}
