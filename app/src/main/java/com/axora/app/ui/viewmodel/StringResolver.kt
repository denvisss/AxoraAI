package com.axora.app.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes

fun interface StringResolver {
    fun get(@StringRes resId: Int, vararg args: Any): String
}

class ContextStringResolver(private val context: Context) : StringResolver {
    override fun get(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args)
}