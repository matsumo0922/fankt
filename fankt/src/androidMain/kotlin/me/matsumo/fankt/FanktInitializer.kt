package me.matsumo.fankt

import android.content.Context
import androidx.startup.Initializer

internal lateinit var applicationContext: Context
    private set

internal object FanktContext

internal class FanktInitializer: Initializer<FanktContext> {
    override fun create(context: Context): FanktContext {
        applicationContext = context.applicationContext
        return FanktContext
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}