package com.monzatti.app

import com.monzatti.app.domain.model.Measurements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementsTest {
    @Test
    fun suggestedSizeFollowsBust() {
        assertNull(Measurements(heightCm = 170.0).suggestedSize)
        assertEquals("XS", Measurements(bustCm = 80.0).suggestedSize)
        assertEquals("S", Measurements(bustCm = 84.0).suggestedSize)
        assertEquals("M", Measurements(bustCm = 90.0).suggestedSize)
        assertEquals("L", Measurements(bustCm = 101.9).suggestedSize)
        assertEquals("XL", Measurements(bustCm = 110.0).suggestedSize)
    }
}
