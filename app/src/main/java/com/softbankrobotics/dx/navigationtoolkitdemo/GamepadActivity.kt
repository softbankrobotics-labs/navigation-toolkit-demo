package com.softbankrobotics.dx.navigationtoolkitdemo

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.ExplorationMap
import com.aldebaran.qi.sdk.`object`.actuation.LocalizeAndMap
import com.aldebaran.qi.sdk.`object`.conversation.*
import com.aldebaran.qi.sdk.builder.ChatBuilder
import com.aldebaran.qi.sdk.builder.LocalizeAndMapBuilder
import com.aldebaran.qi.sdk.builder.QiChatbotBuilder
import com.aldebaran.qi.sdk.builder.TopicBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.aldebaran.qi.sdk.util.copyToStream
import com.softbankrobotics.peppergamepad.RemoteRobotController
import kotlinx.android.synthetic.main.activity_gamepad.*
import java.io.File
import kotlin.math.abs

class GamepadActivity : RobotActivity(), InputManager.InputDeviceListener, RobotLifecycleCallbacks,
        Chat.OnStartedListener, QiChatbot.OnEndedListener {

    companion object {
        private const val TAG = "GamepadActivity"
        private const val FOLDER_NAME = "places"
        private const val MAP_FILE_NAME = "map.qimap"
    }

    private var inputManager: InputManager? = null
    private var remoteRobotController: RemoteRobotController? = null
    private var qiChatbot: QiChatbot? = null
    private lateinit var bookmarks: MutableMap<String, Bookmark>
    private var chat: Chat? = null
    private var chatFuture: Future<Void>? = null
    private var mapping = false
    private var localizeAndMap: LocalizeAndMap? = null
    private var localizeAndMapFuture: Future<Void>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        setContentView(R.layout.activity_gamepad)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)

        homeButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        mapping = intent.getBooleanExtra("mapping", false)
        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager!!.registerInputDeviceListener(this, null)
        checkControllerConnection()
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        Log.d(TAG, "onInputDeviceRemoved")
        checkControllerConnection()
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        Log.d(TAG, "onInputDeviceAdded")
        checkControllerConnection()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        Log.d(TAG, "onInputDeviceChanged")
        checkControllerConnection()
    }

    private fun checkControllerConnection() {
        val connectedControllers = Utils.getGameControllerIds(inputManager!!)
        if (connectedControllers.isEmpty()) {
            Log.i(TAG, "No gamepad connected")
            runOnUiThread {
                backgroundGifImageView.visibility = View.GONE
                errorImageView.visibility = View.VISIBLE
            }
        } else {
            Log.i(TAG, "Gamepad connected")
            runOnUiThread {
                backgroundGifImageView.visibility = View.VISIBLE
                errorImageView.visibility = View.GONE
            }
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        // Add null protection for when the controller disconnects
        val inputDevice = event.device ?: return super.onGenericMotionEvent(event)

        // Get left joystick coordinates
        val leftJoystickX = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_X)
        val leftJoystickY = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_Y)

        // Get right joystick coordinates
        val rightJoystickX = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_Z)
        val rightJoystickY = getCenteredAxis(event, inputDevice, MotionEvent.AXIS_RZ)

        AsyncTask.execute {
            remoteRobotController?.updateTarget(leftJoystickX, leftJoystickY, rightJoystickX, rightJoystickY)
        }

        Log.d(TAG, "onGenericMotionEvent: LeftX=$leftJoystickX, LeftY=$leftJoystickY, RightX=$rightJoystickX, RightY=$rightJoystickY")
        return true
    }

    private fun getCenteredAxis(
            event: MotionEvent,
            device: InputDevice,
            axis: Int
    ): Float {
        val range: InputDevice.MotionRange? = device.getMotionRange(axis, event.source)

        // A joystick at rest does not always report an absolute position of
        // (0,0). Use the getFlat() method to determine the range of values
        // bounding the joystick axis center.
        range?.apply {
            val value = event.getAxisValue(axis)

            // Ignore axis values that are within the 'flat' region of the
            // joystick axis center.
            if (abs(value) > flat) {
                return value
            }
        }
        return 0f
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        Log.d(TAG, "onKeyDown $event")

        if (keyCode == KeyEvent.KEYCODE_BUTTON_X || keyCode == KeyEvent.KEYCODE_BUTTON_A || keyCode == KeyEvent.KEYCODE_BUTTON_Y || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            // Go back to Main screen
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return true
        }
        return false
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.i(TAG, "Robot focus gained")

        // Check if the robot is docked
        Utils.checkRobotIsDocked(this, qiContext)

        // Check if the charging flap is closed
        Utils.checkChargingFlapIsClosed(this, qiContext)

        // Build and run Chat
        val topic = TopicBuilder.with(qiContext).withResource(R.raw.gamepad).build()
        bookmarks = topic.bookmarks

        qiChatbot = QiChatbotBuilder.with(qiContext).withTopic(topic).build()
        qiChatbot!!.addOnEndedListener(this)

        chat = ChatBuilder.with(qiContext).withChatbot(qiChatbot).build()
        chat!!.listeningBodyLanguage = BodyLanguageOption.DISABLED
        chat!!.addOnStartedListener(this)
        chatFuture = chat!!.async().run()

        // Start mapping if necessary
        if (mapping) {
            localizeAndMap = LocalizeAndMapBuilder.with(qiContext).build()
            localizeAndMapFuture = localizeAndMap!!.async().run()
        }

        remoteRobotController = RemoteRobotController(qiContext)
    }

    override fun onStarted() {
        Log.i(TAG, "Chat started")
        qiChatbot!!.async().goToBookmark(
                bookmarks["start"],
                AutonomousReactionImportance.HIGH,
                AutonomousReactionValidity.IMMEDIATE
        )
    }

    override fun onEnded(endReason: String) {
        Log.i(TAG, "Chat done, reason: $endReason")
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun saveMapInFile(explorationMap: ExplorationMap) {
        Log.d(TAG, "saveMapInFile")
        val file = File(getExternalFilesDir(FOLDER_NAME), MAP_FILE_NAME)
        val buffer = explorationMap.serializeAsStreamableBuffer()
        file.outputStream().use { buffer.copyToStream(it) }
    }

    override fun onRobotFocusRefused(reason: String) {
        Log.e(TAG, "Robot focus refused: $reason")
    }

    override fun onRobotFocusLost() {
        Log.i(TAG, "Robot focus lost")
        qiChatbot?.removeAllOnEndedListeners()
        chat?.removeAllOnStartedListeners()
        chatFuture?.requestCancellation()
        localizeAndMapFuture?.requestCancellation()
        localizeAndMap?.dumpMap()?.let { saveMapInFile(it) }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        inputManager?.unregisterInputDeviceListener(this)
        QiSDK.unregister(this, this)
    }
}