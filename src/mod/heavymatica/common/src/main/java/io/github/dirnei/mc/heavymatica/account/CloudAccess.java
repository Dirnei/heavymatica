package io.github.dirnei.mc.heavymatica.account;

/** The player's choice whether the game may use the Heavymatica Cloud. */
public enum CloudAccess {
    /** Ask the cloud whether it is open when the library opens. */
    AUTO,
    /** Always show the cloud, without asking. */
    ON,
    /** Never show the cloud and never contact it. */
    OFF,
}
