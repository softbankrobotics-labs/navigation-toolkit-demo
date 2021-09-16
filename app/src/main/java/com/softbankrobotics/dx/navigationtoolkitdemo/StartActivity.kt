package com.softbankrobotics.dx.navigationtoolkitdemo

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import kotlinx.android.synthetic.main.activity_start.*
import java.io.File

class StartActivity : RobotActivity(), RobotLifecycleCallbacks,
    Chat.OnStartedListener, QiChatbot.OnEndedListener, QiChatbot.OnBookmarkReachedListener {

    companion object {
        private const val TAG = "StartActivity"
        private const val RECHARGE_PERMISSION = "com.softbankrobotics.permission.AUTO_RECHARGE"
        private const val MULTIPLE_PERMISSIONS = 2
        private const val FOLDER_NAME = "places"
        private const val MAP_FILE_NAME = "map.qimap"
    }

    private lateinit var permissionsNeededWarningDialog: AlertDialog
    private var mapFileExists = false
    private var localizeFailed = false
    private var menuMustBeDisplayed = false
    private var qiChatbot: QiChatbot? = null
    private lateinit var bookmarks: MutableMap<String, Bookmark>
    private var chat: Chat? = null
    private var chatFuture: Future<Void>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)

        homeButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle(R.string.permissions_needed_warning_title)
        alertDialogBuilder.setMessage(R.string.permissions_needed_warning_message)
        alertDialogBuilder.setPositiveButton("OK") { dialogInterface, _ ->
            dialogInterface.dismiss()
            checkPermissions()
        }
        alertDialogBuilder.setNegativeButton(getString(R.string.quit_app)) { _, _ ->
            finish()
        }
        permissionsNeededWarningDialog = alertDialogBuilder.create()

        localizeFailed = intent.getBooleanExtra("localizeFailed", false)
        // If we displayed this screen because we couldn't localize, don't do the rest
        if (localizeFailed) {
            mappingMenuTextView.text = getString(R.string.cant_localize)
            menuMustBeDisplayed = true
            QiSDK.register(this, this)
            return
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        Log.i(TAG, "Checking permissions")
        if (ContextCompat.checkSelfPermission(
                this,
                RECHARGE_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            checkMapFile()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, RECHARGE_PERMISSION),
                MULTIPLE_PERMISSIONS
            )
        }
    }

    /**
     * As WRITE_EXTERNAL_STORAGE permission is mandatory to save a map,
     * the application will be closed if the user denies this permission.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissionsList: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            MULTIPLE_PERMISSIONS -> {
                if (grantResults.isNotEmpty()) {
                    var permissionsResults = ""
                    for ((i, per) in permissionsList.withIndex()) {
                        permissionsResults += if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                            "\n$per : PERMISSION_DENIED"
                        } else "\n$per : PERMISSION_GRANTED"
                    }
                    Log.d(TAG, "onRequestPermissionsResult: $permissionsResults")
                }
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    checkMapFile()
                } else {
                    permissionsNeededWarningDialog.show()
                }
            }
        }
    }

    private fun checkMapFile() {
        Log.i(TAG, "Checking map file")
        val file = File(getExternalFilesDir(FOLDER_NAME), MAP_FILE_NAME)
        Log.d(TAG, "mapFileExists=${file.exists()}")
        if (file.exists()) {
            mapFileExists = true
        } else {
            menuMustBeDisplayed = true
        }
        QiSDK.register(this, this)
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.i(TAG, "Robot focus gained")

        // Check if the robot is docked
        Utils.checkRobotIsDocked(this, qiContext)

        // Build Chat
        val topic = TopicBuilder.with(qiContext).withResource(R.raw.start).build()
        bookmarks = topic.bookmarks

        qiChatbot = QiChatbotBuilder.with(qiContext).withTopic(topic).build()
        qiChatbot!!.addOnBookmarkReachedListener(this)
        qiChatbot!!.addOnEndedListener(this)

        chat = ChatBuilder.with(qiContext).withChatbot(qiChatbot).build()
        chat!!.addOnStartedListener(this)
        chatFuture = chat!!.async().run()

        if (menuMustBeDisplayed) {
            runOnUiThread {
                mappingMenuTextView.visibility = View.VISIBLE
                mappingMenuContainer.visibility = View.VISIBLE
            }

            // Create the list of the cards to be displayed
            val menuItemList: ArrayList<MenuItemData> = ArrayList()
            menuItemList.add(
                MenuItemData(
                    getString(R.string.yes),
                    R.drawable.create_map,
                    "display_mapping_menu"
                )
            )
            menuItemList.add(
                MenuItemData(
                    getString(R.string.no),
                    R.drawable.dont_create_map,
                    "no"
                )
            )

            // Add the DynamicConversationMenuFragment
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            val mappingMenuFragment = DynamicConversationMenuFragment(
                menuItemList = menuItemList,
                topic = topic,
                qiChatbot = qiChatbot,
            )
            fragmentTransaction.replace(R.id.mappingMenuContainer, mappingMenuFragment)
            fragmentTransaction.commit()
        }
    }

    override fun onStarted() {
        Log.i(TAG, "Chat started")
        when {
            mapFileExists -> {
                qiChatbot!!.async().goToBookmark(
                    bookmarks["map"],
                    AutonomousReactionImportance.HIGH,
                    AutonomousReactionValidity.IMMEDIATE
                )
            }
            localizeFailed -> {
                qiChatbot!!.async().goToBookmark(
                    bookmarks["localize_failed"],
                    AutonomousReactionImportance.HIGH,
                    AutonomousReactionValidity.IMMEDIATE
                )
            }
            else -> {
                qiChatbot!!.async().goToBookmark(
                    bookmarks["no_map"],
                    AutonomousReactionImportance.HIGH,
                    AutonomousReactionValidity.IMMEDIATE
                )
            }
        }
    }

    override fun onBookmarkReached(bookmark: Bookmark) {
        Log.i(TAG, "Bookmark reached: ${bookmark.name}")
        if (bookmark.name == "display_mapping_menu") {
            startActivity(Intent(this, MappingMenuActivity::class.java))
            finish()
        } else if (bookmark.name == "start_localize") {
            startActivity(Intent(this, LocalizeActivity::class.java))
            finish()
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
        QiSDK.unregister(this, this)
    }
}