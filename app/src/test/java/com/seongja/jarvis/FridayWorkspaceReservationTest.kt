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
        assertTrue(
            FridayWorkspaceReservation.shouldBypassGenericDeviceRouter(
                "Hey FRIDAY, could you please open your eyes and tell me what you see"
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
                "open my private diary and find yesterday"
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
                "open x camera rear lens"
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
        assertFalse(
            FridayWorkspaceReservation.shouldBypassGenericDeviceRouter(
                "open eye doctor"
            )
        )
    }
}
