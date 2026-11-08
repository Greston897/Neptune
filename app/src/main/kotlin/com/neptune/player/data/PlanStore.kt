package com.neptune.player.data

import android.content.Context
import com.neptune.player.model.PlanTier
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Remembers the subscription tier locally.
 *
 * There is no payment integration and no receipt: the purchase flow is a designed screen that ends
 * in "подписка активна" so the whole product can be evaluated, and this store is the single seam a
 * real billing client would replace. Nothing here pretends to be an entitlement server — it is
 * explicitly a local record, which is why it never blocks playback or imports.
 */
class PlanStore(context: Context) {
    private val prefs = context.getSharedPreferences("neptune.v3.plan", Context.MODE_PRIVATE)
    private val _tier = MutableStateFlow(parseTier(prefs.getString("tier", null)))
    val tier = _tier
    private val _annual = MutableStateFlow(prefs.getBoolean("annual", false))
    /** Which price the user picked last; the paywall opens on it. */
    val annual = _annual

    fun set(tier: PlanTier, annual: Boolean = _annual.value) {
        _tier.value = tier
        _annual.value = annual
        prefs.edit().putString("tier", tier.name).putBoolean("annual", annual).apply()
    }

    fun subscribe(annual: Boolean) = set(PlanTier.PLUS, annual)

    /**
     * Leaving the subscription also revokes what it unlocked in settings, so the app never keeps a
     * paid state visible after the plan ends.
     */
    fun cancel(settings: SettingsStore) {
        set(PlanTier.FREE)
        settings.edit { it.withoutPaidExtras() }
    }

    /** Unknown or corrupt stored values fall back to the free tier, never to a paid one. */
    private fun parseTier(raw: String?): PlanTier =
        PlanTier.entries.firstOrNull { it.name == raw } ?: PlanTier.FREE
}
