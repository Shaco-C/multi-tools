package com.pockettoolbox.feature.fitness

import org.junit.Assert.*
import org.junit.Test

class FitnessAvailabilityTest {
    @Test fun sourceLibraryIsShippedWithoutClaimingProfessionalReview() {
        assertTrue("The released app must expose the fitness entry", BuildConfig.FITNESS_RELEASE_ENABLED)
        assertTrue(FitnessFeatureDependencies.isEnabled)
        assertFalse("Product release does not approve exercise teaching", BuildConfig.FITNESS_CONTENT_REVIEWED)
    }
}
