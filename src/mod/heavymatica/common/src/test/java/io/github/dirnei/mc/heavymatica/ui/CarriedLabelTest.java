package io.github.dirnei.mc.heavymatica.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CarriedLabelTest {

    @Test
    void noneCarried() {
        assertEquals(CarriedLabel.Kind.NONE, CarriedLabel.of(0, 58).kind());
    }

    @Test
    void someButNotEnough() {
        CarriedLabel label = CarriedLabel.of(22, 58);
        assertEquals(CarriedLabel.Kind.SOME, label.kind());
        assertEquals(22, label.have());
        assertEquals(36, CarriedLabel.shortage(22, 58));
    }

    @Test
    void enoughForWhatIsStillNeeded() {
        assertEquals(CarriedLabel.Kind.ENOUGH, CarriedLabel.of(128, 58).kind());
        assertEquals(0, CarriedLabel.shortage(128, 58));
    }

    @Test
    void anUnlockedRowNeedsEveryPosition() {
        assertEquals(CarriedLabel.Kind.SOME, CarriedLabel.of(64, 96).kind());
    }
}
