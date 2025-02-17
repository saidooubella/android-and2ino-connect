package saidooubella.and.ino.conn

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.EXTRA_DEVICE
import android.bluetooth.BluetoothSocket
import android.content.Intent
import androidx.annotation.RequiresPermission

class BTDevice private constructor(
    val name: String,
    val address: String,
    val bondState: BondState,
    private val device: BluetoothDevice
) {

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    constructor(device: BluetoothDevice) : this(
        device.name ?: "<unknown>",
        device.address,
        BondState(device.bondState),
        device
    )

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(): BluetoothSocket? = tryOrNull {
        device.createRfcommSocketToServiceRecord(SerialPortProfileUUID)?.apply { connect() }
    }

    enum class BondState(val displayName: String) {

        Bonded("Bounded"),
        Bonding("Bonding"),
        None("");

        companion object {
            operator fun invoke(bondState: Int) = when (bondState) {
                BluetoothDevice.BOND_BONDING -> BTDevice.BondState.Bonding
                BluetoothDevice.BOND_BONDED -> BTDevice.BondState.Bonded
                BluetoothDevice.BOND_NONE -> BTDevice.BondState.None
                else -> error("Illegal state encountered")
            }
        }
    }

    companion object {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        fun from(intent: Intent?): BTDevice? {
            return intent?.parcelable<BluetoothDevice>(EXTRA_DEVICE)?.let(::BTDevice)
        }
    }
}
