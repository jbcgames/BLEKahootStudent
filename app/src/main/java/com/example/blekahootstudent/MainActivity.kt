package com.example.blekahootstudent

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private val TAG = "Student_Main"
    private var assignedCode: String? = null
    // BLE
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isAdvertising = false
    private var isScanning = false

    // UI
    private lateinit var etName: EditText
    private lateinit var btnSendName: Button
    private lateinit var tvStatus: TextView

    // Nombre del estudiante
    private var studentName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI
        etName = findViewById(R.id.etName)
        btnSendName = findViewById(R.id.btnSendName)
        tvStatus = findViewById(R.id.tvStatus)

        // Inicializar BLE
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        // Pedir permisos
        checkAndRequestPermissions()

        btnSendName.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Ingresa un nombre válido", Toast.LENGTH_SHORT).show()
            } else {
                studentName = name
                startAdvertisingName(name)
                startScanForEvents()
            }
        }
    }

    // ---------------------------------------------------
    // 1. Pedir permisos BLE
    // ---------------------------------------------------
    private fun checkAndRequestPermissions() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        val missing = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }

    // ---------------------------------------------------
    // 2. Enviar "NAME:<nombre>"
    // ---------------------------------------------------
    private fun startAdvertisingName(name: String) {
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Activa Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }
        if (advertiser == null) {
            Toast.makeText(this, "No se puede hacer Advertising", Toast.LENGTH_SHORT).show()
            return
        }

        val dataString = "NAME:$name"
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

        tvStatus.text = "Enviando nombre \"$name\"..."
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
        }
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Toast.makeText(this@MainActivity, "Error advertising: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAdvertisingIfNeeded() {
        if (isAdvertising) {
            advertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
        }
    }

    // ---------------------------------------------------
    // 3. Escanear eventos ("CONF:", "START:")
    // ---------------------------------------------------
    private fun startScanForEvents() {
        if (isScanning) return
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Activa Bluetooth para escanear", Toast.LENGTH_SHORT).show()
            return
        }
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "No se puede escanear", Toast.LENGTH_SHORT).show()
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
    }

    private fun stopScan() {
        if (!isScanning) return
        bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
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
        }
    }

    // FILE: MainActivity.kt (Estudiante)

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val data = record.getManufacturerSpecificData(0x1234) ?: return
        val dataString = String(data)

        if (dataString.startsWith("CONF:")) {
            val parts = dataString.split(":")
            if (parts.size == 3) {
                val confirmedName = parts[1]
                val code = parts[2]
                if (confirmedName == studentName) {
                    // (1) Guardar code
                    assignedCode = code
                    runOnUiThread {
                        tvStatus.text = "Confirmado con código $code. Esperando START..."
                        btnSendName.isEnabled = false
                    }
                    // (2) Mandar ACKCODE
                    advertiseAckCode(code)
                }
            }
        } else if (dataString.startsWith("START:ALL")) {
            runOnUiThread {
                goToWaitingActivity()
            }
        }
    }



    /**
     * Guarda el assignedCode en SharedPreferences (método B).
     */
    // FILE: MainActivity.kt (Estudiante)

    private fun advertiseAckCode(code: String) {
        stopAdvertisingIfNeeded() // si ya estabas haciendo un advertise, detenlo

        val dataString = "ACKCODE:$code"
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

        // Detener en 2s
        Handler(Looper.getMainLooper()).postDelayed({
            stopAdvertisingIfNeeded()
        }, 2000)
    }

    private fun saveAssignedCode(code: String) {
        val prefs = getSharedPreferences("BLE_Kahoot_Student", MODE_PRIVATE)
        prefs.edit().putString("assigned_code", code).apply()
    }

    private fun goToWaitingActivity() {
        stopScan()
        stopAdvertisingIfNeeded()
        val intent = Intent(this, WaitingActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
        stopAdvertisingIfNeeded()
    }
}
