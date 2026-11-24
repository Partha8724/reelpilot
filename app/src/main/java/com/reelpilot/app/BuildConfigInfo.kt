package com.reelpilot.app

import android.content.Context

object BuildConfigInfo {
    fun version(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) { "1.0" }
}
