package io.github.dirnei.mc.heavymatica.library;

/** One card of the library explorer: the way up, a folder, or a schematic. */
public sealed interface LibraryItem {

    /** @param parent the folder it opens, {@code ""} for the top level */
    record Up(String parent) implements LibraryItem {
    }

    /** @param totalCount entries in this folder and every folder below it */
    record Folder(String path, String name, int totalCount) implements LibraryItem {
    }

    record Entry(LibraryEntry entry) implements LibraryItem {
    }
}
