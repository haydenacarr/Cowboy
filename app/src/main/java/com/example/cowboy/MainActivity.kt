package com.example.cowboy

import android.Manifest
import android.util.Log
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.cowboy.BLEListener
import com.example.cowboy.BLEUARTService
import com.example.cowboy.BLEUARTService.BLEBinder


class MainActivity : AppCompatActivity(), BLEListener {
    var service: BLEUARTService? = null
    var mBound: Boolean = false

    var PERMISSION_ALL: Int = 1
    var PERMISSIONS: Array<String> = arrayOf<String>(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL)
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to LocalService.
        val intent = Intent(this, BLEUARTService::class.java)
        //Intent intent = new Intent(this, BLEService.class);
        bindService(intent, connection, BIND_AUTO_CREATE)
    }

    override fun dataReceived(data: ByteArray) {
        Log.d("MainActivity", "Works")
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
        override fun onServiceConnected(
            className: ComponentName?,
            iBinder: IBinder?
        ) {
            val binder = iBinder as BLEBinder
            service = binder.service
            service?.startScan()
            service?.addBLEListener(this@MainActivity)
            //service.addBLEListener(new MotionAnalyser());
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName?) {
            mBound = false
        }
    }


    fun hasPermissions(context: Context?, permissions: Array<out String>): Boolean {
        if (context != null && permissions != null) {
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
        }
        return true
    }

}