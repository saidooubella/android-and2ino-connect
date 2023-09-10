package saidooubella.and.ino.conn

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.EXTRA_DEVICE
import android.bluetooth.BluetoothSocket
import android.content.Intent
import androidx.annotation.RequiresPermission

class BtDevice private constructor(
    val name: String,
    val address: String,
    val bondState: BondState,
    private val device: BluetoothDevice
) {

    @RequiresPermission(BLUETOOTH_CONNECT)
    constructor(device: BluetoothDevice) : this(
        device.name ?: "<unknown>",
        device.address,
        BondState(device.bondState),
        device
    )

    @RequiresPermission(BLUETOOTH_CONNECT)
    fun connect(): BluetoothSocket? = tryOrNull {
        device.createRfcommSocketToServiceRecord(SerialPortProfileUUID).apply { connect() }
    }

    enum class BondState(val displayName: String) {

        Bonded("Bounded"),
        Bonding("Bonding"),
        None("");

        companion object {
            operator fun invoke(bondState: Int) = when (bondState) {
                BluetoothDevice.BOND_BONDING -> BtDevice.BondState.Bonding
                BluetoothDevice.BOND_BONDED -> BtDevice.BondState.Bonded
                BluetoothDevice.BOND_NONE -> BtDevice.BondState.None
                else -> error("Illegal state encountered")
            }
        }
    }

    companion object {
        @RequiresPermission(BLUETOOTH_CONNECT)
        fun from(intent: Intent?): BtDevice? {
            return intent?.parcelable<BluetoothDevice>(EXTRA_DEVICE)?.let(::BtDevice)
        }
    }
}
