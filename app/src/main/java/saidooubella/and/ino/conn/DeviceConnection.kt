package saidooubella.and.ino.conn

import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

interface DeviceConnection {
    suspend fun readPin(type: PinType, pin: Int): Int
    suspend fun writePin(type: PinType, pin: Int, value: Int): Int
    suspend fun changePin(type: PinType, pin: Int, value: Int): Int
    fun close()
}

private const val TYPE_ANALOG = 0b0
private const val TYPE_DIGITAL = 0b1

private const val MODE_CHANGE = 0b00
private const val MODE_READ = 0b01
private const val MODE_WRITE = 0b10

sealed class Command(val id: Int, val name: String) {

    abstract fun isValidValue(pin: PinType, value: Int): Boolean

    data object Change : Command(MODE_CHANGE, "Change") {
        override fun isValidValue(pin: PinType, value: Int): Boolean {
            return value in 0..1
        }
    }

    data object Write : Command(MODE_WRITE, "Write") {
        override fun isValidValue(pin: PinType, value: Int): Boolean {
            return when (pin) {
                PinType.Digital -> value in 0..1
                PinType.Analog -> value in 0..1023
            }
        }
    }

    data object Read : Command(MODE_READ, "Read") {
        override fun isValidValue(pin: PinType, value: Int): Boolean {
            return value == 0
        }
    }

    companion object {
        val entries by lazy(LazyThreadSafetyMode.NONE) { listOf(Change, Write, Read) }
    }
}

enum class PinType(val id: Int) {

    Digital(TYPE_DIGITAL) {
        override fun isValidPin(pin: Int) = pin in 0..13
    },

    Analog(TYPE_ANALOG) {
        override fun isValidPin(pin: Int) = pin in 0..6
    };

    abstract fun isValidPin(pin: Int): Boolean
}

// M - command: change(00)/read(01)/write(10)
// T - type: analog(0)/digital(1)
// P - pin:  analog(000..111)/digital(0000..1111)
// D - data payload:
//       - if M == change: analog & digital (0..1)
//       - if M == write:  analog(0000000000..1111111111)/digital(0..1)
//       - if M == read:   unavailable

class DeviceConnectionImpl private constructor(
    private val socket: BluetoothSocket,
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
) : DeviceConnection {

    constructor(socket: BluetoothSocket) : this(socket, socket.inputStream, socket.outputStream)

    override suspend fun readPin(type: PinType, pin: Int): Int {
        val command = Command.Read
        check(type.isValidPin(pin))
        return withContext(Dispatchers.IO) {
            when (type) {
                PinType.Digital -> {
                    val payload = (command.id shl 6) or (type.id shl 5) or (pin shl 1)
                    outputStream.write(payload)
                    inputStream.read()
                }

                PinType.Analog -> {
                    val payload = (command.id shl 6) or (type.id shl 5) or (pin shl 2)
                    outputStream.write(payload)
                    val high = inputStream.read()
                    val low = inputStream.read()
                    (high shl 8) or low
                }
            }
        }
    }

    override suspend fun writePin(type: PinType, pin: Int, value: Int): Int {
        val command = Command.Write
        check(command.isValidValue(type, value))
        check(type.isValidPin(pin))
        return withContext(Dispatchers.IO) {
            when (type) {
                PinType.Digital -> {
                    val payload = (command.id shl 6) or (type.id shl 5) or (pin shl 1) or value
                    outputStream.write(payload)
                    outputStream.flush()
                    inputStream.read()
                }
                PinType.Analog -> {
                    val payload1 = (command.id shl 6) or
                            (type.id shl 5) or
                            (pin shl 2) or
                            (value ushr 8 and 3)
                    val payload2 = value and 0xFF
                    outputStream.write(payload1)
                    outputStream.write(payload2)
                    outputStream.flush()
                    inputStream.read()
                }
            }
        }
    }

    override suspend fun changePin(type: PinType, pin: Int, value: Int): Int {
        val command = Command.Change
        check(command.isValidValue(type, value))
        check(type.isValidPin(pin))
        return withContext(Dispatchers.IO) {
            when (type) {
                PinType.Digital -> {
                    val payload = (command.id shl 6) or (type.id shl 5) or (pin shl 1) or value
                    outputStream.write(payload)
                    outputStream.flush()
                    inputStream.read()
                }
                PinType.Analog -> {
                    val payload = (command.id shl 6) or (type.id shl 5) or (pin shl 2) or value
                    outputStream.write(payload)
                    outputStream.flush()
                    inputStream.read()
                }
            }
        }
    }

    override fun close() {
        tryOrNull { socket.close() }
    }
}
