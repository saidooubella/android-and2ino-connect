package saidooubella.and.ino.conn

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresPermission
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.WindowCompat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import saidooubella.and.ino.conn.ui.theme.And2InoConnectTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val bluetoothAdapter = getSystemService<BluetoothManager>()?.adapter

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
                            permissionsLauncher.launch(PERMISSIONS)
                        } else {
                            screenState = when (result.resultCode == RESULT_OK) {
                                true -> ScreenState.ReadyForConnection
                                else -> ScreenState.Error("Couldn't turn on Bluetooth")
                            }
                        }
                    }

                LaunchedEffect(bluetoothAdapter, resultLauncher) {
                    if (bluetoothAdapter == null) {
                        screenState = ScreenState.Error("Bluetooth isn't available")
                    } else if (!bluetoothAdapter.isEnabled) {
                        resultLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    } else if (SDK_INT >= S) {
                        permissionsLauncher.launch(PERMISSIONS)
                    } else {
                        screenState = ScreenState.ReadyForConnection
                    }
                }

                val coroutineScope = rememberCoroutineScope()

                when (val state = screenState) {
                    is ScreenState.Error -> ErrorScreen(state)
                    is ScreenState.Loading -> LoadingScreen()
                    is ScreenState.ReadyForConnection -> {
                        ConnectionScreen(bluetoothAdapter!!) {
                            screenState = ScreenState.Loading
                            coroutineScope.launch {
                                val connection = connectToDevice(it) ?: run {
                                    screenState =
                                        ScreenState.Error("Couldn't connect to the device") {
                                            screenState = ScreenState.ReadyForConnection
                                        }
                                    return@launch
                                }
                                screenState = ScreenState.ReadyForMonitoring(it, connection)
                            }
                        }
                    }

                    is ScreenState.ReadyForMonitoring -> {
                        DisposableEffect(state.connection) {
                            onDispose { state.connection.close() }
                        }
                        MonitoringScreen {
                            coroutineScope.launch { state.connection.it() }
                        }
                    }
                }
            }
        }
    }
}

class Pin(val type: PinType, val pin: Int) {
    override fun toString(): String = "${type.name} $pin"
}

@Composable
private fun MonitoringScreen(action: (suspend DeviceConnection.() -> Unit) -> Unit) {

    val logs = remember { mutableStateListOf<String>() }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {

        item {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .safeDrawingPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {

                var selectedCommand by remember { mutableStateOf<Command>(Command.Change) }
                var commandMenuExpand by remember { mutableStateOf(false) }

                ToggleTitle("Selected command: ${selectedCommand.name}") {
                    commandMenuExpand = !commandMenuExpand
                }

                AnimatedVisibility(visible = commandMenuExpand) {
                    Menu(Command.entries) { command ->
                        MenuItem(text = command.name) {
                            selectedCommand = command
                            commandMenuExpand = false
                        }
                    }
                }

                var pinMenuExpand by remember { mutableStateOf(false) }
                var selectedPin by remember { mutableStateOf<Pin?>(null) }

                ToggleTitle("Selected pin: ${selectedPin ?: "None"}") {
                    pinMenuExpand = !pinMenuExpand
                }

                AnimatedVisibility(visible = pinMenuExpand) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Menu(0..19) { index ->
                            val type = if (index > 13) PinType.Analog else PinType.Digital
                            val pin = index % 14
                            MenuItem("${type.name} $pin") {
                                selectedPin = Pin(type, pin)
                                pinMenuExpand = false
                            }
                        }
                    }
                }

                var selectedValue by remember { mutableStateOf("") }

                AnimatedContent(targetState = selectedCommand, label = "command-input") { command ->
                    when (command) {
                        Command.Change -> {
                            Menu(IOMenuItems) { (title, value) ->
                                MenuItem(title) {
                                    selectedValue = value
                                }
                            }
                        }

                        Command.Write -> {
                            OutlinedTextField(
                                enabled = selectedPin != null && selectedCommand != Command.Read,
                                modifier = Modifier.fillMaxWidth(),
                                value = selectedValue,
                                onValueChange = { selectedValue = it }
                            )
                        }

                        Command.Read -> {}
                    }
                }

                OutlinedButton(
                    modifier = Modifier.align(Alignment.End),
                    enabled = selectedPin != null,
                    onClick = {
                        action {
                            when (selectedCommand) {
                                Command.Change -> {
                                    val value = selectedValue.toIntOrNull() ?: run {
                                        logs += "$selectedValue is invalid"
                                        return@action
                                    }
                                    if (!selectedCommand.isValidValue(selectedPin!!.type, value)) {
                                        logs += "$selectedValue is invalid"
                                        return@action
                                    }
                                    changePin(selectedPin!!.type, selectedPin!!.pin, value)
                                    logs += "${selectedPin!!.type.name} pin ${selectedPin!!.pin} is changed"
                                }

                                Command.Write -> {
                                    val value = selectedValue.toIntOrNull() ?: run {
                                        logs += "$selectedValue is invalid"
                                        return@action
                                    }
                                    if (!selectedCommand.isValidValue(selectedPin!!.type, value)) {
                                        logs += "$selectedValue is invalid"
                                        return@action
                                    }
                                    writePin(selectedPin!!.type, selectedPin!!.pin, value)
                                    logs += "$selectedValue is written into ${selectedPin!!.type.name} pin ${selectedPin!!.pin}"
                                }

                                Command.Read -> {
                                    val value = readPin(selectedPin!!.type, selectedPin!!.pin)
                                    logs += "${selectedPin!!.type.name} pin ${selectedPin!!.pin} has value $value"
                                }
                            }
                        }
                    },
                    content = { Text(text = "Submit") }
                )
            }
        }

        items(logs) {
            Text(text = it)
        }
    }
}

