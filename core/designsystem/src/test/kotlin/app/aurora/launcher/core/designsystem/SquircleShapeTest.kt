package app.aurora.launcher.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SquircleShapeTest {
    @Test
    fun `computeSuperellipsePoints produces symmetric bounded vertices matching target dimensions`() {
        val pts = SquircleShape.computeSuperellipsePoints(width = 120f, height = 120f, samples = 72)
        assertEquals(72, pts.size)
        val minX = pts.minOf { it.first }
        val maxX = pts.maxOf { it.first }
        val minY = pts.minOf { it.second }
        val maxY = pts.maxOf { it.second }
        assertEquals(0f, minX, 0.01f)
        assertEquals(120f, maxX, 0.01f)
        assertEquals(0f, minY, 0.01f)
        assertEquals(120f, maxY, 0.01f)
        // Diagonal corner at 45 degrees sits strictly inside the square corner (0,0)-(120,120) and outside inscribed circle
        val quarterTurn = pts[9] // 45 degrees (theta = pi/4)
        assertTrue(quarterTurn.first > 102f && quarterTurn.first < 116f)
    }
}
