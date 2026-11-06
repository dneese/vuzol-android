package com.roman.zemzeme.iconswitch

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

internal object IconSwitcher {

    private const val TAG = "IconSwitcher"
    const val ALIAS_COUNT = 100
    private const val PACKAGE = "app.vuzol.messenger"

    val NAMES = arrayOf(
        "Vuzol", "NetChat", "TalkBox", "MsgHub", "BuzzTalk",
        "PingMe", "ChatNow", "QuickMsg", "TalkLine", "MsgWave",
        "ChatZone", "HiTalk", "TxtFlow", "ConnectMe", "ChatBolt",
        "MsgPulse", "TalkSnap", "ChatLink", "FlowChat", "PingTalk",
        "MsgDash", "ChatVibe", "TalkSync", "MsgNest", "ChatBee",
        "QuickTalk", "MsgLoop", "ChatGrid", "TalkNet", "MsgDrop",
        "ChatPing", "TalkWave", "MsgBlip", "ChatRush", "TalkMesh",
        "MsgLink", "ChatSpark", "TalkZap", "MsgBuzz", "ChatDrift",
        "TalkPad", "MsgVault", "ChatOrb", "TalkDeck", "MsgBeam",
        "ChatNode", "TalkBase", "MsgCore", "ChatFlux", "ChatEdge",
        "MsgRing", "ChatPlex", "TalkStar", "MsgHive", "ChatBit",
        "TalkNote", "MsgPort", "ChatWeave", "TalkCast", "MsgPeer",
        "ChatBridge", "TalkFusion", "MsgSwift", "ChatPulsar", "TalkRelay",
        "MsgHop", "ChatTrace", "TalkPath", "MsgRoute", "ChatAxis",
        "TalkGo", "MsgLift", "ChatDot", "ChatAir", "MsgWire",
        "ChatJet", "TalkBit", "MsgNova", "ChatRipple", "MsgMint",
        "MsgCloud", "ChatBreeze", "TalkEcho", "MsgWarp", "ChatStream",
        "TalkQuick", "MsgBolt", "ChatSphere", "ChatSpace", "ChatTap",
        "ChatFlash", "ChatBlaze", "MsgGlow", "ChatPulse", "MsgVolt",
        "MsgSnap", "ChatDial", "TalkRush", "ChatZone", "TalkWave"
    )

    data class SwitchResult(val index: Int, val name: String)

    /** Pick a random next alias (different from current) without applying it. */
    fun pickNext(context: Context): SwitchResult {
        val current = IconSwitchPreferences(context).currentAliasIndex
        var next: Int
        do {
            next = kotlin.random.Random.nextInt(1, ALIAS_COUNT + 1)
        } while (next == current)
        return SwitchResult(index = next, name = NAMES[next - 1])
    }

    /**
     * Apply a previously picked alias.
     *
     * The old alias is not disabled right away: the app usually runs through
     * that alias and Android kills the process when the running component
     * gets disabled, which looked like a crash. The old alias is retired on
     * a later start, when nothing runs through it anymore.
     */
    fun applySwitch(context: Context, nextIndex: Int): Boolean {
        val prefs = IconSwitchPreferences(context)
        val current = prefs.currentAliasIndex
        val pm = context.packageManager

        if (!setAliasEnabled(pm, nextIndex, true)) return false

        prefs.currentAliasIndex = nextIndex
        if (current != nextIndex) {
            prefs.pendingDisableIndex = current
        }
        return true
    }

    /** Retire the previous alias once the app does not run through it. */
    fun retirePendingAlias(context: Context, runningClassName: String?) {
        val prefs = IconSwitchPreferences(context)
        val pending = prefs.pendingDisableIndex
        if (pending <= 0 || pending == prefs.currentAliasIndex) {
            prefs.pendingDisableIndex = 0
            return
        }
        val pendingClass = aliasClassName(pending)
        if (runningClassName == pendingClass) return
        if (setAliasEnabled(context.packageManager, pending, false)) {
            prefs.pendingDisableIndex = 0
        }
    }

    /** Pick and apply in one step. */
    fun switchNow(context: Context): SwitchResult {
        val result = pickNext(context)
        applySwitch(context, result.index)
        return result
    }

    private fun aliasClassName(index: Int): String = "$PACKAGE.iconswitch.Alias_$index"

    private fun setAliasEnabled(pm: PackageManager, index: Int, enabled: Boolean): Boolean {
        if (index !in 1..ALIAS_COUNT) return false
        val component = ComponentName(PACKAGE, aliasClassName(index))
        return try {
            // The platform throws when the component does not exist
            pm.getActivityInfo(component, 0)
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not ${if (enabled) "enable" else "disable"} $component: ${e.message}")
            false
        }
    }
}
