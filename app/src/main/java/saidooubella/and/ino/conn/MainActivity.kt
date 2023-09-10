package saidooubella.and.ino.conn

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.FEATURE_BLUETOOTH
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import saidooubella.and.ino.conn.ui.theme.And2InoConnectTheme
import java.util.*

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
                        } else {
                            screenState = when (result.resultCode == RESULT_OK) {
                                true -> ScreenState.ReadyForConnection
                                else -> ScreenState.Error("Couldn't turn on Bluetooth")
                            }
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
                    is ScreenState.Error -> ErrorScreen(state.message)
                    is ScreenState.Loading -> LoadingScreen()
                    is ScreenState.ReadyForConnection -> {
                        ConnectionScreen(bluetoothAdapter) {
                            screenState = ScreenState.Loading
                            coroutineScope.launch {
                                val connection = connectToDevice(it) ?: run {
                                    screenState =
                                        ScreenState.Error("Couldn't connect to the device")
                                    return@launch
                                }
                                screenState = ScreenState.ReadyForMonitoring(it, connection)
                            }
                        }
                    }
                    is ScreenState.ReadyForMonitoring -> {
                        DisposableEffect(state.connection) {
                            onDispose {
                                state.connection.close()
                                screenState = ScreenState.ReadyForConnection
                            }
                        }
                        MonitoringScreen {
                            coroutineScope.launch {
                                state.connection.write(it.toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MonitoringScreen(onSend: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(modifier = Modifier.align(Alignment.Center), text = "Connected")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            var input by remember { mutableStateOf("") }
            OutlinedTextField(
                modifier = Modifier
                    .height(56.dp)
                    .weight(1.0F),
                value = input,
                onValueChange = { input = it },
                shape = CircleShape,
                trailingIcon = {
                    Surface(
                        color = Color.Transparent,
                        onClick = { onSend(input); input = "" },
                        shape = CircleShape,
                    ) {
                        Icon(
                            modifier = Modifier.padding(16.dp),
                            imageVector = Icons.Outlined.Send,
                            contentDescription = "Send"
                        )
                    }
                }
            )
        }
    }
}

@Composable
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
private fun ConnectionScreen(
    btAdapter: BluetoothAdapter,
    discoveryState: DiscoveryState = discoverBluetoothDevices(btAdapter),
    onDevice: (BtDevice) -> Unit,
) {
    Column {

        CenterAlignedTopAppBar(
            modifier = Modifier.fillMaxWidth(),
            title = {
                Text(
                    text = "Devices Discoverability",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            actions = {
                Surface(
                    color = Color.Transparent,
                    onClick = {
                        when (discoveryState.isDiscovering) {
                            true -> btAdapter.cancelDiscovery()
                            else -> btAdapter.startDiscovery()
                        }
                    },
                    shape = CircleShape,
                ) {
                    val (icon, description) = when (discoveryState.isDiscovering) {
                        true -> Icons.Outlined.Close to "Stop discovery"
                        else -> Icons.Outlined.Radar to "Start Discovery"
                    }
                    Icon(imageVector = icon, contentDescription = description)
                }
            }
        )

        if (discoveryState.isDiscovering) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(discoveryState.devices) { device ->
                BtDeviceItem(device, onDevice)
            }
        }
    }
}

@Composable
private fun BtDeviceItem(device: BtDevice, onClick: (BtDevice) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(device) }
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
@RequiresPermission(allOf = [BLUETOOTH_CONNECT, BLUETOOTH_SCAN])
private fun discoverBluetoothDevices(
    bluetoothAdapter: BluetoothAdapter,
    context: Context = LocalContext.current
): DiscoveryState {

    var state by remember { mutableStateOf(DiscoveryState()) }

    DisposableEffect(bluetoothAdapter, context) {

        val receiver = object : BroadcastReceiver() {
            @RequiresPermission(BLUETOOTH_CONNECT)
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = BtDevice.from(intent) ?: return
                        state = state.copy(devices = state.devices.add(device))
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        state = state.copy(devices = persistentListOf(), isDiscovering = true)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        state = state.copy(isDiscovering = false)
                    }
                }
            }
        }

        context.registerReceiver(receiver, DISCOVERY_INTENT_FILTER)
        bluetoothAdapter.startDiscovery()

        onDispose {
            bluetoothAdapter.cancelDiscovery()
            context.unregisterReceiver(receiver)
        }
    }

    return state
}

private data class DiscoveryState(
    val devices: PersistentList<BtDevice> = persistentListOf(),
    val isDiscovering: Boolean = false,
)

@Composable
private fun ErrorScreen(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, fontSize = 18.sp)
    }
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

@SuppressLint("MissingPermission")
private suspend fun connectToDevice(device: BtDevice): DeviceConnection? {
    return withContext(Dispatchers.IO) { device.connect() }?.let(::DeviceConnectionImpl)
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
