package com.softwareidiot.soundsentinel.logic

import org.junit.Assert.*
import org.junit.Test

class BackgroundSettingsTargetsTest {
    @Test fun commonManufacturersGetTheirOwnCandidates() {
        for (maker in listOf("realme", "oppo", "oneplus", "xiaomi", "redmi", "poco", "huawei", "honor", "vivo", "iqoo", "asus")) {
            assertTrue(maker, BackgroundSettingsTargets.forManufacturer(maker).isNotEmpty())
        }
    }
    @Test fun unknownAndStockDevicesUseStandardAndroidFallback() {
        for (maker in listOf("Google", "Samsung", "Motorola", "Unknown")) assertTrue(BackgroundSettingsTargets.forManufacturer(maker).isEmpty())
    }
    @Test fun manufacturerMatchingIgnoresCaseAndWhitespace() {
        assertEquals(BackgroundSettingsTargets.forManufacturer("realme"), BackgroundSettingsTargets.forManufacturer(" REALME "))
    }
}
