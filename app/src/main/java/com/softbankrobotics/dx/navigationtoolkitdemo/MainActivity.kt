package com.softbankrobotics.dx.navigationtoolkitdemo

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Bundle
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.Frame
import com.aldebaran.qi.sdk.`object`.conversation.*
import com.aldebaran.qi.sdk.`object`.human.Human
import com.aldebaran.qi.sdk.`object`.humanawareness.HumanAwareness
import com.aldebaran.qi.sdk.autonomousrecharge.AutonomousRecharge
import com.aldebaran.qi.sdk.builder.ChatBuilder
import com.aldebaran.qi.sdk.builder.QiChatbotBuilder
import com.aldebaran.qi.sdk.builder.TopicBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.softbankrobotics.dx.dynamicconversationmenu.DynamicConversationMenuFragment
import com.softbankrobotics.dx.dynamicconversationmenu.MenuItemData
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : RobotActivity(), RobotLifecycleCallbacks, InputManager.InputDeviceListener,
    Chat.OnStartedListener, QiChatbot.OnBookmarkReachedListener,
    HumanAwareness.OnHumansAroundChangedListener {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var inputManager: InputManager? = null
    private var humanAwareness: HumanAwareness? = null
    private var qiChatbot: QiChatbot? = null
    private lateinit var bookmarks: MutableMap<String, Bookmark>
    private var chat: Chat? = null
    private var chatFuture: Future<Void>? = null
    private var gamepadConnected = false
    private var humanDetected = false
    private var passiveLookingAnimationsLoop: PassiveLookingAnimationsLoop? = null
    private var chargingStationFrame: Frame? = null
    private var mapFrame: Frame? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        setContentView(R.layout.activity_main)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)
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
            gamepadConnected = false
            runOnUiThread {
                gamepadIcon.setImageResource(R.drawable.gamepad_not_connected)
            }
        } else {
            Log.i(TAG, "Gamepad connected")
            gamepadConnected = true
            runOnUiThread {
                gamepadIcon.setImageResource(R.drawable.gamepad_connected)
            }
        }
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.i(TAG, "Robot focus gained")

        // Check if the robot is docked
        Utils.checkRobotIsDocked(this, qiContext)

        // Build the passive looking animations loop
        passiveLookingAnimationsLoop = PassiveLookingAnimationsLoop(qiContext)

        // Get the charging station Frame
        chargingStationFrame = qiContext.mapping.chargingStationFrame()

        // Get the map Frame
        mapFrame = qiContext.mapping.mapFrame()

        // Build and run Chat
        val topic = TopicBuilder.with(qiContext).withResource(R.raw.main).build()
        bookmarks = topic.bookmarks

        qiChatbot = QiChatbotBuilder.with(qiContext).withTopic(topic).build()
        qiChatbot!!.addOnBookmarkReachedListener(this)

        chat = ChatBuilder.with(qiContext).withChatbot(qiChatbot).build()
        chat!!.addOnStartedListener(this)
        chatFuture = chat!!.async().run()

        val menuItemList: ArrayList<MenuItemData> = ArrayList()
        menuItemList.add(
                MenuItemData(
                        getString(R.string.come_here),
                        R.drawable.come_here,
                        "come_here"
                )
        )
        menuItemList.add(
                MenuItemData(
                        getString(R.string.follow_me),
                        R.drawable.follow_me,
                        "follow_me"
                )
        )
        menuItemList.add(
                MenuItemData(
                        getString(R.string.gamepad),
                        R.drawable.gamepad,
                        "gamepad"
                )
        )
        menuItemList.add(
                MenuItemData(
                        getString(R.string.map),
                        R.drawable.mapping,
                        "display_mapping_menu"
                )
        )
        menuItemList.add(
                MenuItemData(
                        getString(R.string.go_home),
                        R.drawable.go_home,
                        "go_home"
                )
        )
        menuItemList.add(
                MenuItemData(
                        getString(R.string.go_to_pod),
                        R.drawable.go_to_pod,
                        "go_to_pod"
                )
        )
        menuItemList.add(
                MenuItemData(
                        getString(R.string.point_at_home),
                        R.drawable.point_at_home,
                        "point_at_home"
                )
        )
        menuItemList.add(
                MenuItemData(
                        getString(R.string.quit),
                        R.drawable.quit,
                        "quit"
                )
        )

        // Add the DynamicConversationMenuFragment
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        val mainMenuFragment = DynamicConversationMenuFragment(
                menuItemList = menuItemList,
                topic = topic,
                qiChatbot = qiChatbot,
                numberOfItemsPerLine = 4
        )
        fragmentTransaction.replace(R.id.mainMenuContainer, mainMenuFragment)
        fragmentTransaction.commit()

        // Look for humans
        humanAwareness = qiContext.humanAwareness
        humanAwareness!!.addOnHumansAroundChangedListener(this)

        // Check if a human is already detected
        val humansAround = qiContext.humanAwareness.humansAround
        onHumansAroundChanged(humansAround)
    }

    override fun onHumansAroundChanged(humans: MutableList<Human>) {
        if (humans.size != 0) {
            Log.i(TAG, "Human detected")
            humanDetected = true
            runOnUiThread {
                humanIcon.setImageResource(R.drawable.human_detected)
            }
            passiveLookingAnimationsLoop?.stop()
        } else {
            Log.i(TAG, "No human detected")
            humanDetected = false
            runOnUiThread {
                humanIcon.setImageResource(R.drawable.no_human_detected)
            }
            passiveLookingAnimationsLoop?.start()
        }
    }

    override fun onStarted() {
        Log.i(TAG, "Chat started")
        qiChatbot!!.async().goToBookmark(
                bookmarks["start"],
                AutonomousReactionImportance.HIGH,
                AutonomousReactionValidity.IMMEDIATE
        )
    }

    override fun onBookmarkReached(bookmark: Bookmark) {
        Log.i(TAG, "Bookmark reached: ${bookmark.name}")
        when (bookmark.name) {
            "gamepad" -> {
                if (gamepadConnected) {
                    qiChatbot!!.async().goToBookmark(
                            bookmarks["gamepad_connected"],
                            AutonomousReactionImportance.HIGH,
                            AutonomousReactionValidity.IMMEDIATE
                    )
                } else {
                    qiChatbot!!.async().goToBookmark(
                            bookmarks["no_gamepad"],
                            AutonomousReactionImportance.HIGH,
                            AutonomousReactionValidity.IMMEDIATE
                    )
                }
            }
            "start_gamepad" -> {
                startActivity(Intent(this, GamepadActivity::class.java))
            }
            "display_mapping_menu" -> {
                startActivity(Intent(this, MappingMenuActivity::class.java))
            }
            "come_here" -> {
                if (humanDetected) {
                    qiChatbot!!.async().goToBookmark(
                            bookmarks["come_here_human_detected"],
                            AutonomousReactionImportance.HIGH,
                            AutonomousReactionValidity.IMMEDIATE
                    )
                } else {
                    qiChatbot!!.async().goToBookmark(
                            bookmarks["come_here_human_not_detected"],
                            AutonomousReactionImportance.HIGH,
                            AutonomousReactionValidity.IMMEDIATE
                    )
                }
            }
            "start_come_here" -> {
                val intent = Intent(this, FollowMeActivity::class.java)
                intent.putExtra("comeHere", true)
                startActivity(intent)
            }
            "follow_me" -> {
                if (humanDetected) {
                    qiChatbot!!.async().goToBookmark(
                        bookmarks["follow_me_human_detected"],
                        AutonomousReactionImportance.HIGH,
                        AutonomousReactionValidity.IMMEDIATE
                    )
                } else {
                    qiChatbot!!.async().goToBookmark(
                        bookmarks["follow_me_human_not_detected"],
                        AutonomousReactionImportance.HIGH,
                        AutonomousReactionValidity.IMMEDIATE
                    )
                }
            }
            "start_follow_me" -> {
                startActivity(Intent(this, FollowMeActivity::class.java))
            }
            "go_to_pod" -> {
                // Check if a ChargingStationFrame is in robot's memory
                if (chargingStationFrame == null) {
                    qiChatbot!!.async().goToBookmark(
                        bookmarks["cant_go_to_pod"],
                        AutonomousReactionImportance.HIGH,
                        AutonomousReactionValidity.IMMEDIATE
                    )
                } else {
                    try {
                        AutonomousRecharge.startDockingActivity(this, true)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Start docking error: ", t)
                    }
                }
            }
            "go_home" -> {
                // Check that there's map Frame in robot's memory
                if (mapFrame == null) {
                    qiChatbot!!.async().goToBookmark(
                            bookmarks["cant_go_home"],
                            AutonomousReactionImportance.HIGH,
                            AutonomousReactionValidity.IMMEDIATE
                    )
                } else {
                    startActivity(Intent(this, GoHomeActivity::class.java))
                }
            }
            "point_at_home" -> {
                // Check that there's map Frame in robot's memory
                if (mapFrame == null) {
                    qiChatbot!!.async().goToBookmark(
                            bookmarks["cant_point_at_home"],
                            AutonomousReactionImportance.HIGH,
                            AutonomousReactionValidity.IMMEDIATE
                    )
                } else {
                    startActivity(Intent(this, PointAtHomeActivity::class.java))
                }
            }
            "quit" -> {
                finish()
            }
        }
    }

    override fun onRobotFocusRefused(reason: String) {
        Log.e(TAG, "Robot focus refused: $reason")
    }

    override fun onRobotFocusLost() {
        Log.i(TAG, "Robot focus lost")
        humanAwareness?.removeAllOnHumansAroundChangedListeners()
        qiChatbot?.removeAllOnBookmarkReachedListeners()
        chat?.removeAllOnStartedListeners()
        passiveLookingAnimationsLoop?.stop()
        chatFuture?.requestCancellation()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        inputManager?.unregisterInputDeviceListener(this)
        QiSDK.unregister(this, this)
    }
}