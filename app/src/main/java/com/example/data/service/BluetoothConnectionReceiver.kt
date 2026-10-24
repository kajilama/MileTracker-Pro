package com.example.data.service

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.local.PreferencesManager

class BluetoothConnectionReceiver : BroadcastReceiver() {

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prefs = PreferencesManager(context)
        val profile = prefs.userProfile.value

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

        var deviceName = "Unknown Bluetooth Device"
        if (device != null) {
            val hasBtConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (hasBtConnect) {
                try {
                    deviceName = device.name ?: device.address ?: "Car Audio System"
                } catch (e: SecurityException) {
                    deviceName = "Vehicle Audio"
                }
            } else {
                deviceName = "Connected Audio Device"
            }
        }

        val targetName = profile.selectedBluetoothDeviceName.trim()
        val isTargetMatch = targetName.isEmpty() ||
                targetName.equals("Any Car Bluetooth", ignoreCase = true) ||
                targetName.equals("Any Vehicle / Audio Bluetooth", ignoreCase = true) ||
                deviceName.contains(targetName, ignoreCase = true)

        TrackingDiagnosticLogger.logInfo(
            "Bluetooth",
            "Action: $action, Device: $deviceName, TargetMatch: $isTargetMatch"
        )

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                if (profile.bluetoothAutoStartEnabled && isTargetMatch) {
                    TrackingDiagnosticLogger.logInfo(
                        "Bluetooth",
                        "Auto-starting drive tracking due to BT connect with $deviceName"
                    )
                    DriveTrackingService.startService(
                        context = context,
                        manual = false,
                        tripTypeName = profile.defaultTripTypeName,
                        tripTypeId = profile.defaultTripTypeId
                    )
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                if (profile.bluetoothAutoStopEnabled && isTargetMatch) {
                    TrackingDiagnosticLogger.logInfo(
                        "Bluetooth",
                        "Auto-stopping drive tracking due to BT disconnect from $deviceName"
                    )
                    DriveTrackingService.stopDrive(context)
                }
            }
        }
    }
}
