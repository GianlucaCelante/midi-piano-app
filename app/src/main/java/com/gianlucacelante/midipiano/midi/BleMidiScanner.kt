package com.gianlucacelante.midipiano.midi

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat

/** Cerca tastiere BLE MIDI nei dintorni. I callback arrivano sul thread principale. */
class BleMidiScanner(
    private val context: Context,
    private val onDevices: (List<BleMidiDevice>) -> Unit,
    private val onScanning: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var devices = emptyList<BleMidiDevice>()
    private var scanning = false
    private val stopRunnable = Runnable { stop() }

    private val callback = object : ScanCallback() {
        @SuppressLint("MissingPermission") // la scansione parte solo se hasPermissions() è vero
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val d = result.device
            val name = runCatching { d.name }.getOrNull() ?: result.scanRecord?.deviceName
            devices = BleDeviceList.upsert(devices, BleMidiDevice(d.address, name))
            onDevices(devices)
        }

        override fun onScanFailed(errorCode: Int) {
            stop()
            onError("Scansione Bluetooth non riuscita (codice $errorCode).")
        }
    }

    fun hasPermissions(): Boolean = BlePermissions.required(Build.VERSION.SDK_INT).all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission") // controllato da hasPermissions()
    fun start() {
        if (scanning) return
        val a = adapter
        when {
            a == null -> return onError("Questo dispositivo non ha il Bluetooth.")
            !a.isEnabled -> return onError("Attiva il Bluetooth per cercare la tastiera.")
            !hasPermissions() -> return onError("Mancano i permessi Bluetooth.")
        }
        val scanner = a?.bluetoothLeScanner ?: return onError("Scanner Bluetooth non disponibile.")
        devices = emptyList()
        onDevices(devices)
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(BleMidi.SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, callback)
        scanning = true
        onScanning(true)
        handler.postDelayed(stopRunnable, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        handler.removeCallbacks(stopRunnable)
        if (!scanning) return
        scanning = false
        if (hasPermissions()) runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
        onScanning(false)
    }

    /** Dispositivo di sistema per l'indirizzo trovato in scansione (serve a MidiInput per connettersi). */
    fun remoteDevice(address: String) = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()

    private companion object {
        const val SCAN_TIMEOUT_MS = 15_000L
    }
}
