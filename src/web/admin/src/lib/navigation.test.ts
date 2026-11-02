import { describe, expect, it } from 'vitest';
import {
  accountUrl,
  accountsUrl,
  invitationToken,
  isWellFormedSetupCode,
  normalizeSetupCode,
  safeReturnTo,
  signInUrl,
} from './navigation';

describe('safeReturnTo', () => {
  it('keeps same-site paths', () => {
    expect(safeReturnTo('/admins/')).toBe('/admins/');
    expect(safeReturnTo('/account/?id=abc')).toBe('/account/?id=abc');
  });

  it('falls back to the account search for anything else', () => {
    for (const value of [null, undefined, '', 'admins', '//evil.example', '/\\evil.example', 'https://evil.example', '/a\nb']) {
      expect(safeReturnTo(value)).toBe('/accounts/');
    }
  });
});

describe('signInUrl', () => {
  it('returns to the page after the sign-in', () => {
    expect(signInUrl('/admins/', false)).toBe('/?returnTo=%2Fadmins%2F');
    expect(signInUrl('/audit/', true)).toBe('/?returnTo=%2Faudit%2F&ended=1');
  });

  it('leaves out the default destinations', () => {
    expect(signInUrl('/accounts/', false)).toBe('/');
    expect(signInUrl('/passkeys/', true)).toBe('/?ended=1');
  });
});

describe('invitationToken', () => {
  const token = 'q83vEjRWeJq83vEjRWeJq83vEjRWeJq83vEjRWeJAAA';

  it('reads the token from the fragment', () => {
    expect(invitationToken(`#${token}`)).toBe(token);
    expect(invitationToken(`#token=${token}`)).toBe(token);
  });

  it('is undefined for missing or malformed tokens', () => {
    expect(invitationToken('')).toBeUndefined();
    expect(invitationToken('#')).toBeUndefined();
    expect(invitationToken('#short')).toBeUndefined();
    expect(invitationToken(`#${token}<script>`)).toBeUndefined();
  });
});

describe('setup codes', () => {
  it('normalizes and checks the alphabet and length', () => {
    expect(normalizeSetupCode(' abcd-efgh-jkmn ')).toBe('ABCDEFGHJKMN');
    expect(isWellFormedSetupCode('abcd-efgh-jkmn')).toBe(true);
    expect(isWellFormedSetupCode('ABCD EFGH JKMN')).toBe(true);
    expect(isWellFormedSetupCode('ABCD-EFGH')).toBe(false);
    expect(isWellFormedSetupCode('ABCD-EFGH-IJKL')).toBe(false);
  });
});

describe('urls', () => {
  it('builds account and search links', () => {
    expect(accountUrl('a b')).toBe('/account/?id=a%20b');
    expect(accountsUrl(' steve ')).toBe('/accounts/?q=steve');
    expect(accountsUrl('')).toBe('/accounts/');
  });
});
