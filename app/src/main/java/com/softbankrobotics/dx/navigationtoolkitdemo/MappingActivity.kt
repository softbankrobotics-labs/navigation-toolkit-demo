package com.softbankrobotics.dx.navigationtoolkitdemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.ExplorationMap
import com.aldebaran.qi.sdk.`object`.actuation.LocalizationStatus
import com.aldebaran.qi.sdk.`object`.actuation.LocalizeAndMap
import com.aldebaran.qi.sdk.`object`.conversation.*
import com.aldebaran.qi.sdk.builder.ChatBuilder
import com.aldebaran.qi.sdk.builder.LocalizeAndMapBuilder
import com.aldebaran.qi.sdk.builder.QiChatbotBuilder
import com.aldebaran.qi.sdk.builder.TopicBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.aldebaran.qi.sdk.util.copyToStream
import kotlinx.android.synthetic.main.activity_mapping.*
import java.io.File

class MappingActivity : RobotActivity(), RobotLifecycleCallbacks,
    LocalizeAndMap.OnStatusChangedListener, Chat.OnStartedListener, QiChatbot.OnEndedListener {

    companion object {
        private const val TAG = "MappingActivity"
        private const val FOLDER_NAME = "places"
        private const val MAP_FILE_NAME = "map.qimap"
    }

    private var localizeAndMap: LocalizeAndMap? = null
    private var localizeAndMapFuture: Future<Void>? = null
    private var qiChatbot: QiChatbot? = null
    private lateinit var bookmarks: MutableMap<String, Bookmark>
    private var chat: Chat? = null
    private var chatFuture: Future<Void>? = null
    private var chatDone = false
    private var mapSaved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        setContentView(R.layout.activity_mapping)
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

        // Build Chat
        val topic = TopicBuilder.with(qiContext).withResource(R.raw.localize_and_map).build()
        bookmarks = topic.bookmarks

        qiChatbot = QiChatbotBuilder.with(qiContext).withTopic(topic).build()
        qiChatbot!!.addOnEndedListener(this)

        chat = ChatBuilder.with(qiContext).withChatbot(qiChatbot).build()
        chat!!.listeningBodyLanguage = BodyLanguageOption.DISABLED
        chat!!.addOnStartedListener(this)
        chatFuture = chat!!.async().run()

        // Build and run LocalizeAndMap action
        localizeAndMap = LocalizeAndMapBuilder.with(qiContext).build()
        localizeAndMap!!.addOnStatusChangedListener(this)
        localizeAndMapFuture = localizeAndMap!!.async().run()
        localizeAndMapFuture!!.thenConsume {
            if (!it.isSuccess && !it.isCancelled) {
                Log.i(TAG, "Localize and map failed")
                qiChatbot!!.async().goToBookmark(
                        bookmarks["localize_failed"],
                        AutonomousReactionImportance.HIGH,
                        AutonomousReactionValidity.IMMEDIATE
                )
            }
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

    override fun onEnded(endReason: String) {
        Log.i(TAG, "Chat done, reason: $endReason")
        chatDone = true
        if (!(endReason == "success" && !mapSaved)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onStatusChanged(status: LocalizationStatus) {
        Log.i(TAG, "Localize and map status changed: ${status.name}")
        if (status == LocalizationStatus.LOCALIZED) {
            Log.i(TAG, "Localize and map done")
            qiChatbot!!.async().goToBookmark(
                    bookmarks["localized"],
                    AutonomousReactionImportance.HIGH,
                    AutonomousReactionValidity.IMMEDIATE
            )
            localizeAndMapFuture!!.requestCancellation()
            saveMapInFile(localizeAndMap!!.dumpMap())
        }
    }

    private fun saveMapInFile(explorationMap: ExplorationMap) {
        Log.d(TAG, "saveMapInFile")
        val file = File(getExternalFilesDir(FOLDER_NAME), MAP_FILE_NAME)
        val buffer = explorationMap.serializeAsStreamableBuffer()
        file.outputStream().use { buffer.copyToStream(it) }
        mapSaved = true
        if (chatDone) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onRobotFocusRefused(reason: String) {
        Log.e(TAG, "Robot focus refused: $reason")
    }

    override fun onRobotFocusLost() {
        Log.i(TAG, "Robot focus lost")
        localizeAndMap?.removeAllOnStatusChangedListeners()
        chat?.removeAllOnStartedListeners()
        qiChatbot?.removeAllOnEndedListeners()
        chatFuture?.requestCancellation()
        localizeAndMapFuture?.requestCancellation()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        QiSDK.unregister(this, this)
    }
}