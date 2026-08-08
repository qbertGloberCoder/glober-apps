package me.qbert.globewrapping.blend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LinearFalloffConfidenceWeightTest {

    private final LinearFalloffConfidenceWeight weightFunction = new LinearFalloffConfidenceWeight();

    @Test
    void fullConfidenceAtNadir() {
        assertEquals(1.0, weightFunction.weight(0.0, Math.toRadians(80.0)), 1e-9);
    }

    @Test
    void zeroConfidenceAtTheLimb() {
        double thetaMax = Math.toRadians(80.0);
        assertEquals(0.0, weightFunction.weight(thetaMax, thetaMax), 1e-9);
    }

    @Test
    void halfwayIsHalfConfidence() {
        double thetaMax = Math.toRadians(80.0);
        assertEquals(0.5, weightFunction.weight(thetaMax / 2.0, thetaMax), 1e-9);
    }

    @Test
    void beyondTheLimbClampsToZeroRatherThanGoingNegative() {
        double thetaMax = Math.toRadians(80.0);
        assertEquals(0.0, weightFunction.weight(thetaMax * 1.5, thetaMax), 1e-9);
    }

    @Test
    void rejectsNonPositiveMaxAngle() {
        assertThrows(IllegalArgumentException.class, () -> weightFunction.weight(0.1, 0.0));
    }
}
