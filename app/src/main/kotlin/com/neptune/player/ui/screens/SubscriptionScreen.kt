package com.neptune.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neptune.player.model.Features
import com.neptune.player.model.PlanFeature
import com.neptune.player.model.PlanTier
import com.neptune.player.model.Pricing
import com.neptune.player.state.NeptuneState
import com.neptune.player.ui.*

/**
 * Neptune Plus — the paywall and the plan manager in one screen.
 *
 * Design rules this screen follows, because a paywall is where apps usually lose their manners:
 *
 *  * **It states what is already free, first.** Playback, import, playlists and karaoke are not
 *    trials, and the copy says so before anything is offered for sale. Nobody should have to pay to
 *    reach their own files.
 *  * **One honest promise per row.** Every line maps to a [PlanFeature] that the code actually
 *    gates through `Features.has`, so nothing can be advertised and then not enforced (or vice versa).
 *  * **No urgency theatre.** No countdown, no crossed-out fake price, no pre-ticked checkbox. Two
 *    real prices, one tap, and a cancel path that is one row further down.
 *  * **No backend, said plainly.** The purchase is stored on the device; there is no receipt server
 *    yet. Pretending otherwise would be the one dishonest thing on the screen.
 */
@Composable
fun SubscriptionScreen(state: NeptuneState, onClose: () -> Unit) {
    val tier by state.plan.collectAsStateWithLifecycle()
    val annual by state.annualPlan.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf(false) }
    // Read live, not remembered: a refused save can set the reason while this page is already open,
    // and the header must show the current one rather than a snapshot from the first composition.
    val focused = state.paywall
    val isPlus = tier == PlanTier.PLUS
    // The page closes itself when the plan it was opened for is granted. `state.subscribe` already
    // clears the request; this covers the other path into Plus (a restored purchase) without
    // leaving a manager page open on top of the library.
    LaunchedEffect(isPlus) { if (isPlus) state.closePaywall() }

    // Opaque, not translucent. Everything behind this page is another screen — often itself a
    // blurred panel — and showing it through a glass sheet produced a smeared double exposure.
    // The scrim stays for the one frame a fade takes, then the page is simply itself.
    Box(Modifier.fillMaxSize().background(Ne.Void)) {
    LazyColumn(
        // Tagged so a test can scroll to a control that lives below the fold instead of assuming
        // it fits on the viewport (a LazyColumn does not compose off-screen items at all).
        Modifier.fillMaxSize().statusBarsPadding().testTag("subscriptionList").background(Ne.Void),
        contentPadding = PaddingValues(
            start = NeSpace.screen, end = NeSpace.screen, top = NeSpace.sm,
            // The dock (nav bar + mini player) is hidden while this page is up, so the ambient inset
            // would leave a dead strip of empty space under the last row. Keep only the system bars.
            bottom = NeSpace.xxl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = NeRow.header), verticalAlignment = Alignment.CenterVertically) {
                NeIconButton(NeIcon.Close, "Закрыть", onClose)
                NeText(
                    "ПОДПИСКА", Modifier.weight(1f).padding(start = NeSpace.sm),
                    size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Faint,
                    tracking = 1.2.sp, weight = FontWeight.Bold,
                )
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = NeSpace.md, bottom = NeSpace.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Deliberately still: a breathing mark over a blurred panel is the most expensive
                // frame on this screen (software GPU on low-end devices) and buys nothing — the page
                // is read once, then acted on.
                NeptuneMark(Modifier.size(56.dp), accent())
                Spacer(Modifier.height(NeSpace.md))
                NeText(
                    if (isPlus) "Neptune Plus активен" else "Neptune Plus",
                    size = NeType.headline, lineHeight = NeType.headlineLine, weight = FontWeight.ExtraBold,
                    tracking = (-0.5).sp, align = TextAlign.Center,
                )
                Spacer(Modifier.height(NeSpace.xs))
                NeText(
                    if (isPlus) state.planPrice() else PlanTier.PLUS.tagline,
                    size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim,
                    align = TextAlign.Center, maxLines = 2,
                )
            }
        }
        // The reason the paywall opened, when a specific control sent the user here (null when the
        // user simply pressed "Neptune Plus", in which case no excuse card is shown). The second line
        // is the promise that matters after a refused save: the thing the user already did (a typed
        // text, a picked photo) is kept and applied the moment Plus is on, so nothing is lost.
        if (focused != null && !isPlus) {
            item {
                GlassPanel(Modifier.fillMaxWidth().padding(bottom = NeSpace.md)) {
                    Row(Modifier.fillMaxWidth().padding(NeSpace.md), verticalAlignment = Alignment.CenterVertically) {
                        NeIconView(NeIcon.Sparkle, NeIconSize.lg, accent())
                        Spacer(Modifier.width(NeSpace.md))
                        Column(Modifier.weight(1f)) {
                            NeText("«${focused.title}» — в Plus", size = NeType.label, lineHeight = NeType.labelLine, weight = FontWeight.Bold)
                            Spacer(Modifier.height(NeSpace.xxs))
                            NeText(focused.description, size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 2)
                            Spacer(Modifier.height(NeSpace.xxs))
                            NeText(
                                "Всё, что вы уже сделали, применится сразу после подключения.",
                                size = NeType.micro, lineHeight = NeType.microLine, color = Ne.Faint, maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
        item { SettingsGroup("ВСЕГДА БЕСПЛАТНО") {
            Column(Modifier.fillMaxWidth().padding(vertical = NeSpace.xs)) {
                Features.available(PlanTier.FREE).forEach { feature ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = NeRow.panelInset, vertical = NeSpace.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NeIconView(NeIcon.Check, NeIconSize.sm, Ne.Dim)
                        Spacer(Modifier.width(NeSpace.md))
                        NeText(feature.title, Modifier.weight(1f), size = NeType.label, lineHeight = NeType.labelLine, color = Ne.Text)
                    }
                }
            }
        } }
        if (Features.locked(PlanTier.FREE).isNotEmpty()) item { SettingsGroup("В NEPTUNE PLUS") {
            Column(Modifier.fillMaxWidth().padding(vertical = NeSpace.xs)) {
                Features.locked(PlanTier.FREE).forEach { feature ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = NeRow.panelInset, vertical = NeSpace.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NeIconView(NeIcon.Check, NeIconSize.sm, accent())
                        Spacer(Modifier.width(NeSpace.md))
                        Column(Modifier.weight(1f)) {
                            NeText(feature.title, size = NeType.label, lineHeight = NeType.labelLine, weight = FontWeight.SemiBold)
                            Spacer(Modifier.height(NeSpace.xxs))
                            NeText(feature.description, size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 2)
                        }
                    }
                }
            }
        } }
        if (!isPlus) {
            item {
                SettingsGroup("ЦЕНА") {
                    Column(Modifier.fillMaxWidth().padding(NeSpace.md), verticalArrangement = Arrangement.spacedBy(NeSpace.sm)) {
                        PriceChoice(
                            title = Pricing.monthlyLabel(), note = "Отмена в любой момент",
                            selected = !annual, onClick = { state.setAnnualPlan(false) },
                        )
                        PriceChoice(
                            title = Pricing.yearlyLabel(),
                            note = "Два месяца в подарок · ${Pricing.yearlyNote()}",
                            selected = annual, onClick = { state.setAnnualPlan(true) },
                            badge = "−${Pricing.yearlySavingPercent}%",
                        )
                    }
                }
            }
            item {
                NeButton(
                    if (annual) "Подключить за ${Pricing.yearlyLabel()}" else "Подключить за ${Pricing.monthlyLabel()}",
                    { state.subscribe(annual) },
                    Modifier.fillMaxWidth().padding(top = NeSpace.lg), NeIcon.Sparkle,
                )
            }
            item {
                NeText(
                    "Оплата пока не подключена: Neptune работает без сервера, поэтому подписка включается " +
                        "на этом устройстве и ни рубля не списывается. Когда появится оплата, здесь будет " +
                        "тот же экран с подтверждением у магазина.",
                    Modifier.fillMaxWidth().padding(top = NeSpace.lg),
                    size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Faint,
                    align = TextAlign.Center, maxLines = 5,
                )
            }
            // The way in must exist even while nothing is charged, otherwise the feature list on this
            // screen describes a state no tap can reach.
            item {
                NeButton(
                    "Открыть Plus сейчас", { state.subscribe(annual) },
                    Modifier.fillMaxWidth().padding(top = NeSpace.sm), NeIcon.Sparkle, primary = false,
                )
            }
        } else {
            item {
                SettingsGroup("ПОДПИСКА") {
                    SettingRow(
                        "Тариф", if (annual) "Год · ${Pricing.yearlyLabel()}" else "Месяц · ${Pricing.monthlyLabel()}",
                        NeIcon.Sparkle,
                    )
                    SettingRow("Отключить Plus", "Свой акцент и фото вернутся к базовым", NeIcon.Close) { confirmCancel = true }
                }
            }
            item {
                NeText(
                    "Подписка хранится на устройстве. После отключения музыка, плейлисты и любимое остаются — " +
                        "пропадает только то, что было в Plus.",
                    Modifier.fillMaxWidth().padding(top = NeSpace.lg),
                    size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Faint,
                    align = TextAlign.Center, maxLines = 5,
                )
            }
        }
    }
    }
    // Not the destructive dialog: cancelling a plan loses no music and no text, only the extras that
    // Plus itself added. Red "this cannot be undone" wording would be a lie here.
    if (confirmCancel) ConfirmDialog(
        title = "Отключить Neptune Plus?",
        text = "Свой акцентный цвет и фото профиля вернутся к базовым настройкам. Плейлисты, любимое и тексты останутся.",
        action = "Отключить",
        onDismiss = { confirmCancel = false },
    ) {
        confirmCancel = false
        state.cancelSubscription()
    }
}

/**
 * A price option.
 *
 * Selection is a rim plus a check, not a filled accent block: the annual option is already louder
 * than the monthly one through its badge, and two competing emphasis devices on a two-row list read
 * as a sale banner.
 */
@Composable
fun PriceChoice(
    title: String, note: String, selected: Boolean, onClick: () -> Unit, badge: String? = null,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHaptics.current
    Column(
        modifier.fillMaxWidth().heightIn(min = NeTouch.standard)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) accent().copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f))
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) accent() else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(16.dp),
            )
            .clickable { haptic(); onClick() }
            // The visible price is text; the description adds the state so TalkBack announces
            // "selected" without the row having to duplicate the label in a second node.
            .semantics {
                this.selected = selected
                contentDescription = "$title, ${if (selected) "выбрано" else "не выбрано"}"
            }
            .padding(horizontal = NeSpace.lg, vertical = NeSpace.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NeText(title, Modifier.weight(1f), size = NeType.body, lineHeight = NeType.bodyLine, weight = FontWeight.Bold)
            if (badge != null) {
                Box(
                    Modifier.clip(CircleShape).background(accent()).padding(horizontal = NeSpace.sm, vertical = 2.dp),
                ) { NeText(badge, size = NeType.micro, lineHeight = NeType.microLine, weight = FontWeight.Bold, color = Ne.Black) }
                Spacer(Modifier.width(NeSpace.sm))
            }
            if (selected) NeIconView(NeIcon.Check, NeIconSize.sm, accent())
                else Box(Modifier.size(NeIconSize.sm).clip(CircleShape).border(1.dp, Ne.Faint, CircleShape))
        }
        Spacer(Modifier.height(NeSpace.xxs))
        NeText(note, size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 2)
    }
}

