package com.neptune.player

import com.neptune.player.model.Features
import com.neptune.player.model.PlanFeature
import com.neptune.player.model.PlanTier
import com.neptune.player.model.Pricing
import com.neptune.player.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The subscription is local and unmonetised for now, but the *boundary* has to be honest from day
 * one: a feature is either gated by code or advertised as free, never both.
 */
class PlanTest {

    @Test fun thePlayerItselfIsNeverPaid() {
        listOf(PlanFeature.Playback, PlanFeature.Lyrics).forEach { feature ->
            assertTrue("${feature.title} must work on Free", Features.has(feature, PlanTier.FREE))
            assertTrue("${feature.title} must work on Plus", Features.has(feature, PlanTier.PLUS))
        }
    }

    @Test fun everyLockedFeatureIsAnnouncedAndEveryFreeOneIsNot() {
        val locked = Features.locked(PlanTier.FREE)
        assertTrue("a paywall with nothing to sell is broken", locked.isNotEmpty())
        locked.forEach { assertFalse("${it.title} is listed as locked but reads free", it.free) }
        // Plus gets everything: no feature may be unreachable by any tier.
        assertEquals("Plus must unlock every feature", PlanFeature.entries.size, Features.available(PlanTier.PLUS).size)
        assertEquals(0, Features.locked(PlanTier.PLUS).size)
    }

    @Test fun freeAndPaidPartitionTheFeatureList() {
        val free = Features.available(PlanTier.FREE)
        val locked = Features.locked(PlanTier.FREE)
        assertEquals("free + locked must cover the list exactly once", PlanFeature.entries.size, free.size + locked.size)
        assertTrue("nothing may appear in both", free.intersect(locked.toSet()).isEmpty())
    }

    @Test fun pricesAreInternallyConsistent() {
        assertEquals(Pricing.MONTHLY * 12, Pricing.YEARLY_AT_MONTHLY)
        assertTrue("the yearly plan must actually be cheaper", Pricing.YEARLY < Pricing.YEARLY_AT_MONTHLY)
        assertTrue(Pricing.yearlySaving > 0)
        assertTrue("a discount above 40% would read as a fake price", Pricing.yearlySavingPercent in 1..40)
        assertTrue(Pricing.monthlyLabel().contains(Pricing.MONTHLY.toString()))
    }

    @Test fun endingAPlanDropsOnlyWhatWasPaidFor() {
        val plus = Settings(
            customAccent = true, accentArgb = 0xFFFF0000.toInt(), profileName = "Саша",
            avatarPhoto = "avatar-1.img", avatarPhotoVersion = 4, avatarZoom = 2f,
            compact = true, thumbScale = 1.2f,
        )
        val free = plus.withoutPaidExtras()
        assertFalse("a personal accent is a Plus extra", free.customAccent)
        assertEquals("", free.avatarPhoto)
        assertEquals(0, free.avatarPhotoVersion)
        assertEquals(1f, free.avatarZoom, 0.0001f)
        // Everything that was never paid must survive untouched.
        assertEquals("Саша", free.profileName)
        assertTrue("list density is free", free.compact)
        assertEquals(1.2f, free.thumbScale, 0.0001f)
    }

    @Test fun avatarPhotoNameAndZoomAreSanitized() {
        val s = Settings(avatarPhoto = " ../../etc/passwd ", avatarZoom = 99f, avatarPhotoVersion = -5).validated()
        assertEquals("a path is rejected outright, not repaired", "", s.avatarPhoto)
        assertEquals(2.5f, s.avatarZoom, 0.0001f)
        assertEquals(0, s.avatarPhotoVersion)
        assertFalse("no separators may survive", s.avatarPhoto.contains("/"))
        // A backslash is a separator on some paths and must not survive either.
        assertEquals("", Settings(avatarPhoto = "..\\avatar-1.img").validated().avatarPhoto)
        // The name this app itself writes must pass through untouched.
        assertEquals("avatar-3.img", Settings(avatarPhoto = " avatar-3.img ").validated().avatarPhoto)
    }
}
