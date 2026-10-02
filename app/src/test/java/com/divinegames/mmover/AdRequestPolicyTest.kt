package com.divinegames.mmover

import org.junit.Assert.*
import org.junit.Test

class AdRequestPolicyTest {
    @Test fun requestsWaitForConsentAndRespectUmpDenial() {
        val policy = AdRequestPolicy()
        assertFalse(policy.isAllowed(false, true))
        policy.consentReady = true
        assertFalse(policy.isAllowed(false, false))
        assertTrue(policy.isAllowed(false, true))
    }

    @Test fun privacyChangeDiscardsOldResultsEvenWhenAdsRemainAllowed() {
        val policy = AdRequestPolicy().apply { consentReady = true }
        val oldRequest = policy.generation
        policy.changingPrivacy = true
        policy.invalidate()
        assertFalse(policy.isAllowed(false, true))
        policy.changingPrivacy = false
        assertFalse(policy.acceptsResult(oldRequest, false, true))
        assertTrue(policy.acceptsResult(policy.generation, false, true))
    }

    @Test fun revocationBlocksRetriesAndPendingResults() {
        val policy = AdRequestPolicy().apply { consentReady = true }
        val request = policy.generation
        assertTrue(policy.isAllowed(false, true))
        assertFalse(policy.isAllowed(false, false))
        assertFalse(policy.acceptsResult(request, false, false))
    }

    @Test fun destroyedScreenCannotAcceptAdsAfterConsentChanges() {
        val policy = AdRequestPolicy().apply { consentReady = true }
        val request = policy.generation
        policy.close()
        policy.consentReady = true
        assertFalse(policy.isAllowed(false, true))
        assertFalse(policy.acceptsResult(request, false, true))
        assertFalse(policy.isAllowed(true, false))
    }

    @Test fun yandexBranchDoesNotDependOnUninitializedUmp() {
        val policy = AdRequestPolicy().apply { consentReady = true }
        assertTrue(policy.isAllowed(true, false))
        policy.changingPrivacy = true
        assertFalse(policy.isAllowed(true, false))
    }
}
