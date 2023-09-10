package saidooubella.and.ino.conn

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothDevice.*
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.FEATURE_BLUETOOTH
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import saidooubella.and.ino.conn.ui.theme.And2InoConnectTheme
import java.io.InputStream
import java.io.OutputStream
import java.util.*

private const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"

@RequiresApi(S)
private val BLUETOOTH_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
)

private val SerialPortProfileUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!packageManager.hasSystemFeature(FEATURE_BLUETOOTH)) {
            Toast.makeText(this, "Bluetooth isn't available", Toast.LENGTH_SHORT).show()
            finish()
        }

        val bluetoothManager = getSystemService<BluetoothManager>() ?: return
        val bluetoothAdapter = bluetoothManager.adapter ?: return

        setContent {

            And2InoConnectTheme {

                var screenState by remember { mutableStateOf<ScreenState>(ScreenState.Loading) }

                val permissionsLauncher =
                    rememberLauncherForActivityResult(RequestMultiplePermissions()) { permissionsResult ->
                        val isGranted = permissionsResult.values.all { granted -> granted }
                        screenState = when (isGranted) {
                            true -> ScreenState.ReadyForConnection
                            else -> ScreenState.Error("Permissions aren't granted")
                        }
                    }

                val resultLauncher =
                    rememberLauncherForActivityResult(StartActivityForResult()) { result ->

                        if (result.resultCode == RESULT_OK && SDK_INT >= S) {
                            permissionsLauncher.launch(BLUETOOTH_PERMISSIONS)
                            return@rememberLauncherForActivityResult
                        }

                        screenState = when (result.resultCode == RESULT_OK) {
                            true -> ScreenState.ReadyForConnection
                            else -> ScreenState.Error("Couldn't turn on Bluetooth")
                        }
                    }

                LaunchedEffect(bluetoothAdapter, resultLauncher) {
                    if (!bluetoothAdapter.isEnabled) {
                        resultLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    } else if (SDK_INT >= S) {
                        permissionsLauncher.launch(BLUETOOTH_PERMISSIONS)
                    } else {
                        screenState = ScreenState.ReadyForConnection
                    }
                }

                val coroutineScope = rememberCoroutineScope()

                when (val state = screenState) {
                    is ScreenState.Error -> ErrorScreen(state)
                    is ScreenState.Loading -> LoadingScreen()
                    is ScreenState.ReadyForConnection -> {
                        ConnectionScreen(bluetoothAdapter) {
                            screenState = ScreenState.Loading
                            coroutineScope.launch {
                                val connection = connectToDevice(it.device) ?: run {
                                    screenState = ScreenState.Error("Couldn't connect to the device")
                                    return@launch
                                }
                                screenState = ScreenState.ReadyForMonitoring(it, connection)
                            }
                        }
                    }
                    is ScreenState.ReadyForMonitoring -> {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "Connected")
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun connectToDevice(device: BluetoothDevice): DeviceConnection? {
    return withContext(Dispatchers.IO) { device.connect() }?.let(::DeviceConnectionImpl)
}

@RequiresPermission(BLUETOOTH_CONNECT)
private fun BluetoothDevice.connect(): BluetoothSocket? = tryOrNull {
    createRfcommSocketToServiceRecord(SerialPortProfileUUID).apply { connect() }
}

private class DeviceConnectionImpl private constructor(
    private val socket: BluetoothSocket,
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
) : DeviceConnection {

    constructor(socket: BluetoothSocket) : this(socket, socket.inputStream, socket.outputStream)

    override suspend fun read(arr: ByteArray, offset: Int, length: Int): Int =
        withContext(Dispatchers.IO) {
            tryOrNull { inputStream.read(arr, offset, length) } ?: -1
        }

    override suspend fun write(arr: ByteArray, offset: Int, length: Int): Boolean =
        withContext(Dispatchers.IO) {
            tryOrNull { outputStream.write(arr, offset, length); true } ?: false
        }

    override fun close() {
        tryOrNull { socket.close() }
    }
}

private interface DeviceConnection {
    suspend fun read(arr: ByteArray, offset: Int, length: Int): Int
    suspend fun write(arr: ByteArray, offset: Int, length: Int): Boolean
    fun close()
}

@Composable
@SuppressLint("MissingPermission")
private fun ConnectionScreen(
    btAdapter: BluetoothAdapter,
    devices: List<BtDevice> = discoverBluetoothDevices(btAdapter),
    onDevice: (BtDevice) -> Unit,
) {
    Column {

        DisposableEffect(btAdapter) {
            btAdapter.startDiscovery()
            onDispose { btAdapter.cancelDiscovery() }
        }

        Box(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Devices Discoverability",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(devices) { device ->
                BtDeviceItem(device) { onDevice(device) }
            }
        }
    }
}

@Composable
private fun BtDeviceItem(device: BtDevice, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            Column(
                modifier = Modifier.weight(1.0f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(device.name)
                Text(device.address)
            }
            Text(device.bondState.displayName)
        }
    }
}

@Composable
@RequiresPermission(BLUETOOTH_CONNECT)
private fun discoverBluetoothDevices(
    btAdapter: BluetoothAdapter,
    context: Context = LocalContext.current
): List<BtDevice> {

    val devices = remember { mutableStateListOf<BtDevice>() }

    DisposableEffect(context) {

        btAdapter.bondedDevices.mapTo(devices) { BtDevice(it) }

        val receiver = object : BroadcastReceiver() {
            @RequiresPermission(BLUETOOTH_CONNECT)
            override fun onReceive(context: Context?, intent: Intent?) {
                devices.add(BtDevice(intent?.parcelable(EXTRA_DEVICE) ?: return))
            }
        }

        context.registerReceiver(receiver, IntentFilter(ACTION_FOUND))
        onDispose { context.unregisterReceiver(receiver) }
    }

    return devices
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorScreen(state: ScreenState.Error) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = state.message, fontSize = 18.sp)
    }
}

private sealed interface ScreenState {

    object Loading : ScreenState

    data class ReadyForMonitoring(
        val btDevice: BtDevice,
        val connection: DeviceConnection,
    ) : ScreenState

    object ReadyForConnection : ScreenState

    data class Error(val message: String) : ScreenState

}

private data class BtDevice(
    val name: String,
    val address: String,
    val bondState: BondState,
    val device: BluetoothDevice
) {

    @RequiresPermission(BLUETOOTH_CONNECT)
    constructor(device: BluetoothDevice) : this(
        device.name,
        device.address,
        BondState(device.bondState),
        device
    )

    enum class BondState(val displayName: String) {

        Bonded("Bounded"),
        Bonding("Bonding"),
        None("");

        companion object {
            operator fun invoke(bondState: Int) = when (bondState) {
                BOND_BONDING -> BtDevice.BondState.Bonding
                BOND_BONDED -> BtDevice.BondState.Bonded
                BOND_NONE -> BtDevice.BondState.None
                else -> error("Illegal state encountered")
            }
        }
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? {
    return when {
        SDK_INT >= TIRAMISU -> getParcelableExtra(key, T::class.java)
        else -> getParcelableExtra(key)
    }
}

private inline fun <T, R> T.tryOrNull(block: T.() -> R): R? {
    return try { block() } catch (_: Exception) { null }
}
