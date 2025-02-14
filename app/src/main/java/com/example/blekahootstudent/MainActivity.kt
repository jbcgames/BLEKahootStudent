package com.example.blekahootstudent

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var isAdvertising = false
    private val TAG = "BLE_Advertising"

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkAndRequestPermissions()

        // Inicializar Bluetooth
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Bluetooth no disponible o apagado", Toast.LENGTH_LONG).show()
            return
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Toast.makeText(this, "Este dispositivo no soporta BLE Advertising", Toast.LENGTH_LONG).show()
            return
        }

        // Botones
        val buttonA = findViewById<Button>(R.id.buttonA)
        val buttonB = findViewById<Button>(R.id.buttonB)
        val buttonC = findViewById<Button>(R.id.buttonC)
        val buttonD = findViewById<Button>(R.id.buttonD)

        buttonA.setOnClickListener { startAdvertising("A") }
        buttonB.setOnClickListener { startAdvertising("B") }
        buttonC.setOnClickListener { startAdvertising("C") }
        buttonD.setOnClickListener { startAdvertising("D") }
    }

    /**
     * Verifica y solicita los permisos necesarios en tiempo de ejecución.
     */
    private fun checkAndRequestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    /**
     * Maneja la respuesta de los permisos otorgados por el usuario.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permisos concedidos", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permisos de Bluetooth necesarios", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Genera un identificador único basado en el dispositivo.
     */
    private fun generateDeviceId(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        return androidId.takeLast(6) // Tomamos los últimos 6 caracteres para mayor privacidad
    }

    /**
     * Inicia el BLE Advertising con la opción seleccionada.
     */
    private fun startAdvertising(option: String) {
        try {
            if (isAdvertising) {
                advertiser?.stopAdvertising(advertiseCallback)
                isAdvertising = false
            }

            val deviceId = generateDeviceId()
            val dataToSend = "$deviceId:$option".toByteArray()

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build()

            val data = AdvertiseData.Builder()
                .addManufacturerData(0x1234, dataToSend) // 0x1234 es un ID de fabricante ficticio
                .setIncludeDeviceName(false)
                .build()

            advertiser?.startAdvertising(settings, data, advertiseCallback)
            isAdvertising = true

            Toast.makeText(this, "Enviando: $deviceId:$option", Toast.LENGTH_SHORT).show()

            // Detener la transmisión después de 5 segundos
            Handler(Looper.getMainLooper()).postDelayed({
                advertiser?.stopAdvertising(advertiseCallback)
                isAdvertising = false
                Toast.makeText(this, "Advertising detenido", Toast.LENGTH_SHORT).show()
            }, 5000)

        } catch (e: SecurityException) {
            Toast.makeText(this, "Error de permisos BLE: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e(TAG, "SecurityException: ${e.message}")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "Advertising iniciado correctamente")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Error en advertising: $errorCode")
            Toast.makeText(applicationContext, "Error en advertising: $errorCode", Toast.LENGTH_SHORT).show()
        }
    }
}
