package com.wheelhouse.app.data

/**
 * Where the mock API server (api/mock-server/) is reachable. Defaults to what a
 * physical device reaches after `adb reverse tcp:8787 tcp:8787` — that's this app's
 * actual dev setup this session. Swap to "http://10.0.2.2:8787" for the emulator, or a
 * LAN IP for a device over Wi-Fi instead of USB. See api/mock-server/README.md.
 */
object ApiConfig {
    const val BASE_URL = "http://127.0.0.1:8787"
}
