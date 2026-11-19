package com.poseidon.ad.main.load

import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class ParameterVisibilityTest {

    @Test
    fun `sceneName field is volatile for callback visibility`() {
        val field = Parameter::class.java.getDeclaredField("sceneName")
        assertTrue(Modifier.isVolatile(field.modifiers))
    }
}
