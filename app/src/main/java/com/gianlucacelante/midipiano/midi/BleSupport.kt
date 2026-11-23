package com.gianlucacelante.midipiano.midi

import java.util.UUID

object BleMidi {
    /** Servizio BLE MIDI standard (MMA/Apple). */
    val SERVICE_UUID: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
}

/** Permessi runtime necessari per cercare e collegare dispositivi BLE, in base alla versione di Android. */
object BlePermissions {
    fun required(sdkInt: Int): List<String> =
        if (sdkInt >= 31) {
            listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT")
        } else {
            listOf("android.permission.ACCESS_FINE_LOCATION") // fino ad Android 11 la scansione BLE richiede la posizione
        }
}

data class BleMidiDevice(val address: String, val name: String?) {
    val displayName: String get() = name ?: "Dispositivo BLE ($address)"
}

object BleDeviceList {
    /** Un solo elemento per indirizzo; un nome noto non viene sostituito da uno sconosciuto. */
    fun upsert(list: List<BleMidiDevice>, device: BleMidiDevice): List<BleMidiDevice> {
        val existing = list.firstOrNull { it.address == device.address }
            ?: return list + device
        val merged = existing.copy(name = device.name ?: existing.name)
        return list.map { if (it.address == device.address) merged else it }
    }
}
