package com.gianlucacelante.midipiano.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BleSupportTest {
    @Test
    fun android12AndLaterNeedScanAndConnect() {
        assertEquals(
            listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"),
            BlePermissions.required(sdkInt = 31),
        )
        assertEquals(BlePermissions.required(31), BlePermissions.required(35))
    }

    @Test
    fun android6To11NeedFineLocationForScanning() {
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"), BlePermissions.required(sdkInt = 30))
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"), BlePermissions.required(sdkInt = 26))
    }

    @Test
    fun midiServiceUuidIsTheOfficialOne() {
        assertEquals("03b80e5a-ede8-4b33-a751-6ce34ec4c700", BleMidi.SERVICE_UUID.toString())
    }

    @Test
    fun deviceListKeepsOneEntryPerAddressAndUpdatesTheName() {
        var list = emptyList<BleMidiDevice>()
        list = BleDeviceList.upsert(list, BleMidiDevice("AA:BB", null))
        list = BleDeviceList.upsert(list, BleMidiDevice("CC:DD", "Tastiera"))
        list = BleDeviceList.upsert(list, BleMidiDevice("AA:BB", "Piano Yamaha"))
        assertEquals(2, list.size)
        assertEquals("Piano Yamaha", list.first { it.address == "AA:BB" }.name)
    }

    @Test
    fun aKnownNameIsNotReplacedByAnUnknownOne() {
        var list = BleDeviceList.upsert(emptyList(), BleMidiDevice("AA:BB", "Piano"))
        list = BleDeviceList.upsert(list, BleMidiDevice("AA:BB", null))
        assertEquals("Piano", list.single().name)
    }

    @Test
    fun displayNameFallsBackToTheAddress() {
        assertEquals("Piano", BleMidiDevice("AA:BB", "Piano").displayName)
        assertTrue(BleMidiDevice("AA:BB", null).displayName.contains("AA:BB"))
    }
}
