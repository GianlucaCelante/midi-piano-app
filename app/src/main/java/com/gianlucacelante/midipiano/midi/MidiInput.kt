package com.gianlucacelante.midipiano.midi

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Ascolta tutte le porte di uscita MIDI dei dispositivi collegati (USB class-compliant e
 * dispositivi già accoppiati dal sistema). Gli eventi arrivano su un thread MIDI di sistema.
 */
class MidiInput(
    context: Context,
    private val onEvent: (MidiEvent) -> Unit,
    private val onDevicesChanged: (List<String>) -> Unit,
) {
    private val manager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager?
    private val handler = Handler(Looper.getMainLooper())
    private val open = HashMap<Int, OpenDevice>()
    private val pending = HashSet<Int>() // aperture in corso: evita di aprire due volte lo stesso dispositivo
    private var stopped = false

    private class OpenDevice(val name: String, val device: MidiDevice, val ports: List<MidiOutputPort>)

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) = openDevice(device)
        override fun onDeviceRemoved(device: MidiDeviceInfo) = closeDevice(device.id)
    }

    val isSupported: Boolean get() = manager != null

    fun start() {
        val m = manager ?: return
        stopped = false
        m.registerDeviceCallback(callback, handler)
        @Suppress("DEPRECATION")
        m.devices.forEach(::openDevice)
    }

    fun stop() {
        stopped = true // le aperture ancora in corso si chiuderanno da sole in attach()
        pending.clear()
        manager?.unregisterDeviceCallback(callback)
        open.keys.toList().forEach(::closeDevice)
    }

    private fun openDevice(info: MidiDeviceInfo) {
        if (info.outputPortCount == 0 || open.containsKey(info.id) || !pending.add(info.id)) return
        manager?.openDevice(info, { device ->
            pending.remove(info.id)
            attach(info, device)
        }, handler)
    }

    /**
     * Collega una tastiera Bluetooth LE MIDI già trovata in scansione. Una volta connessa compare
     * anche tra i dispositivi di sistema: [attach] evita di aprirla due volte.
     */
    fun connectBluetooth(device: BluetoothDevice, onResult: (Boolean) -> Unit) {
        val m = manager ?: return onResult(false)
        m.openBluetoothDevice(device, { midiDevice ->
            if (midiDevice == null) {
                onResult(false)
            } else {
                attach(midiDevice.info, midiDevice)
                onResult(true)
            }
        }, handler)
    }

    private fun attach(info: MidiDeviceInfo, device: MidiDevice?) {
        if (device == null) {
            Log.w(TAG, "Impossibile aprire il dispositivo MIDI ${info.id}")
            return
        }
        if (stopped || open.containsKey(info.id)) { // fermato nel frattempo, o già collegato da un'altra strada
            runCatching { device.close() }
            return
        }
        val ports = (0 until info.outputPortCount).mapNotNull { index ->
            device.openOutputPort(index)?.also { port ->
                val parser = MidiParser()
                port.connect(object : MidiReceiver() {
                    override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                        parser.parse(msg, offset, count).forEach(onEvent)
                    }
                })
            }
        }
        if (ports.isEmpty()) { // porta occupata da un'altra app o apertura fallita: non risultare "collegato"
            Log.w(TAG, "Nessuna porta MIDI utilizzabile su ${info.id}")
            runCatching { device.close() }
            return
        }
        val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Dispositivo MIDI ${info.id}"
        open[info.id] = OpenDevice(name, device, ports)
        publish()
    }

    private fun closeDevice(id: Int) {
        val d = open.remove(id) ?: return
        d.ports.forEach { runCatching { it.close() } }
        runCatching { d.device.close() }
        publish()
    }

    private fun publish() = onDevicesChanged(open.values.map { it.name }.sorted())

    private companion object {
        const val TAG = "MidiInput"
    }
}
