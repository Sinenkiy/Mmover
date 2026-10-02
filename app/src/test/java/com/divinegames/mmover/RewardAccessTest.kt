package com.divinegames.mmover

import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class RewardAccessTest {
    private class Store : RewardAccessStore {
        var record = RewardAccessRecord()
        var writes = 0
        override fun read() = record
        override fun write(record: RewardAccessRecord) { this.record = record; writes++ }
    }
    private val store = Store()
    private var time = ZonedDateTime.parse("2026-09-23T23:59:59+02:00[Europe/Berlin]")
    private val access = RewardAccess(store) { time }

    @Test fun noRewardCannotEnableMode() {
        assertFalse(access.setEnabled(true))
        assertFalse(access.isEnabled())
    }

    @Test fun grantSurvivesRecreationAndToggleOffOn() {
        access.grant()
        assertEquals("2026-09-23", store.record.date)
        assertTrue(RewardAccess(store) { time }.isEnabled())
        assertFalse(access.setEnabled(false))
        assertTrue(access.setEnabled(true))
    }

    @Test fun midnightExpiresAndPersistsDisabledMode() {
        access.grant()
        time = time.plusSeconds(1)
        assertFalse(access.isEnabled())
        assertFalse(store.record.enabled)
        assertFalse(access.setEnabled(true))
    }

    @Test fun expiredRewardRequiresNewGrant() {
        access.grant()
        time = time.plusDays(1)
        assertFalse(access.isEnabled())
        access.grant()
        assertTrue(access.isEnabled())
        assertEquals("2026-09-24", store.record.date)
    }

    @Test fun invalidMissingAndFutureDatesNeverEnableAccess() {
        for (date in listOf("", "broken", "2026-09-24")) {
            store.record = RewardAccessRecord(date, true)
            assertFalse(access.isEnabled())
            assertFalse(store.record.enabled)
        }
    }

    @Test fun changingLocaleDoesNotChangeStoredDate() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            access.grant()
            assertEquals("2026-09-23", store.record.date)
            Locale.setDefault(Locale.GERMAN)
            assertTrue(access.isEnabled())
        } finally { Locale.setDefault(previous) }
    }

    @Test fun timezoneChangeUsesCurrentLocalDay() {
        access.grant()
        time = time.withZoneSameInstant(java.time.ZoneId.of("Asia/Tokyo"))
        assertFalse(access.isEnabled())
    }

    @Test fun refreshTargetsMidnightAndBoundsWallClockRechecks() {
        assertEquals(1000L, access.nextRefreshDelayMs())
        time = time.minusHours(5)
        assertEquals(60000L, access.nextRefreshDelayMs())
        time = ZonedDateTime.parse("2026-03-29T23:59:59+02:00[Europe/Berlin]")
        assertEquals(1000L, access.nextRefreshDelayMs())
    }

    @Test fun sessionGrantsAtMostOnceEvenIfDateChanges() {
        val session = RewardSession(access::grant)
        session.reward()
        time = time.plusDays(1)
        session.reward()
        assertEquals(1, store.writes)
        assertEquals("2026-09-23", store.record.date)
    }

    @Test fun dismissFailureOrDisposalBeforeRewardGivesNoAccess() {
        val session = RewardSession(access::grant)
        session.close()
        session.reward()
        assertEquals(0, store.writes)
        assertFalse(access.isEnabled())
    }

    @Test fun dismissalAfterRewardDoesNotRevokeEarnedAccess() {
        val session = RewardSession(access::grant)
        session.reward()
        session.close()
        session.reward()
        assertEquals(1, store.writes)
        assertTrue(access.isEnabled())
    }

    @Test fun visibleScreenExpiresAtMidnightWithoutRecreation() {
        val scheduler = Scheduler()
        val states = mutableListOf<Boolean>()
        val controller = RewardAccessController(access, scheduler, states::add)
        controller.grant()
        controller.onResume()
        assertEquals(listOf(true), states)
        assertEquals(1000L, scheduler.delay)
        time = time.plusSeconds(1)
        scheduler.action!!()
        assertEquals(listOf(true, false), states)
        assertFalse(store.record.enabled)
    }

    @Test fun pauseAndCloseDiscardQueuedRefreshAndDoNotRestart() {
        val scheduler = Scheduler()
        val states = mutableListOf<Boolean>()
        val controller = RewardAccessController(access, scheduler, states::add)
        controller.onResume()
        val stale = scheduler.action!!
        controller.onPause()
        assertTrue(scheduler.cancelled)
        stale()
        assertEquals(1, states.size)
        controller.onResume()
        val beforeClose = states.size
        val staleAfterClose = scheduler.action!!
        controller.close()
        staleAfterClose()
        controller.grant()
        controller.onResume()
        assertEquals(beforeClose, states.size)
        assertEquals(0, store.writes)
    }

    @Test fun rewardWhileAdCoversScreenPersistsAndRendersOnResume() {
        val states = mutableListOf<Boolean>()
        val controller = RewardAccessController(access, Scheduler(), states::add)
        controller.onResume()
        controller.onPause()
        controller.grant()
        assertEquals(listOf(false), states)
        assertTrue(store.record.enabled)
        controller.onResume()
        assertEquals(listOf(false, true), states)
    }

    private class Scheduler : TaskScheduler {
        override val nowMs = 0L
        var delay = 0L
        var action: (() -> Unit)? = null
        var cancelled = false
        override fun schedule(delayMs: Long, action: () -> Unit): () -> Unit {
            delay = delayMs
            this.action = action
            cancelled = false
            return { cancelled = true }
        }
    }
}
