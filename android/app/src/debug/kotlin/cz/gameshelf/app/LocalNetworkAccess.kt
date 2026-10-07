package cz.gameshelf.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Debug builds talk to a development API on the local network (the emulator host 10.0.2.2), which
 * Android 17+ only allows with the runtime permission ACCESS_LOCAL_NETWORK. Release builds use a
 * public API and ship a no-op variant of this function.
 */
internal fun ComponentActivity.requestLocalNetworkAccessIfNeeded() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return
    val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
    if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return
    registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(permission)
}
