package com.example.lcsc_android_erp.core.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class P0BluetoothClient(
    private val adapter: BluetoothAdapter,
    private val onStateChanged: (PrinterConnectionState, String) -> Unit,
    private val onBytesReceived: (ByteArray) -> Unit,
) {
    private val running = AtomicBoolean(false)

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var inputStream: InputStream? = null

    @Volatile
    private var outputStream: OutputStream? = null

    @Volatile
    private var workerThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        disconnect()
        onStateChanged(PrinterConnectionState.CONNECTING, "connecting to $address")
        running.set(true)
        workerThread = thread(name = "p0-spp-reader") {
            try {
                val remoteDevice = adapter.getRemoteDevice(address)
                adapter.cancelDiscovery()
                val bluetoothSocket = remoteDevice.createRfcommSocketToServiceRecord(P0Protocol.sppUuid)
                socket = bluetoothSocket
                bluetoothSocket.connect()
                inputStream = bluetoothSocket.inputStream
                outputStream = bluetoothSocket.outputStream
                onStateChanged(
                    PrinterConnectionState.CONNECTED,
                    "connected to ${remoteDevice.name ?: address}"
                )
                readLoop(bluetoothSocket.inputStream)
            } catch (error: Exception) {
                onStateChanged(
                    PrinterConnectionState.DISCONNECTED,
                    "connect failed: ${error.message ?: error.javaClass.simpleName}"
                )
                disconnect()
            }
        }
    }

    fun disconnect() {
        running.set(false)
        closeQuietly(inputStream)
        closeQuietly(outputStream)
        closeQuietly(socket)
        inputStream = null
        outputStream = null
        socket = null
        if (workerThread != null) {
            onStateChanged(PrinterConnectionState.DISCONNECTED, "connection closed")
        }
        workerThread = null
    }

    @Throws(IOException::class)
    fun send(bytes: ByteArray) {
        val out = outputStream ?: throw IOException("not connected")
        synchronized(this) {
            out.write(bytes)
            out.flush()
        }
    }

    private fun readLoop(stream: InputStream) {
        val buffer = ByteArray(2048)
        while (running.get()) {
            val count = try {
                stream.read(buffer)
            } catch (_: IOException) {
                break
            }
            if (count <= 0) {
                break
            }
            onBytesReceived(buffer.copyOf(count))
        }
        if (running.get()) {
            onStateChanged(PrinterConnectionState.DISCONNECTED, "remote closed")
        }
        disconnect()
    }

    private fun closeQuietly(closeable: AutoCloseable?) {
        try {
            closeable?.close()
        } catch (_: Exception) {
        }
    }
}
