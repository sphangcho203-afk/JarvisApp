package com.seongja.jarvis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FridayDesignCommandParserTest {
    @Test
    fun changesToNamedInterfaces() {
        assertEquals(
            FridayDesignCommand.Activate(FridayDesignMode.QUANTUM),
            FridayDesignCommandParser.parse("Friday, activate quantum interface")
        )
        assertEquals(
            FridayDesignCommand.Activate(FridayDesignMode.STEALTH),
            FridayDesignCommandParser.parse("Switch the system design to stealth")
        )
        assertEquals(
            FridayDesignCommand.Activate(FridayDesignMode.SECURE),
            FridayDesignCommandParser.parse("Use the privacy hardened theme")
        )
    }

    @Test
    fun genericDesignRequestCyclesTheMatrix() {
        assertEquals(
            FridayDesignCommand.Cycle,
            FridayDesignCommandParser.parse("Change system design")
        )
    }

    @Test
    fun unrelatedCommandsAreIgnored() {
        assertNull(FridayDesignCommandParser.parse("Show me the weather in Tokyo"))
        assertNull(FridayDesignCommandParser.parse("Open my private diary"))
    }
}
