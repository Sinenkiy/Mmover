package com.divinegames.mmover

/** State shared by initial requests, retries and callbacks for one screen instance. */
internal class AdRequestPolicy {
    var consentReady = false
    var changingPrivacy = false
    var generation = 0
        private set
    private var closed = false

    fun isAllowed(isRuUser: Boolean, umpAllowsAds: Boolean): Boolean =
        !closed && consentReady && !changingPrivacy && (isRuUser || umpAllowsAds)

    fun acceptsResult(token: Int, isRuUser: Boolean, umpAllowsAds: Boolean): Boolean =
        token == generation && isAllowed(isRuUser, umpAllowsAds)

    fun invalidate() { generation++ }

    fun close() {
        closed = true
        invalidate()
    }
}
