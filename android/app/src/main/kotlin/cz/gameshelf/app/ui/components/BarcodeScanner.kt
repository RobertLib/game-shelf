package cz.gameshelf.app.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

private const val TAG = "BarcodeScanner"

/**
 * Google's code scanner from Play services: a full-screen camera view that needs no camera permission
 * and also lets the user type a damaged code by hand. Returns a function that opens it; [onScanned]
 * receives the digits of the code, [onUnavailable] is called when the scanner can't start (no Play
 * services, scanner module still downloading). Closing the scanner calls neither.
 */
@Composable
fun rememberBarcodeScanner(
    onScanned: (String) -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    return remember(context) {
        val scanner = GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                )
                .enableAutoZoom()
                .allowManualInput()
                .build(),
        )
        val scan: () -> Unit = {
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    barcode.rawValue?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }?.let(currentOnScanned)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Barcode scanner failed", e)
                    currentOnUnavailable()
                }
        }
        scan
    }
}
