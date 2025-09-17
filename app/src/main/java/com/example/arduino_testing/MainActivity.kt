package com.example.arduino_testing

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface

class MainActivity : AppCompatActivity() {
    private lateinit var completedata: TextView
    private var serial: UsbSerialDevice? = null
    private lateinit var usbManager: UsbManager

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.arduino_testing.USB_PERMISSION"
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        completedata = findViewById(R.id.completedata)
        usbManager = getSystemService(USB_SERVICE) as UsbManager

        // Register BroadcastReceiver for USB events
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }

        // Check for connected devices
        checkUsbDevice()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle USB device attachment
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            checkUsbDevice()
        }
    }

    private fun checkUsbDevice() {
        val deviceList = usbManager.deviceList
        if (deviceList.isNotEmpty()) {
            val device = deviceList.values.first()
            Log.d("USB", "Device found: ${device.deviceName}, VendorID=${device.vendorId}, ProductID=${device.productId}")

            // Check if we already have permission
            if (usbManager.hasPermission(device)) {
                Log.d("USB", "Permission already granted")
                connectToDevice(device)
            } else {
                val permissionIntent = PendingIntent.getBroadcast(
                    this, 0, Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                usbManager.requestPermission(device, permissionIntent)
            }
        } else {
            Toast.makeText(this, "No USB device found", Toast.LENGTH_SHORT).show()
            Log.d("USB", "No USB device found")
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("USB", "Broadcast received: ${intent?.action}")

            when (intent?.action) {
                ACTION_USB_PERMISSION -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                    if (granted && device != null) {
                        Log.d("USB", "Permission granted for ${device.deviceName}")
                        Toast.makeText(context, "USB Permission granted", Toast.LENGTH_SHORT).show()
                        connectToDevice(device)
                    } else {
                        Toast.makeText(context, "USB Permission denied", Toast.LENGTH_SHORT).show()
                        Log.d("USB", "USB Permission denied")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d("USB", "USB device attached")
                    checkUsbDevice()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d("USB", "USB device detached")
                    serial?.close()
                    serial = null
                    Toast.makeText(context, "USB device disconnected", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun connectToDevice(device: UsbDevice) {
        val connection = usbManager.openDevice(device)
        if (connection == null) {
            Toast.makeText(this, "Cannot open device", Toast.LENGTH_SHORT).show()
            Log.e("USB", "Connection is null")
            return
        }

        serial = UsbSerialDevice.createUsbSerialDevice(device, connection)
        if (serial != null) {
            if (serial!!.open()) {
                serial!!.setBaudRate(9600)
                serial!!.setDataBits(UsbSerialInterface.DATA_BITS_8)
                serial!!.setStopBits(UsbSerialInterface.STOP_BITS_1)
                serial!!.setParity(UsbSerialInterface.PARITY_NONE)
                serial!!.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)

                serial!!.read(callback)
                Toast.makeText(this, "Serial Connected", Toast.LENGTH_SHORT).show()
                Log.d("USB", "Serial connection opened")

                // Send a test message to Arduino
                Thread {
                    Thread.sleep(1000) // Wait for connection to stabilize
                    val testMessage = "Hello Arduino!\n"
                    serial?.write(testMessage.toByteArray())
                }.start()
            } else {
                Toast.makeText(this, "Error opening device", Toast.LENGTH_SHORT).show()
                Log.e("USB", "Error opening device")
            }
        } else {
            Toast.makeText(this, "Serial device not supported", Toast.LENGTH_SHORT).show()
            Log.e("USB", "Serial device is null")
        }
    }

    private val callback = UsbSerialInterface.UsbReadCallback { data ->
        val msg = String(data)
        Log.d("USB_DATA", "Received: $msg")
        runOnUiThread {
            completedata.text = "$msg"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            Log.e("USB", "Receiver not registered: ${e.message}")
        }
        serial?.close()
        Log.d("USB", "Serial closed and receiver unregistered")
    }
}