package com.softwareidiot.soundsentinel

import android.app.Application
import com.softwareidiot.soundsentinel.data.repository.CctvRepository

class SoundSentinelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CctvRepository.getInstance(this)
    }
}
