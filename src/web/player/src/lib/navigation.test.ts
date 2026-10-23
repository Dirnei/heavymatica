import { describe, expect, it } from 'vitest';
import { landingUrl, safeReturnTo, signInUrl } from './navigation';

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
