package me.matsumo.fankt.fanbox.guest

import app.cash.zipline.Zipline

@OptIn(ExperimentalJsExport::class)
@JsExport
fun launchZipline() {
    Zipline.get().bind<FanboxGuestService>(
        name = FANBOX_GUEST_SERVICE_NAME,
        instance = RealFanboxGuestService(),
    )
}
