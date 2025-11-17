package com.example.cowboy

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import java.util.UUID


class BLEUARTService : Service() {
    // Bluetooth variables
    var bluetoothAdapter: BluetoothAdapter? = null
    var bluetoothLeScanner: BluetoothLeScanner? = null
    var bluetoothManager: BluetoothManager? = null
    var bluetoothScanCallback: ScanCallback? = null
    var gattClient: BluetoothGatt? = null
    var bleBinder: BLEBinder?

    val UARTSERVICE_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")


    val TAG: String = "MicroBitConnectService"


    val uBit_name: String = "BBC micro:bit [tapit]"
    val uBit_address: String = "D9:79:94:89:06:31"

    //list of listeners for data received events
    private val listeners: MutableList<BLEListener> = ArrayList<BLEListener>()

    init {
        bleBinder = BLEBinder()
    }

    fun addBLEListener(listener: BLEListener?) {
        listeners.add(listener!!)
    }

    /**
     * Class used for the client Binder. The Binder object is responsible for returning an instance
     * of "BLEService" to the client.
     */
    inner class BLEBinder : Binder() {
        val service: BLEUARTService
            get() =// Return this instance of MyService so clients can call public methods
                this@BLEUARTService
    }


    override fun onBind(intent: Intent?): IBinder? {
        return bleBinder
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_STICKY
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    fun startScan() {
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val devices = bluetoothManager!!.getConnectedDevices(BluetoothProfile.GATT)
        for (device in devices) {
            if (device.getAddress().equals(uBit_address)) {
                connectDevice(device);
                return;
            }
        }
        bluetoothAdapter = bluetoothManager!!.getAdapter()
        bluetoothScanCallback = BluetoothScanCallback()
        bluetoothLeScanner = bluetoothAdapter!!.getBluetoothLeScanner()
        bluetoothLeScanner!!.startScan(bluetoothScanCallback)
        Log.i(TAG, "startScan()")
    }

    // BLUETOOTH CONNECTION
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectDevice(device: BluetoothDevice?) {
        if (device == null) {
            Log.i(TAG, "Device is null")
            return
        }
        val gattClientCallback: GattClientCallback = GattClientCallback()
        gattClient = device.connectGatt(this, false, gattClientCallback, 2)
    }

    // BLE Scan Callbacks
    private inner class BluetoothScanCallback : ScanCallback() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (result.getDevice().getName() != null) {
                Log.i(TAG, result.getDevice().getName())
                if (result.getDevice().getAddress().equals(uBit_address)) {
                    connectDevice(result.getDevice())
                    bluetoothLeScanner!!.stopScan(bluetoothScanCallback) // stop scan
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult?>?) {
            Log.i(TAG, "onBatchScanResults")
        }

        override fun onScanFailed(errorCode: Int) {
            Log.i(TAG, "ErrorCode: " + errorCode)
        }
    }

    // Bluetooth GATT Client Callback
    private inner class GattClientCallback : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.i(TAG, "onConnectionStateChange")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "GATT operation unsuccessful (status): " + status)
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "onConnectionStateChange CONNECTED")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "onConnectionStateChange DISCONNECTED")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.i(TAG, "onServicesDiscovered")
            if (status != BluetoothGatt.GATT_SUCCESS) return
            gattClient = gatt

            val service = gatt.getService(UARTSERVICE_SERVICE_UUID)
            val characteristics = service.getCharacteristics()
            //List and display in log the characteristics of this service
            for (characteristic in characteristics) {
                Log.i(TAG, characteristic.getUuid().toString())
                gatt.setCharacteristicNotification(characteristic, true)

                val descriptors = characteristic.getDescriptors()
                for (descriptor in descriptors) {
                    Log.i(TAG, descriptor.getUuid().toString())
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                    gatt.writeDescriptor(descriptor)
                }
            }
        }

        //this is the callback that receives the accelerometer data
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            Log.i(TAG, "onCharacteristicChanged: " + String(characteristic.getValue()))
            for (listener in listeners) {
                listener.dataReceived(characteristic.getValue())
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            Log.i(TAG, "onCharacteristicRead")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.i(TAG, "onCharacteristicWrite")
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)
            Log.i(TAG, "onDescriptorRead")
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            Log.i(TAG, "onDescriptorWrite")
        }
    }
}