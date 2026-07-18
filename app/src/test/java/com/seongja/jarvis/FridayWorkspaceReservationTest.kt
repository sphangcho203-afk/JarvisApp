package com.seongja.jarvis

import com.jarvis.core.device.FridayWorkspaceReservation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FridayWorkspaceReservationTest {
    @Test
    fun openYourEyesBypassesGenericAppLauncher() {
        assertTrue(
            FridayWorkspaceReservation.shouldBypassGenericDeviceRouter(
                "FRIDAY, open your eyes"
            )
        )
    }

    @Test
    fun privateDiaryAndMemoryVaultStayNative() {
        assertTrue(
            FridayWorkspaceReservation.shouldBypassGenericDeviceRouter(
                "open my private diary"
            )
        )
        assertTrue(
            FridayWorkspaceReservation.shouldBypassGenericDeviceRouter(
                "please open the memory vault"
            )
        )
    }

    @Test
    fun visualLabAndXCameraStayNative() {
        assertTrue(
            FridayWorkspaceReservation.shouldBypassGenericDeviceRouter(
                "open x-camera"
            )
        )
        assertTrue(
            FridayWorkspaceReservation.shouldBypassGenericDeviceRouter(
                "bring up visual lab"
            )
        )
    }

    @Test
    fun ordinaryAppsStillUseGenericLauncher() {
        assertFalse(
            FridayWorkspaceReservation.shouldBypassGenericDeviceRouter(
                "open Instagram"
            )
        )
        assertFalse(
            FridayWorkspaceReservation.shouldBypassGenericDeviceRouter(
                "open settings"
            )
        )
    }
}
