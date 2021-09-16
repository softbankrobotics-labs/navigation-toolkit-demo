package com.softbankrobotics.dx.navigationtoolkitdemo

import android.content.Intent
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
import com.softbankrobotics.dx.pepperextras.actuation.StubbornGoToBuilder
import kotlinx.android.synthetic.main.activity_go_home.*

class GoHomeActivity : RobotActivity(), RobotLifecycleCallbacks, QiChatbot.OnEndedListener,
    Chat.OnStartedListener {

    companion object {
        private const val TAG = "GoHomeActivity"
    }

    private var goHomeSuccess = false
    private var qiChatbot: QiChatbot? = null
    private lateinit var bookmarks: MutableMap<String, Bookmark>
    private var chat: Chat? = null
    private var chatFuture: Future<Void>? = null
    private var goToFuture: Future<Boolean>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        setContentView(R.layout.activity_go_home)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)

        homeButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.i(TAG, "Robot focus gained")

        // Check if the robot is docked
        Utils.checkRobotIsDocked(this, qiContext)

        // Check if the charging flap is closed
        Utils.checkChargingFlapIsClosed(this, qiContext)

        // Build and run Chat
        val topic = TopicBuilder.with(qiContext).withResource(R.raw.go_home).build()
        bookmarks = topic.bookmarks

        qiChatbot = QiChatbotBuilder.with(qiContext).withTopic(topic).build()
        qiChatbot!!.addOnEndedListener(this)

        chat = ChatBuilder.with(qiContext).withChatbot(qiChatbot).build()
        chat!!.listeningBodyLanguage = BodyLanguageOption.DISABLED
        chat!!.addOnStartedListener(this)

        // Get the MapFrame
        val mapFrame = qiContext.mapping.mapFrame()

        // Build and run the StubbornGoTo action
        val stubbornGoTo = StubbornGoToBuilder.with(qiContext)
                .withFrame(mapFrame)
                .build()
        goToFuture = stubbornGoTo.async().run()
        goToFuture!!.thenConsume {
            if (it.isSuccess) {
                goHomeSuccess = it.get()
            }
            chatFuture = chat!!.async().run()
        }
    }

    override fun onStarted() {
        Log.i(TAG, "Chat started")
        if (goHomeSuccess) {
            qiChatbot!!.async().goToBookmark(
                bookmarks["go_home_done"],
                AutonomousReactionImportance.HIGH,
                AutonomousReactionValidity.IMMEDIATE
            )
        } else {
            qiChatbot!!.async().goToBookmark(
                bookmarks["go_home_fail"],
                AutonomousReactionImportance.HIGH,
                AutonomousReactionValidity.IMMEDIATE
            )
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
        qiChatbot?.removeAllOnEndedListeners()
        chat?.removeAllOnStartedListeners()
        chatFuture?.requestCancellation()
        goToFuture?.requestCancellation()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        QiSDK.unregister(this, this)
    }
}