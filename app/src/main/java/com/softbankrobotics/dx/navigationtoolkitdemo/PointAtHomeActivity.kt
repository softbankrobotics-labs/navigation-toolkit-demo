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
import com.softbankrobotics.pepperpointat.PointAtAnimator
import kotlinx.android.synthetic.main.activity_point_at_home.*
import kotlin.math.sqrt

class PointAtHomeActivity : RobotActivity(), RobotLifecycleCallbacks, Chat.OnStartedListener,
        QiChatbot.OnEndedListener {

    companion object {
        private const val TAG = "PointAtHomeActivity"
    }

    private var qiChatbot: QiChatbot? = null
    private lateinit var bookmarks: MutableMap<String, Bookmark>
    private var chat: Chat? = null
    private var chatFuture: Future<Void>? = null
    private var mapFrameTooClose = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        setContentView(R.layout.activity_point_at_home)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)

        buttonStop.setOnClickListener {
            qiChatbot?.async()?.goToBookmark(
                    bookmarks["stop"],
                    AutonomousReactionImportance.HIGH,
                    AutonomousReactionValidity.IMMEDIATE
            )
        }

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

        // Build Chat
        val topic = TopicBuilder.with(qiContext).withResource(R.raw.point_at_home).build()
        bookmarks = topic.bookmarks

        qiChatbot = QiChatbotBuilder.with(qiContext).withTopic(topic).build()
        qiChatbot!!.addOnEndedListener(this)

        chat = ChatBuilder.with(qiContext).withChatbot(qiChatbot).build()
        chat!!.addOnStartedListener(this)

        // Get the RobotFrame
        val robotFrame = qiContext.actuation.robotFrame()

        // Get the MapFrame
        val mapFrame = qiContext.mapping.mapFrame()

        //Check if the mapFrame is not too close
        val transformTime = mapFrame.computeTransform(robotFrame)
        val transform = transformTime.transform
        val translation = transform.translation
        val x = translation.x
        val y = translation.y
        val distance = sqrt(x * x + y * y)
        Log.d(TAG, "Distance between robot and mapFrame=$distance")

        if (distance < 1.0) {
            mapFrameTooClose = true
            chatFuture = chat!!.async().run()
            return
        }

        // If a PointAt action should be run, remove the ListeningBodyLanguage of the Chat
        chat!!.listeningBodyLanguage = BodyLanguageOption.DISABLED
        chatFuture = chat!!.async().run()

        // Build and run PointAt
        val pointAtAnimator = PointAtAnimator(qiContext)
        pointAtAnimator.pointAt(mapFrame)
    }

    override fun onStarted() {
        Log.i(TAG, "Chat started")
        if (mapFrameTooClose) {
            qiChatbot!!.async().goToBookmark(
                    bookmarks["map_frame_too_close"],
                    AutonomousReactionImportance.HIGH,
                    AutonomousReactionValidity.IMMEDIATE
            )
        } else {
            qiChatbot!!.async().goToBookmark(
                    bookmarks["point_at_home"],
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
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        QiSDK.unregister(this, this)
    }
}