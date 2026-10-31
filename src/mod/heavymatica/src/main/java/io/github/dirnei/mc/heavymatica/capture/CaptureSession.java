package io.github.dirnei.mc.heavymatica.capture;

import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Capture mode: on while it has a box. It ends when the player captures, cancels, leaves the world or changes
 * dimension; nothing of it is saved.
 */
public final class CaptureSession {

    /** How far a running capture is: the share of cells read, then saving. */
    public record Progress(double read, boolean saving) {
    }

    private @Nullable CaptureBox box;
    private String dimension = "";
    private @Nullable Progress progress;

    public boolean on() {
        return box != null;
    }

    public Optional<CaptureBox> box() {
        return Optional.ofNullable(box);
    }

    /** Starts capture mode with no corners; {@code minY} and {@code maxY} are the world's block build height. */
    public void start(String dimension, int minY, int maxY) {
        box = new CaptureBox(minY, maxY);
        this.dimension = dimension;
        progress = null;
    }

    public void end() {
        box = null;
        progress = null;
    }

    /** Called every tick with the player's dimension, or "" outside a world. */
    public void tick(String currentDimension) {
        if (box != null && !currentDimension.equals(dimension)) {
            end();
        }
    }

    public Optional<Progress> progress() {
        return Optional.ofNullable(progress);
    }

    public boolean capturing() {
        return progress != null;
    }

    public void setProgress(@Nullable Progress progress) {
        this.progress = progress;
    }
}
