package com.jaynak.app

import android.app.Application
import com.jaynak.app.core.Boot

class JaynakApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Boot.app = this
    }
}
