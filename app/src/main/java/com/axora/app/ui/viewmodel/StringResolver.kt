package com.axora.app.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import com.axora.app.data.repository.SettingsRepository

fun interface StringResolver {
    fun get(@StringRes resId: Int, vararg args: Any): String
}

class ContextStringResolver(private val context: Context) : StringResolver {
    override fun get(resId: Int, vararg args: Any): String {
        val localized = SettingsRepository.wrapLocale(context)
        return if (args.isEmpty()) localized.getString(resId) else localized.getString(resId, *args)
    }
}