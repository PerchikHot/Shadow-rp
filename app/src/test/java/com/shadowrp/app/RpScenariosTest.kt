package com.shadowrp.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RpScenariosTest {
    @Test fun scenePresetsHaveValidDescriptions() {
        assertEquals(4, RpScenarios.templates.size)
        for (index in RpScenarios.templates.indices) {
            val (name, description) = RpScenarios.choose(index)
            assertTrue(name.isNotBlank())
            assertTrue(description.length in 61..4000)
        }
    }

    @Test fun invalidPresetIsRejected() {
        for (index in listOf(-1, 4, 99)) {
            try {
                RpScenarios.choose(index)
                throw AssertionError("Invalid scenario accepted")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
