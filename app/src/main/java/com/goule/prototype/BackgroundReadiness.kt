package com.goule.prototype

// OEM background controls may not update Android's battery optimization allowlist.
internal fun backgroundReady(batteryExempt: Boolean, restricted: Boolean, userConfirmed: Boolean): Boolean =
    !restricted && (batteryExempt || userConfirmed)
