package com.desikitchen.app

import android.app.Application

class DesiKitchenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        com.desikitchen.app.util.ImportImageStore.init(this)
    }
}
