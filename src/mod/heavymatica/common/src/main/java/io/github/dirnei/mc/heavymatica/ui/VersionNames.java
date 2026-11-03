package io.github.dirnei.mc.heavymatica.ui;

import java.util.Map;
import java.util.Optional;

/** Minecraft release names for the data versions schematic files record, for the releases builders use most. */
final class VersionNames {

    private static final Map<Integer, String> RELEASES = Map.ofEntries(
            Map.entry(1343, "1.12.2"),
            Map.entry(1631, "1.13.2"),
            Map.entry(1976, "1.14.4"),
            Map.entry(2230, "1.15.2"),
            Map.entry(2586, "1.16.5"),
            Map.entry(2730, "1.17.1"),
            Map.entry(2975, "1.18.2"),
            Map.entry(3120, "1.19.2"),
            Map.entry(3337, "1.19.4"),
            Map.entry(3465, "1.20.1"),
            Map.entry(3578, "1.20.2"),
            Map.entry(3700, "1.20.4"),
            Map.entry(3839, "1.20.6"),
            Map.entry(3953, "1.21"),
            Map.entry(3955, "1.21.1"),
            Map.entry(4082, "1.21.3"),
            Map.entry(4189, "1.21.4"));

    private VersionNames() {
    }

    /**
     * The release name of a data version, the running game's name for its own data version, or empty when the data
     * version is not a release in the table.
     */
    static Optional<String> name(int dataVersion, int gameDataVersion, String gameName) {
        if (dataVersion == gameDataVersion) {
            return Optional.of(gameName);
        }
        return Optional.ofNullable(RELEASES.get(dataVersion));
    }
}
