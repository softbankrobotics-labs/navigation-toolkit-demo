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
import com.aldebaran.qi.sdk.`object`.conversation.*
import com.aldebaran.qi.sdk.builder.ChatBuilder
import com.aldebaran.qi.sdk.builder.QiChatbotBuilder
import com.aldebaran.qi.sdk.builder.TopicBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.softbankrobotics.dx.dynamicconversationmenu.DynamicConversationMenuFragment
import com.softbankrobotics.dx.dynamicconversationmenu.MenuItemData
import kotlinx.android.synthetic.main.activity_mapping_menu.*

class MappingMenuActivity : RobotActivity(), RobotLifecycleCallbacks,
    InputManager.InputDeviceListener,
    Chat.OnStartedListener, QiChatbot.OnEndedListener, QiChatbot.OnBookmarkReachedListener {

    companion object {
        private const val TAG = "MappingMenuActivity"
    }

    private var inputManager: InputManager? = null
    private var gamepadConnected = false
    private var qiChatbot: QiChatbot? = null
    private lateinit var bookmarks: MutableMap<String, Bookmark>
    private var chat: Chat? = null
    private var chatFuture: Future<Void>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        setContentView(R.layout.activity_mapping_menu)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)

        homeButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

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
        Log.d(TAG, "checkControllerConnection")
        val connectedControllers = Utils.getGameControllerIds(inputManager!!)
        gamepadConnected = connectedControllers.isNotEmpty()
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.i(TAG, "Robot focus gained")

        // Check if the robot is docked
        Utils.checkRobotIsDocked(this, qiContext)

        // Build and run Chat
        val topic = TopicBuilder.with(qiContext).withResource(R.raw.mapping_menu).build()
        bookmarks = topic.bookmarks

        qiChatbot = QiChatbotBuilder.with(qiContext).withTopic(topic).build()
        qiChatbot!!.addOnBookmarkReachedListener(this)
        qiChatbot!!.addOnEndedListener(this)

        chat = ChatBuilder.with(qiContext).withChatbot(qiChatbot).build()
        chat!!.addOnStartedListener(this)
        chatFuture = chat!!.async().run()

        // Create the list of the cards to be displayed
        val menuItemList: ArrayList<MenuItemData> = ArrayList()
        menuItemList.add(
            MenuItemData(
                getString(R.string.default_mapping),
                R.drawable.default_mapping,
                "default_mapping"
            )
        )
        menuItemList.add(
            MenuItemData(
                getString(R.string.gamepad_mapping),
                R.drawable.gamepad_mapping,
                "gamepad_mapping"
            )
        )

        // Add the DynamicConversationMenuFragment
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        val mappingModeMenuFragment = DynamicConversationMenuFragment(
            menuItemList = menuItemList,
            topic = topic,
            qiChatbot = qiChatbot,
        )
        fragmentTransaction.replace(R.id.mappingModeMenuContainer, mappingModeMenuFragment)
        fragmentTransaction.commit()
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
        if (bookmark.name == "start_default_mapping") {
            startActivity(Intent(this, MappingActivity::class.java))
            finish()
        } else if (bookmark.name == "gamepad_mapping") {
            if (gamepadConnected) {
                val intent = Intent(this, GamepadActivity::class.java)
                intent.putExtra("mapping", true)
                startActivity(intent)
                finish()
            } else {
                qiChatbot!!.async().goToBookmark(
                    bookmarks["no_gamepad"],
                    AutonomousReactionImportance.HIGH,
                    AutonomousReactionValidity.IMMEDIATE
                )
            }
        }
    }

    override fun onEnded(endReason: String) {
        Log.i(TAG, "Chat done, reason: $endReason")
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onRobotFocusRefused(reason: String) {
        Log.e(TAG, "Robot focus refused: $reason")
    }

    override fun onRobotFocusLost() {
        Log.i(TAG, "Robot focus lost")
        chat?.removeAllOnStartedListeners()
        qiChatbot?.removeAllOnBookmarkReachedListeners()
        qiChatbot?.removeAllOnEndedListeners()
        chatFuture?.requestCancellation()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        inputManager?.unregisterInputDeviceListener(this)
        QiSDK.unregister(this, this)
    }
}