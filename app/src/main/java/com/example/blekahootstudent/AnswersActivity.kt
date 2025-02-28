
package com.example.blekahootstudent

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AnswersActivity : AppCompatActivity() {
    private var hasResponded = false
    private val ackResAdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertising ACKRES iniciado correctamente")
        }
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "Error al iniciar Advertising ACKRES: $errorCode")
        }
    }
    private val TAG = "AnswersActivity"

    // BLE
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false

    // Asignado en CONF. Lo leeremos de SharedPreferences
    private var assignedCode: String? = null

    private lateinit var btnA: Button
    private lateinit var btnB: Button
    private lateinit var btnC: Button
    private lateinit var btnD: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_answers)

        // Recuperar assignedCode de SharedPreferences
        val prefs = getSharedPreferences("BLE_Kahoot_Student", MODE_PRIVATE)
        assignedCode = prefs.getString("assigned_code", null)
        Log.d(TAG, assignedCode.toString())
        // Referencias UI
        btnA = findViewById(R.id.btnA)
        btnB = findViewById(R.id.btnB)
        btnC = findViewById(R.id.btnC)
        btnD = findViewById(R.id.btnD)

        // BLE
        val manager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = manager.adapter
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        // Listeners
        btnA.setOnClickListener { sendResponse("A") }
        btnB.setOnClickListener { sendResponse("B") }
        btnC.setOnClickListener { sendResponse("C") }
        btnD.setOnClickListener { sendResponse("D") }

        // Empezar a escanear para "CONFRES"
        startScanForConfirm()
    }

    /**
     * Enviar "RESP:<code>:<answer>"
     */
    private fun sendResponse(answer: String) {
        if (assignedCode == null) {

            Toast.makeText(this, "No tienes code asignado " + assignedCode.toString(), Toast.LENGTH_SHORT).show()
            Log.d(TAG, assignedCode.toString())
            return
        }

        val code = assignedCode!!
        val dataString = "RESP:$code:$answer"
        val dataToSend = dataString.toByteArray()

        stopAdvertisingIfNeeded()

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

        // Detener en 2s
        Handler(Looper.getMainLooper()).postDelayed({
            stopAdvertisingIfNeeded()
        }, 2000)

        disableButtons()
        hasResponded = true // <-- Agrega esta línea

        Toast.makeText(this, "Respuesta enviada: $answer", Toast.LENGTH_SHORT).show()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertising RESP iniciado")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "Error advertising RESP: $errorCode")
        }
    }

    private fun disableButtons() {
        btnA.isEnabled = false
        btnB.isEnabled = false
        btnC.isEnabled = false
        btnD.isEnabled = false
    }

    private fun stopAdvertisingIfNeeded() {
        if (isAdvertising) {
            advertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.d(TAG, "Advertising detenido")
        }
    }

    // ----------------------------------------
    // Escanear para "CONFRES:<code>"
    // ----------------------------------------
    private fun startScanForConfirm() {
        if (isScanning) return
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Activa Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "BLE Scanner no disponible", Toast.LENGTH_SHORT).show()
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
        Log.d(TAG, "Escaneo para CONFRES iniciado")
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
    private var isAdvertisingAckRes = false

    private fun startAckResAdvertisingIndefinitely(code: String) {
        if (isAdvertisingAckRes) return

        stopAdvertisingIfNeeded()  // si tienes un genérico

        val dataString = "ACKRES:$code"
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

        advertiser?.startAdvertising(settings, data, ackResAdvertiseCallback)
        isAdvertising = true
        isAdvertisingAckRes = true

        Log.d(TAG, "Estudiante: Anunciando ACKRES:$code indefinidamente")
    }

    // Y al destruir la Activity, o si llega otra señal, lo detienes:
    private fun stopAckResAdvertising() {
        if (!isAdvertisingAckRes) return
        stopAdvertisingIfNeeded()
        isAdvertisingAckRes = false
        Log.d(TAG, "Estudiante: Publicidad ACKRES detenida")
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val data = record.getManufacturerSpecificData(0x1234) ?: return
        val dataString = String(data)

        if (dataString.startsWith("CONFRES:")) {
            val parts = dataString.split(":")
            if (parts.size == 2) {
                val code = parts[1]
                // Si code == assignedCode (o tu lógica), inicia la publicidad "ACKRES:code"
                if (code == assignedCode) {
                    startAckResAdvertisingIndefinitely(code)
                    goToWaitResults()
                }
            }
        }
        else if (dataString.startsWith("ENDROUND")) {

            // Si el estudiante no ha respondido, envía "RESP:<code>:BLANK"
            if (!hasResponded) {
                sendResponse("BLANK")
                // Esto internamente pondrá hasResponded = true y deshabilitará los botones
            }
            runOnUiThread {
                stopScan()
                goToWaitResults()
            }
        }
    }

    private fun goToWaitResults() {
        startActivity(Intent(this, WaitResultsActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAdvertisingIfNeeded()
        stopScan()
    }
}
