import { describe, expect, it } from 'vitest';
import {
  isBrowsing,
  landingUrl,
  libraryUrl,
  listingQuery,
  parseLibraryView,
  safeReturnTo,
  signInUrl,
  topLevel,
  type LibraryView,
} from './navigation';

describe('safeReturnTo', () => {
  it('keeps local paths', () => {
    expect(safeReturnTo('/library/')).toBe('/library/');
    expect(safeReturnTo('/schematic/?id=abc')).toBe('/schematic/?id=abc');
  });

  it('replaces anything else with the library', () => {
    for (const value of ['//evil.example', '/\\evil.example', 'https://evil.example/', 'library', '', null, undefined, '/a\nb']) {
      expect(safeReturnTo(value)).toBe('/library/');
    }
  });
});

describe('signInUrl', () => {
  it('starts the GitHub sign-in with intent and a safe return path', () => {
    expect(signInUrl('register', '/upload/')).toBe('/v1/web/sign-in/github?intent=register&returnTo=%2Fupload%2F');
    expect(signInUrl('login', '//evil.example')).toBe('/v1/web/sign-in/github?intent=login&returnTo=%2Flibrary%2F');
  });
});

describe('landingUrl', () => {
  it('returns to the current page, with the signed-out note when a session ended', () => {
    expect(landingUrl('/schematic/?id=a', false)).toBe('/?returnTo=%2Fschematic%2F%3Fid%3Da');
    expect(landingUrl('/library/', true)).toBe('/?returnTo=%2Flibrary%2F&error=signed_out');
  });
});

describe('library view', () => {
  it('round-trips through the address', () => {
    const views: LibraryView[] = [
      topLevel,
      { folder: '', all: true },
      { folder: 'village/houses', all: false },
      { folder: 'old builds/50% done', all: false },
      { folder: '🏰 burg/türme', all: false, tag: 'wip' },
      { folder: '', all: true, tag: 'spruce & oak' },
      { folder: '', all: false, tag: 'wip' },
    ];
    for (const view of views) {
      expect(parseLibraryView(new URL(libraryUrl(view), 'http://localhost').search)).toEqual(view);
    }
  });

  it('keeps the address short and readable', () => {
    expect(libraryUrl(topLevel)).toBe('/library/');
    expect(libraryUrl({ folder: 'village/houses', all: false })).toBe('/library/?folder=village%2Fhouses');
    expect(libraryUrl({ folder: 'ignored', all: true })).toBe('/library/?all=1');
  });
});

describe('listing query', () => {
  const builds: LibraryView = { folder: 'builds', all: false };

  it('lists the schematics in no folder when browsing the top level', () => {
    expect(isBrowsing(topLevel, '')).toBe(true);
    expect(listingQuery(topLevel, '')).toEqual({ unfiled: true });
  });

  it('lists exactly the open folder when browsing it', () => {
    expect(listingQuery(builds, '  ')).toEqual({ folder: 'builds' });
  });

  it('searches everything from the top level', () => {
    expect(isBrowsing(topLevel, 'tower')).toBe(false);
    expect(listingQuery(topLevel, ' tower ')).toEqual({ q: 'tower' });
    expect(listingQuery({ ...topLevel, tag: 'wip' }, '')).toEqual({ tag: 'wip' });
  });

  it('searches the open folder and below it', () => {
    expect(listingQuery(builds, 'tower')).toEqual({ q: 'tower', folder: 'builds', includeSubfolders: true });
    expect(listingQuery({ ...builds, tag: 'wip' }, 'tower')).toEqual({ q: 'tower', tag: 'wip', folder: 'builds', includeSubfolders: true });
  });

  it('lists everything in the all view, without folder cards', () => {
    const all: LibraryView = { folder: '', all: true };
    expect(isBrowsing(all, '')).toBe(false);
    expect(listingQuery(all, '')).toEqual({});
    expect(listingQuery({ ...all, tag: 'wip' }, 'oak')).toEqual({ q: 'oak', tag: 'wip' });
  });
});
