package com.aldebaran.qi.sdk.autonomousrecharge

import android.content.Context
import android.content.Intent

/**
 * Entry point for autonomous recharge-related APIs.
 */
object AutonomousRecharge {

    private const val DOCK_ACTION_NAME = "com.softbankrobotics.intent.action.AUTO_DOCK"
    private const val UNDOCK_ACTION_NAME = "com.softbankrobotics.intent.action.AUTO_UNDOCK"
    private const val RECALL_POD = "recall_pod"

    /**
     * Sends an intent to start the Autonomous Recharge Docking activity.
     * @param context the [Context].
     * @param recallPod a boolean to indicate whether to recall the previous pod location.
     *
     * @return none
     */
    @JvmStatic
    fun startDockingActivity(context: Context, recallPod: Boolean? = null) {
        val intent = Intent(DOCK_ACTION_NAME)
        recallPod?.let { intent.putExtra(RECALL_POD, recallPod) }
        context.startActivity(intent)
    }

    /**
     * Sends an intent to start the Autonomous Recharge Undocking activity.
     * @param context the [Context].
     *
     * @return none
     */
    @JvmStatic
    fun startUndockingActivity(context: Context) {
        val intent = Intent(UNDOCK_ACTION_NAME)
        context.startActivity(intent)
    }
}
