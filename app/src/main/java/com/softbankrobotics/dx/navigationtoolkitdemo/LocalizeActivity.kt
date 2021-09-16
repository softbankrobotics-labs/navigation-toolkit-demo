package com.softbankrobotics.dx.navigationtoolkitdemo

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.actuation.LocalizationStatus
import com.aldebaran.qi.sdk.`object`.actuation.Localize
import com.aldebaran.qi.sdk.`object`.conversation.*
import com.aldebaran.qi.sdk.`object`.streamablebuffer.StreamableBuffer
import com.aldebaran.qi.sdk.`object`.streamablebuffer.StreamableBufferFactory
import com.aldebaran.qi.sdk.builder.ChatBuilder
import com.aldebaran.qi.sdk.builder.LocalizeBuilder
import com.aldebaran.qi.sdk.builder.QiChatbotBuilder
import com.aldebaran.qi.sdk.builder.TopicBuilder
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer

class LocalizeActivity : RobotActivity(), RobotLifecycleCallbacks,
        Localize.OnStatusChangedListener, QiChatbot.OnEndedListener, Chat.OnStartedListener {

    companion object {
        private const val TAG = "LocalizeActivity"
        private const val FOLDER_NAME = "places"
        private const val MAP_FILE_NAME = "map.qimap"
    }

    private var localize: Localize? = null
    private var localizeFuture: Future<Void>? = null
    private var qiChatbot: QiChatbot? = null
    private lateinit var bookmarks: MutableMap<String, Bookmark>
    private var chat: Chat? = null
    private var chatFuture: Future<Void>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        QiSDK.register(this, this)
        setContentView(R.layout.activity_localize)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)
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

        // Read file
        val file = File(getExternalFilesDir(FOLDER_NAME), MAP_FILE_NAME)
        if (!file.exists()) {
            Log.e(TAG, "Error: map file doesn't exist")
            return
        }
        val buffer = StreamableBufferFactory.fromFile(file)

        // Build and run Localize action
        val mapping = qiContext.mapping
        val explorationMap = mapping.makeMap(buffer)
        localize = LocalizeBuilder.with(qiContext)
            .withMap(explorationMap)
            .build()
        localize!!.addOnStatusChangedListener(this)
        localizeFuture = localize!!.async().run()
        localizeFuture!!.thenConsume {
            if (!it.isSuccess && !it.isCancelled && it.errorMessage != "Focus not owned") {
                Log.i(TAG, "Localization failed")
                val intent = Intent(this, StartActivity::class.java)
                intent.putExtra("localizeFailed", true)
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onStarted() {
        Log.i(TAG, "Chat started")
        qiChatbot!!.async().goToBookmark(
                bookmarks["localized"],
                AutonomousReactionImportance.HIGH,
                AutonomousReactionValidity.IMMEDIATE
        )
    }

    override fun onEnded(endReason: String) {
        Log.i(TAG, "Chat done, reason: $endReason")
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun StreamableBufferFactory.fromFile(file: File): StreamableBuffer {
        return fromFunction(file.length()) { offset, size ->
            RandomAccessFile(file, "r").use {
                val byteArray = ByteArray(size.toInt())
                it.seek(offset)
                it.read(byteArray)
                ByteBuffer.wrap(byteArray)
            }
        }
    }

    override fun onStatusChanged(status: LocalizationStatus) {
        Log.i(TAG, "Localization status changed: ${status.name}")
        if (status == LocalizationStatus.LOCALIZED) {
            Log.i(TAG, "Localization done")
            localizeFuture!!.requestCancellation()
            chatFuture = chat!!.async().run()
        }
    }

    override fun onRobotFocusRefused(reason: String) {
        Log.e(TAG, "Robot focus refused: $reason")
    }

    override fun onRobotFocusLost() {
        Log.i(TAG, "Robot focus lost")
        localizeFuture?.cancel(true)
        qiChatbot?.removeAllOnBookmarkReachedListeners()
        chat?.removeAllOnStartedListeners()
        localize?.removeAllOnStatusChangedListeners()
        chatFuture?.requestCancellation()
        localizeFuture?.requestCancellation()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        QiSDK.unregister(this, this)
    }
}