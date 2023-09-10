package saidooubella.and.ino.conn

import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

interface DeviceConnection {
    suspend fun read(arr: ByteArray, offset: Int, length: Int): Int
    suspend fun write(arr: ByteArray, offset: Int, length: Int): Boolean
    suspend fun write(byte: Int): Boolean
    fun close()
}

class DeviceConnectionImpl private constructor(
    private val socket: BluetoothSocket,
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
) : DeviceConnection {

    constructor(socket: BluetoothSocket) : this(socket, socket.inputStream, socket.outputStream)

    override suspend fun read(arr: ByteArray, offset: Int, length: Int): Int {
        return withContext(Dispatchers.IO) {
            tryOrNull { inputStream.read(arr, offset, length) } ?: -1
        }
    }

    override suspend fun write(arr: ByteArray, offset: Int, length: Int): Boolean {
        return withContext(Dispatchers.IO) {
            tryOrNull { outputStream.write(arr, offset, length); true } ?: false
        }
    }

    override suspend fun write(byte: Int): Boolean {
        return withContext(Dispatchers.IO) {
            tryOrNull { outputStream.write(byte); true } ?: false
        }
    }

    override fun close() {
        tryOrNull { socket.close() }
    }
}

suspend fun DeviceConnection.write(arr: ByteArray) = write(arr, 0, arr.size)
