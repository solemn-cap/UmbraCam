package com.umbracam.app

import android.app.Application
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One process-lifetime listener; does not change or override the device's thermal protection. */
class CaptureThermals(app: Application) {
    private val power = app.getSystemService(PowerManager::class.java)
    private val mutableStatus = MutableStateFlow<Int?>(null)
    val status = mutableStatus.asStateFlow()
    private var registered = false
    private val listener = PowerManager.OnThermalStatusChangedListener { mutableStatus.value = it }
    init {
        try { power.addThermalStatusListener(app.mainExecutor, listener); registered = true }
        catch (_: RuntimeException) { }
        refresh()
    }
    fun refresh(): Int? {
        val value = if (registered) try { power.currentThermalStatus } catch (_: RuntimeException) { null } else null
        mutableStatus.value = value
        return value
    }
}
