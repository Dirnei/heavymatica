import { describe, expect, it } from 'vitest';
import { buildTree, childFolders, chunk, flattenTree, folderName, isInFolder, normalizeFolder, parentsOf, renamedPath, renameMerges } from './folders';

const entry = (path: string, directCount: number, totalCount: number) => ({ path, directCount, totalCount });

describe('isInFolder', () => {
  it('matches the folder and below it, but not lookalikes', () => {
    expect(isInFolder('houses', 'houses')).toBe(true);
    expect(isInFolder('houses/oak', 'houses')).toBe(true);
    expect(isInFolder('housesmall', 'houses')).toBe(false);
    expect(isInFolder('old/houses', 'houses')).toBe(false);
    expect(isInFolder(null, 'houses')).toBe(false);
  });
});

describe('normalizeFolder', () => {
  it('drops empty parts and surrounding spaces', () => {
    expect(normalizeFolder('/village//houses/')).toBe('village/houses');
    expect(normalizeFolder(' village / houses ')).toBe('village/houses');
    expect(normalizeFolder(' / ')).toBe('');
  });
});

describe('parentsOf', () => {
  it('lists every parent shortest first', () => {
    expect(parentsOf('a/b/c')).toEqual(['a', 'a/b']);
    expect(parentsOf('a')).toEqual([]);
  });
});

describe('buildTree', () => {
  it('nests folders and keeps the counts', () => {
    const tree = buildTree([entry('village/houses', 2, 2), entry('castle', 1, 1), entry('village', 1, 3)]);

    expect(tree.map((node) => [node.name, node.totalCount])).toEqual([
      ['castle', 1],
      ['village', 3],
    ]);
    expect(tree[1].children.map((node) => [node.path, node.name, node.directCount, node.totalCount])).toEqual([
      ['village/houses', 'houses', 2, 2],
    ]);
  });

  it('adds parents the list left out', () => {
    const tree = buildTree([entry('a/b', 1, 1)]);

    expect(tree[0].path).toBe('a');
    expect(tree[0].children[0].path).toBe('a/b');
  });

  it('keeps housesmall beside houses, not below it', () => {
    const tree = buildTree([entry('houses', 1, 1), entry('housesmall', 1, 1)]);

    expect(tree.map((node) => node.path)).toEqual(['houses', 'housesmall']);
  });
});

describe('renaming', () => {
  it('moves paths in and below the renamed folder', () => {
    expect(renamedPath('castle', 'castle', 'builds/castle')).toBe('builds/castle');
    expect(renamedPath('castle/towers', 'castle', 'builds/castle')).toBe('builds/castle/towers');
    expect(renamedPath('castles', 'castle', 'builds/castle')).toBe('castles');
  });

  it('merges only into another existing folder', () => {
    const entries = [entry('drafts', 1, 1), entry('wip', 1, 1)];

    expect(renameMerges(entries, 'drafts', 'wip')).toBe(true);
    expect(renameMerges(entries, 'drafts', '/wip/')).toBe(true);
    expect(renameMerges(entries, 'drafts', 'new')).toBe(false);
    expect(renameMerges(entries, 'drafts', 'drafts')).toBe(false);
  });
});

describe('childFolders', () => {
  const entries = [entry('village', 1, 4), entry('village/houses', 2, 3), entry('village/houses/oak', 1, 1), entry('castle', 1, 1), entry('villages', 1, 1)];

  it('lists only the folders directly below the parent', () => {
    expect(childFolders(entries, '').map((folder) => folder.path)).toEqual(['castle', 'village', 'villages']);
    expect(childFolders(entries, 'village').map((folder) => folder.path)).toEqual(['village/houses']);
    expect(childFolders(entries, 'village/houses/oak')).toEqual([]);
  });

  it('names a folder by its last part', () => {
    expect(folderName('village/houses')).toBe('houses');
    expect(folderName('castle')).toBe('castle');
  });
});

describe('flattenTree', () => {
  it('lists parents before children with their depth', () => {
    const flat = flattenTree(buildTree([entry('a', 0, 2), entry('a/b', 1, 1), entry('c', 1, 1)]));

    expect(flat.map(({ node, depth }) => [node.path, depth])).toEqual([
      ['a', 0],
      ['a/b', 1],
      ['c', 0],
    ]);
  });
});

describe('chunk', () => {
  it('splits into chunks of at most the given size', () => {
    const ids = Array.from({ length: 250 }, (_, index) => String(index));

    expect(chunk(ids, 100).map((part) => part.length)).toEqual([100, 100, 50]);
    expect(chunk([], 100)).toEqual([]);
  });
});
