package io.github.dirnei.mc.heavymatica.ui;

import org.jetbrains.annotations.Nullable;

/** What an upload becomes for the chosen target, as the upload dialog says it. Free of the game. */
final class UploadChoice {

    private UploadChoice() {
    }

    /**
     * @param targetName    the cloud schematic it is added to, or null for a new schematic
     * @param targetVersion that schematic's latest version number
     */
    static LinkCodeText.Text summary(String entryName, boolean asVersion, @Nullable String targetName, int targetVersion) {
        if (!asVersion) {
            return LinkCodeText.Text.of("heavymatica.upload.summary.new", entryName);
        }
        if (targetName == null) {
            return LinkCodeText.Text.of("heavymatica.upload.summary.pick");
        }
        return LinkCodeText.Text.of("heavymatica.upload.summary.version", targetName, Integer.toString(targetVersion + 1));
    }

    /** {@code v2} for an unselected row, {@code v2 → v3} for the selected one. */
    static String versionLabel(int version, boolean selected) {
        return selected ? "v" + version + " → v" + (version + 1) : "v" + version;
    }
}
