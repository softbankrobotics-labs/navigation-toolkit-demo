package com.softbankrobotics.dx.navigationtoolkitdemo

import android.app.Activity
import android.content.Intent
import android.hardware.input.InputManager
import android.util.Log
import android.view.InputDevice
import com.aldebaran.qi.sdk.QiContext
import kotlin.math.round

object Utils {

    const val TAG = "Utils"

    fun getGameControllerIds(inputManager: InputManager): List<Int> {
        Log.d(TAG, "getGameControllerIds")
        val gameControllerDeviceIds = mutableListOf<Int>()
        val deviceIds = inputManager.inputDeviceIds
        deviceIds.forEach { deviceId ->
            InputDevice.getDevice(deviceId).apply {
                // Verify that the device has gamepad buttons, control sticks, or both.
                if (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
                    || sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
                ) {
                    // This device is a game controller. Store its device ID.
                    gameControllerDeviceIds
                            .takeIf { !it.contains(deviceId) }
                            ?.add(deviceId)
                }
            }
        }
        return gameControllerDeviceIds
    }

    fun checkRobotIsDocked(activity: Activity, qiContext: QiContext) {
        Log.d(TAG, "checkRobotIsDocked")
        val chargingStationFrame = qiContext.mapping.chargingStationFrame()
        if (chargingStationFrame != null) {
            val robotFrame = qiContext.actuation.robotFrame()
            chargingStationFrame.async().computeTransform(robotFrame)
                .thenConsume { transformTimeFuture ->
                    if (transformTimeFuture.isSuccess) {
                        val transformTime = transformTimeFuture.get()
                        val robotDockVector = transformTime.transform.translation
                        val vectorX = round(robotDockVector.x)
                        val vectorY = round(robotDockVector.y)
                        if (vectorX == 0.0 && vectorY == 0.0) {
                            Log.i(TAG, "Robot is docked")
                            activity.startActivity(
                                Intent(
                                    activity,
                                    DockingMenuActivity::class.java
                                )
                            )
                            activity.finish()
                        } else {
                            Log.i(TAG, "Robot isn't docked")
                        }
                    } else {
                        Log.d(
                            TAG,
                            "Failed to define if robot is docked: ${transformTimeFuture.errorMessage}"
                        )
                    }
                }
        }
    }

    fun checkChargingFlapIsClosed(activity: Activity, qiContext: QiContext) {
        Log.d(TAG, "checkChargingFlapIsClosed")
        val chargingFlap = qiContext.power.chargingFlap
        if (chargingFlap.state.open) {
            Log.i(TAG, "Charging flap is open")
            activity.startActivity(Intent(activity, ChargingFlapActivity::class.java))
        } else {
            Log.i(TAG, "Charging flap is closed")
            chargingFlap.addOnStateChangedListener { flapState ->
                if (flapState.open) {
                    Log.i(TAG, "Charging flap is open")
                    chargingFlap.removeAllOnStateChangedListeners()
                    activity.startActivity(
                        Intent(
                            activity,
                            ChargingFlapActivity::class.java
                        )
                    )
                }
            }
        }
    }
}