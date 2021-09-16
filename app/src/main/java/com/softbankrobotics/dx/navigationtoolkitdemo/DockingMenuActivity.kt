package com.softbankrobotics.dx.navigationtoolkitdemo

import android.os.Bundle
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.conversation.*
import com.aldebaran.qi.sdk.autonomousrecharge.AutonomousRecharge
import com.aldebaran.qi.sdk.builder.ChatBuilder
import com.aldebaran.qi.sdk.builder.QiChatbotBuilder
import com.aldebaran.qi.sdk.builder.TopicBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.softbankrobotics.dx.dynamicconversationmenu.DynamicConversationMenuFragment
import com.softbankrobotics.dx.dynamicconversationmenu.MenuItemData

class DockingMenuActivity : RobotActivity(), RobotLifecycleCallbacks, Chat.OnStartedListener, QiChatbot.OnEndedListener {

    companion object {
        private const val TAG = "DockingMenuActivity"
    }

    private var qiChatbot: QiChatbot? = null
    private lateinit var bookmarks: MutableMap<String, Bookmark>
    private var chat: Chat? = null
    private var chatFuture: Future<Void>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        setContentView(R.layout.activity_docking_menu)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.i(TAG, "Robot focus gained")

        // Build and run Chat
        val topic = TopicBuilder.with(qiContext).withResource(R.raw.docking_menu).build()
        bookmarks = topic.bookmarks

        qiChatbot = QiChatbotBuilder.with(qiContext).withTopic(topic).build()
        qiChatbot!!.addOnEndedListener(this)

        chat = ChatBuilder.with(qiContext).withChatbot(qiChatbot).build()
        chat!!.addOnStartedListener(this)
        chatFuture = chat!!.async().run()

        // Create the list of the cards to be displayed
        val menuItemList: ArrayList<MenuItemData> = ArrayList()
        menuItemList.add(
                MenuItemData(
                        getString(R.string.yes),
                        R.drawable.quit_pod,
                        "quit_pod"
                )
        )
        menuItemList.add(
                MenuItemData(
                        getString(R.string.no),
                        R.drawable.dont_quit_pod,
                        ""
                )
        )

        // Add the DynamicConversationMenuFragment
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        val dockingMenuFragment = DynamicConversationMenuFragment(
            menuItemList = menuItemList,
            topic = topic,
            qiChatbot = qiChatbot,
        )
        fragmentTransaction.replace(R.id.dockingMenuContainer, dockingMenuFragment)
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

    override fun onEnded(endReason: String) {
        Log.i(TAG, "Chat done, reason: $endReason")
        try {
            AutonomousRecharge.startUndockingActivity(this)
            finish()
        } catch (t: Throwable) {
            Log.e(TAG, "start undocking error", t)
        }
    }

    override fun onRobotFocusRefused(reason: String) {
        Log.e(TAG, "Robot focus refused: $reason")
    }

    override fun onRobotFocusLost() {
        Log.i(TAG, "Robot focus lost")
        qiChatbot?.removeAllOnEndedListeners()
        chat?.removeAllOnStartedListeners()
        chatFuture?.requestCancellation()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        QiSDK.unregister(this, this)
    }
}