@Composable
private fun <T> Menu(items: Iterable<T>, content: @Composable (T) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { items.forEach { item -> content(item) } },
    )
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        content = { Text(text = text) }
    )
}

@Composable
private fun ToggleTitle(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, Color(0xFF3F51B5), RoundedCornerShape(8.dp))
            .padding(16.dp),
        content = { Text(text = text) },
    )
}

@Composable
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
private fun ConnectionScreen(
    btAdapter: BluetoothAdapter,
    discoveryState: DiscoveryState = discoverBluetoothDevices(btAdapter),
    onDevice: (BTDevice) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

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
            contentPadding = PaddingValues(8.dp) + WindowInsets.navigationBars.asPaddingValues(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(discoveryState.devices) { device ->
                BtDeviceItem(device, onDevice)
            }
        }
    }
}

@Composable
private fun BtDeviceItem(device: BTDevice, onClick: (BTDevice) -> Unit) {
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
                Text(text = device.name, color = MaterialTheme.colorScheme.onBackground)
                Text(text = device.address, color = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = device.bondState.displayName,
                color = MaterialTheme.colorScheme.onBackground
            )
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
                        val device = BTDevice.from(intent) ?: return
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

        ContextCompat.registerReceiver(
            context,
            receiver,
            DISCOVERY_INTENT_FILTER,
            ContextCompat.RECEIVER_EXPORTED
        )
        bluetoothAdapter.startDiscovery()

        onDispose {
            bluetoothAdapter.cancelDiscovery()
            context.unregisterReceiver(receiver)
        }
    }

    return state
}

@Composable
private fun ErrorScreen(error: ScreenState.Error) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = error.message,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (error.action != null) {
            Button(
                modifier = Modifier.padding(top = 16.dp),
                onClick = error.action,
            ) {
                Text(text = "Retry")
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@SuppressLint("MissingPermission")
private suspend fun connectToDevice(device: BTDevice): DeviceConnection? {
    return withContext(Dispatchers.IO) { device.connect() }?.let(::DeviceConnectionImpl)
}

private sealed interface ScreenState {

    data object Loading : ScreenState

    data class ReadyForMonitoring(
        val btDevice: BTDevice,
        val connection: DeviceConnection,
    ) : ScreenState

    data object ReadyForConnection : ScreenState

    data class Error(val message: String, val action: (() -> Unit)? = null) : ScreenState

}