/**
 * The compact plan card for Settings: current tier, what it means, and the way in (or out).
 *
 * Deliberately one row, not a second paywall: the profile already shows who you are, and the plan is
 * part of that identity, so it belongs next to the name rather than hidden two levels down.
 */
@Composable
fun PlanCard(state: NeptuneState, onOpen: () -> Unit) {
    val tier by state.plan.collectAsStateWithLifecycle()
    val isPlus = tier == PlanTier.PLUS
    GlassPanel(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            Modifier.fillMaxWidth().padding(NeRow.panelInset, NeSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(NeRow.leadingIcon), contentAlignment = Alignment.Center) {
                NeIconView(if (isPlus) NeIcon.Sparkle else NeIcon.Orbit, NeIconSize.lg, if (isPlus) accent() else Ne.Dim)
            }
            Spacer(Modifier.width(NeSpace.md))
            Column(Modifier.weight(1f)) {
                NeText(
                    if (isPlus) "Neptune Plus" else "Neptune Free",
                    size = NeType.body, lineHeight = NeType.bodyLine, weight = FontWeight.Bold,
                    color = if (isPlus) accent() else Ne.Text,
                )
                Spacer(Modifier.height(NeSpace.xxs))
                NeText(
                    if (isPlus) state.planPrice() else "Всё для прослушивания бесплатно",
                    size = NeType.caption, lineHeight = NeType.captionLine, color = Ne.Dim, maxLines = 1,
                )
            }
            NeText(
                if (isPlus) "Управлять" else "Открыть",
                size = NeType.caption, lineHeight = NeType.captionLine, color = accent(), weight = FontWeight.Bold,
            )
        }
    }
}
