package com.neptune.player.model

/**
 * What a listener gets for free, and what the optional subscription adds.
 *
 * Neptune has no backend and no payment gateway (yet), so the tier is pure local state: a plan is
 * something the app *remembers*, not something a server confirms. That is deliberate —
 * [PlanStore] can be swapped for a real entitlement check later without touching a single screen,
 * because every feature gate already asks [Features] instead of reading the tier directly.
 *
 * The important rule this file encodes: **nothing that makes the player usable is paid**. Playing
 * your own files, importing, liking, playlists, a queue and syncing lyrics to a track all work on
 * [FREE]. The subscription adds depth (saved lyrics, a sleep timer that fades, extra accent slots)
 * and cosmetics, never access to your own music.
 */
enum class PlanTier(val title: String, val price: String, val tagline: String) {
    FREE("Free", "0 ₽", "Всё, чтобы слушать свою музыку."),
    PLUS("Neptune Plus", "149 ₽ в месяц", "Глубже: тексты, ночной режим, свои акценты."),
}

/**
 * Monthly prices in whole roubles. One source of truth for the paywall copy and for the annual
 * total, so a price can never be printed twice with two different numbers.
 */
object Pricing {
    const val MONTHLY = 149
    const val YEARLY = 1490

    /** What a year at the monthly rate would cost — the reference the annual price beats. */
    const val YEARLY_AT_MONTHLY = MONTHLY * 12

    val yearlySaving: Int get() = YEARLY_AT_MONTHLY - YEARLY
    val yearlySavingPercent: Int get() = (yearlySaving * 100) / YEARLY_AT_MONTHLY

    fun monthlyLabel(): String = "$MONTHLY ₽ в месяц"
    fun yearlyLabel(): String = "$YEARLY ₽ в год"
    fun yearlyNote(): String = "это $yearlySaving ₽ меньше, чем $MONTHLY ₽ × 12"
}

/**
 * Every gated capability, declared once.
 *
 * Screens never compare tiers themselves — they ask `Features.has(PlanFeature.Download, tier)`, so
 * adding a feature means adding one row here and one call site, and the paywall copy stays in sync
 * with what is actually gated.
 */
enum class PlanFeature(val title: String, val description: String, val free: Boolean) {
    /** Import, scan, play, queue, like, playlists — the whole player. Free by definition. */
    Playback("Плеер целиком", "Импорт, сканирование, очередь, любимое и плейлисты без ограничений", true),
    Lyrics("Текст трека", "Лирика с таймингом, караоке и полноэкранный режим", true),
    SleepFade("Ночной таймер", "Плавное затухание вместо резкой остановки", false),
    LyricsSave("Сохранённые тексты", "Свои тексты остаются в приложении и не пропадают", false),
    AccentPersonal("Свой акцент", "Любой оттенок вместо двенадцати готовых", false),
    AvatarPhoto("Своё фото профиля", "Аватар из галереи вместо фирменного знака", false),
    ProfileBadge("Значок Plus", "Отметка подписки рядом с именем", false),
}

/** Pure gate: one place to answer "can this tier use that?". */
object Features {
    fun has(feature: PlanFeature, tier: PlanTier): Boolean = feature.free || tier == PlanTier.PLUS

    /** The features a tier does not have, in declaration order — what a paywall lists. */
    fun locked(tier: PlanTier): List<PlanFeature> = PlanFeature.entries.filterNot { has(it, tier) }

    /** The features this tier does have, so a paywall can state what is already free. */
    fun available(tier: PlanTier): List<PlanFeature> = PlanFeature.entries.filter { has(it, tier) }
}
