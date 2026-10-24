import type { FolderEntry } from './api';

export interface FolderNode {
  path: string;
  name: string;
  directCount: number;
  totalCount: number;
  children: FolderNode[];
}

/** The path the cloud stores for this input: no leading, trailing or repeated slashes; empty means no folder. */
export function normalizeFolder(input: string): string {
  return input
    .split('/')
    .map((part) => part.trim())
    .filter((part) => part.length > 0)
    .join('/');
}

/** True when `path` is `folder` or below it; `houses` holds `houses/oak` but not `housesmall`. */
export function isInFolder(path: string | null | undefined, folder: string): boolean {
  return path === folder || (typeof path === 'string' && path.startsWith(folder + '/'));
}

/** Every parent of `path`, shortest first, without `path` itself. */
export function parentsOf(path: string): string[] {
  const parts = path.split('/');
  return parts.slice(1).map((_, index) => parts.slice(0, index + 1).join('/'));
}

/** The tree of the cloud's folder list. Parents missing from the list are added with zero counts. */
export function buildTree(entries: readonly FolderEntry[]): FolderNode[] {
  const roots: FolderNode[] = [];
  const nodes = new Map<string, FolderNode>();

  function nodeFor(path: string): FolderNode {
    const existing = nodes.get(path);
    if (existing) {
      return existing;
    }
    const slash = path.lastIndexOf('/');
    const node: FolderNode = { path, name: path.slice(slash + 1), directCount: 0, totalCount: 0, children: [] };
    nodes.set(path, node);
    (slash < 0 ? roots : nodeFor(path.slice(0, slash)).children).push(node);
    return node;
  }

  for (const entry of [...entries].sort((a, b) => (a.path < b.path ? -1 : a.path > b.path ? 1 : 0))) {
    const node = nodeFor(entry.path);
    node.directCount = entry.directCount;
    node.totalCount = entry.totalCount;
  }
  return roots;
}

/** The folders directly below `parent` (`''` for the top level), sorted by name. */
export function childFolders(entries: readonly FolderEntry[], parent: string): FolderEntry[] {
  const prefix = parent ? parent + '/' : '';
  return entries
    .filter((entry) => entry.path.startsWith(prefix) && entry.path.length > prefix.length && !entry.path.slice(prefix.length).includes('/'))
    .sort((a, b) => (a.path < b.path ? -1 : a.path > b.path ? 1 : 0));
}

/** The last part of a path, the name shown on a folder card. */
export function folderName(path: string): string {
  return path.slice(path.lastIndexOf('/') + 1);
}

/** Where `path` ends up when `from` is renamed to `to`, or `path` unchanged when it is outside `from`. */
export function renamedPath(path: string, from: string, to: string): string {
  return isInFolder(path, from) ? to + path.slice(from.length) : path;
}

/** True when renaming `from` to `to` would put its schematics into a folder that already has some. */
export function renameMerges(entries: readonly FolderEntry[], from: string, to: string): boolean {
  const target = normalizeFolder(to);
  return target !== normalizeFolder(from) && entries.some((entry) => entry.path === target);
}

/** Every node with its depth, parents before children, for flat lists such as the move dialog. */
export function flattenTree(nodes: readonly FolderNode[], depth = 0): { node: FolderNode; depth: number }[] {
  return nodes.flatMap((node) => [{ node, depth }, ...flattenTree(node.children, depth + 1)]);
}

/** The drag-and-drop type that carries schematic IDs from the library list onto the folder tree. */
export const dragType = 'application/x-heavymatica-schematics';

export function chunk<T>(items: readonly T[], size: number): T[][] {
  const chunks: T[][] = [];
  for (let start = 0; start < items.length; start += size) {
    chunks.push(items.slice(start, start + size));
  }
  return chunks;
}
