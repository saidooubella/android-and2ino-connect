package saidooubella.and.ino.conn

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.IntentFilter
import android.os.Build
import java.util.*

//const val BLUETOOTH_CONNECT: String = "android.permission.BLUETOOTH_CONNECT"
const val BLUETOOTH_SCAN: String = "android.permission.BLUETOOTH_SCAN"

val PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
} else {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
}

val IOMenuItems = listOf("Input" to "0", "Output" to "1")

val DISCOVERY_INTENT_FILTER: IntentFilter = IntentFilter().apply {
    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
    addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
    addAction(BluetoothDevice.ACTION_FOUND)
}

val SerialPortProfileUUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
