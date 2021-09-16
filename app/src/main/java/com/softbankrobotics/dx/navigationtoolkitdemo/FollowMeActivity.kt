package com.softbankrobotics.dx.navigationtoolkitdemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
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
import com.softbankrobotics.dx.followme.FollowHuman
import kotlinx.android.synthetic.main.activity_follow_me.*

class FollowMeActivity : RobotActivity(), RobotLifecycleCallbacks, FollowHuman.FollowHumanListener,
    QiChatbot.OnBookmarkReachedListener, Chat.OnStartedListener, QiChatbot.OnEndedListener {

    companion object {
        private const val TAG = "FollowMeActivity"
    }

    private var comeHere = false
    private var followHuman: FollowHuman? = null
    private var qiChatbot: QiChatbot? = null
    private lateinit var bookmarks: MutableMap<String, Bookmark>
    private var chat: Chat? = null
    private var chatFuture: Future<Void>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        setContentView(R.layout.activity_follow_me)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)

        comeHere = intent.getBooleanExtra("comeHere", false)
        if (comeHere) {
            followMeImageView.visibility = View.GONE
            buttonStop.visibility = View.GONE
            comeHereImageView.visibility = View.VISIBLE
            followMeTextView.text = getString(R.string.coming_here)
            followMeTextView.bringToFront()
        }

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

        // Build and run Chat
        val topic = TopicBuilder.with(qiContext).withResource(R.raw.follow_me).build()
        bookmarks = topic.bookmarks

        qiChatbot = QiChatbotBuilder.with(qiContext).withTopic(topic).build()
        qiChatbot!!.addOnBookmarkReachedListener(this)
        qiChatbot!!.addOnEndedListener(this)

        chat = ChatBuilder.with(qiContext).withChatbot(qiChatbot).build()
        chat!!.listeningBodyLanguage = BodyLanguageOption.DISABLED
        chat!!.addOnStartedListener(this)
        chatFuture = chat!!.async().run()

        // Get the human to follow
        val humanToFollow = qiContext.humanAwareness.humansAround[0]
        followHuman = FollowHuman(qiContext, humanToFollow, this)
        followHuman!!.start()
    }

    override fun onFollowingHuman() {
        Log.i(TAG, "Following the human")
    }

    override fun onCantReachHuman() {
        Log.e(TAG, "Can't reach the human")
    }

    override fun onDistanceToHumanChanged(distance: Double) {
        Log.i(TAG, "Distance to the human: $distance meters")
        if (comeHere && distance < 1) {
            qiChatbot!!.async().goToBookmark(
                bookmarks["come_here_done"],
                AutonomousReactionImportance.HIGH,
                AutonomousReactionValidity.IMMEDIATE
            )
            followHuman!!.stop()
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
        if (bookmark.name == "stop") {
            followHuman!!.stop()
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
        qiChatbot?.removeAllOnBookmarkReachedListeners()
        qiChatbot?.removeAllOnEndedListeners()
        chat?.removeAllOnStartedListeners()
        chatFuture?.requestCancellation()
        followHuman?.stop()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        QiSDK.unregister(this, this)
    }
}