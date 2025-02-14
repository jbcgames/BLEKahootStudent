package com.example.blekahootstudent

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputFilter
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private val TAG = "StudentApp"

    // Permisos
    private val PERMISSION_REQUEST_CODE = 100

    // Función para devolver permisos según la versión
    private fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
    }

    // UI
    private lateinit var etName: EditText
    private lateinit var btnSendName: Button
    private lateinit var tvStatus: TextView

    // BLE
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var isScanning = false

    // Datos
    private var studentName: String = ""
    private var assignedCode: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Referencias UI
        etName = findViewById(R.id.etName)
        btnSendName = findViewById(R.id.btnSendName)
        tvStatus = findViewById(R.id.tvStatus)

        // Limitar a 17 caracteres
        etName.filters = arrayOf(InputFilter.LengthFilter(17))

        // Inicializar Bluetooth
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

        btnSendName.setOnClickListener {
            studentName = etName.text.toString().trim()
            if (studentName.isEmpty()) {
                Toast.makeText(this, "Ingrese un nombre válido", Toast.LENGTH_SHORT).show()
            } else {
                // Pedir permisos condicionalmente
                checkAndRequestPermissions()
            }
        }
    }

    /**
     * Verifica permisos
     */
    private fun checkAndRequestPermissions() {
        val requiredPermissions = getRequiredPermissions()
        val missingPermissions = requiredPermissions.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            // Permisos OK
            startBleLogic()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startBleLogic()
            } else {
                for ((index, perm) in permissions.withIndex()) {
                    if (grantResults[index] == PackageManager.PERMISSION_DENIED) {
                        val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(this, perm)
                        if (!showRationale) {
                            // “No volver a preguntar”
                            showGoToSettingsDialog()
                            return
                        } else {
                            Toast.makeText(this, "Permiso $perm denegado", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permiso necesario")
            .setMessage("Necesitamos el permiso de Bluetooth/Ubicación para enviar tu nombre al profesor.")
            .setPositiveButton("Ir a Ajustes") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /**
     * Inicia la lógica BLE
     */
    private fun startBleLogic() {
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Activa el Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }
        startAdvertisingName(studentName)
        startScanningForTeacherResponse()
    }

    private fun startAdvertisingName(name: String) {
        // Si ya estaba haciendo advertising, lo detenemos
        if (isAdvertising) {
            advertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
        }
        if (advertiser == null) {
            Toast.makeText(this, "No se puede hacer Advertising en este dispositivo", Toast.LENGTH_SHORT).show()
            return
        }

        val dataString = "NAME:$name"
        val dataToSend = dataString.toByteArray()
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addManufacturerData(0x1234, dataToSend) // Mismo ID que el profesor escanea
            .setIncludeDeviceName(false)
            .build()

        advertiser?.startAdvertising(settings, data, advertiseCallback)
        isAdvertising = true

        tvStatus.text = "Estado: Enviando nombre \"$name\"..."
        Log.d(TAG, "Advertising con: $dataString")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertising iniciado correctamente")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "Error en advertising: $errorCode")
            Toast.makeText(applicationContext, "Error en advertising: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startScanningForTeacherResponse() {
        if (isScanning) return
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "Este dispositivo no puede escanear BLE", Toast.LENGTH_SHORT).show()
            return
        }

        val filter = ScanFilter.Builder()
            .setManufacturerData(0x1234, byteArrayOf()) // Mismo ID
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bluetoothLeScanner?.startScan(listOf(filter), settings, scanCallback)
        isScanning = true
        Log.d(TAG, "Iniciando escaneo BLE para CONF y START")
    }

    private fun stopScanning() {
        if (!isScanning) return
        bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
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
        val manufacturerData = result.scanRecord?.getManufacturerSpecificData(0x1234) ?: return
        val dataString = String(manufacturerData)
        Log.d(TAG, "Recibido: $dataString")

        when {
            dataString.startsWith("CONF:") -> {
                // CONF:<nombre>:<código>
                val parts = dataString.split(":")
                if (parts.size == 3) {
                    val confirmedName = parts[1]
                    val code = parts[2]
                    if (confirmedName == studentName) {
                        assignedCode = code
                        runOnUiThread {
                            tvStatus.text = "Estado: Confirmado con código $code. Esperando START..."
                            btnSendName.isEnabled = false
                        }
                    }
                }
            }
            dataString.startsWith("START") -> {
                // Podría ser "START:ALL", "START" o algo similar
                // Revisamos si el estudiante tiene un code asignado
                if (assignedCode != null) {
                    // Está confirmado, avanza
                    runOnUiThread {
                        tvStatus.text = "Estado: ¡Iniciando preguntas!"
                    }
                    stopScanning()
                    stopAdvertising()
                    goToNextScreen()
                } else {
                    // No confirmado => ignorar
                    Log.d(TAG, "Recibido START, pero no estamos confirmados. Se ignora.")
                }
            }
        }
    }


    private fun stopAdvertising() {
        if (isAdvertising) {
            advertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
        }
    }

    private fun goToNextScreen() {
        // Ir a la pantalla 2
        startActivity(Intent(this, WaitingActivity::class.java))
        finish()  // Cierra la pantalla actual si ya no quieres volver a ella
    }
}
