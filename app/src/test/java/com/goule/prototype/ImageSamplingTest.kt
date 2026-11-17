package com.goule.prototype

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageSamplingTest {
    @Test fun neverUndersamplesTheDestinationOrChangesAspectRatio() {
        assertEquals(2, imageSampleSize(719, 1600, 359, 1))
        assertEquals(1, imageSampleSize(719, 1600, 360, 1))
        assertEquals(2, imageSampleSize(1600, 1200, 800, 600))
        assertEquals(1, imageSampleSize(1600, 1200, 800, 601))
        assertEquals(1, imageSampleSize(846, 1859, 1080, 2374))
    }

    @Test fun zeroBoundsAndVeryLargeImagesDoNotOverflow() {
        assertEquals(1, imageSampleSize(0, 0, 0, 0))
        assertEquals(1 shl 30, imageSampleSize(Int.MAX_VALUE, Int.MAX_VALUE, 1, 1))
    }
}
