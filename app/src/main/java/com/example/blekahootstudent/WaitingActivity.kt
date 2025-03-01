package com.example.blekahootstudent

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class WaitingActivity : AppCompatActivity() {

    private val TAG = "WaitingActivity"

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false
    private var advertiser: BluetoothLeAdvertiser? = null
    @SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        // No llamamos a super => se desactiva el botón atrás
        // Puedes mostrar un Toast si deseas notificar
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_waiting)

        // BLE
        val manager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = manager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        startScanForNewRound()

        startAdvertisingAckStartIndefinitely()
    }

    private fun startScanForNewRound() {
        if (isScanning) return
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Activa Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "No se puede escanear BLE", Toast.LENGTH_SHORT).show()
            return
        }

        val filter = ScanFilter.Builder()
            .setManufacturerData(0x1234, byteArrayOf())
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
        isScanning = true
        Log.d(TAG, "Escaneo para NEWROUND iniciado")
    }

    private fun stopScan() {
        if (!isScanning) return
        bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
        Log.d(TAG, "Escaneo detenido")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(cbType: Int, result: ScanResult) {
            super.onScanResult(cbType, result)
            handleScanResult(result)
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            results.forEach { handleScanResult(it) }
        }
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Escaneo falló: $errorCode")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val data = record.getManufacturerSpecificData(0x1234) ?: return
        val dataString = String(data)
        Log.d(TAG, "Recibido: $dataString")

        if (dataString.startsWith("NEWROUND")) {
            stopAdvertisingAckStart()
            stopScan() // si escaneas aquí
            runOnUiThread {
                startActivity(Intent(this, AnswersActivity::class.java))
                finish()
            }
        }
    }
    private var isAdvertising = false
    private var isAdvertisingAckStart = false

    private fun startAdvertisingAckStartIndefinitely() {
        if (isAdvertisingAckStart) return // evita doble inicio

        stopAdvertisingIfNeeded()

        val dataString = "ACK_START"
        val dataToSend = dataString.toByteArray()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addManufacturerData(0x1234, dataToSend)
            .setIncludeDeviceName(false)
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        isAdvertising = true
        isAdvertisingAckStart = true

        Log.d(TAG, "Iniciando anuncio indefinido de ACK_START en WaitingActivity")
    }

    private fun stopAdvertisingIfNeeded() {
        if (isAdvertising) {
            advertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.d(TAG, "Advertising detenido")
        }
    }

    private fun stopAdvertisingAckStart() {
        if (!isAdvertisingAckStart) return
        stopAdvertisingIfNeeded()
        isAdvertisingAckStart = false
        Log.d(TAG, "Publicidad ACK_START detenida")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertising ACK_START iniciado correctamente")
        }
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "Error al iniciar Advertising: $errorCode")
        }
    }

    private fun goToAnswers() {
        startActivity(Intent(this, AnswersActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
        stopAdvertisingAckStart() // Detiene el ACK_START si sigue activo
    }
}