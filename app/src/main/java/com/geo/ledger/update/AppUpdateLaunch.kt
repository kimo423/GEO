package com.geo.ledger.update

object AppUpdateLaunch {
    fun open(url: String, startActivity: () -> Unit) {
        if (!AppUpdatePolicy.isAllowedApkUrl(url)) return
        try {
            startActivity()
        } catch (_: RuntimeException) {
        }
    }
}
