package saidooubella.and.ino.conn

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

data class DiscoveryState(
    val devices: PersistentList<BTDevice> = persistentListOf(),
    val isDiscovering: Boolean = false,
)
