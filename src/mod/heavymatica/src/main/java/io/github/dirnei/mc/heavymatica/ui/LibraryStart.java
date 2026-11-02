package io.github.dirnei.mc.heavymatica.ui;

/** What the library shows when it opens: its grid, the import screen for an empty library, or nothing yet while the listing loads. */
enum LibraryStart {
    GRID,
    IMPORT,
    WAIT;

    /**
     * @param entries how many local and cloud entries the library has
     * @param loading whether the cloud listing is still loading
     */
    static LibraryStart decide(int entries, boolean loading) {
        if (entries > 0) {
            return GRID;
        }
        return loading ? WAIT : IMPORT;
    }
}
