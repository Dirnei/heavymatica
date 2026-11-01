import { describe, expect, it } from 'vitest';
import { describeAccount, describeLink, formatCode, isWellFormedCode, linkUrl, normalizeCode, statusView } from './links';
import { safeReturnTo } from './navigation';

describe('link codes', () => {
  it('normalizes what players type', () => {
    expect(normalizeCode(' k7qm-3xrp ')).toBe('K7QM3XRP');
    expect(normalizeCode('K7QM 3XRP')).toBe('K7QM3XRP');
    expect(normalizeCode(null)).toBe('');
  });

  it('accepts only codes from the game alphabet', () => {
    expect(isWellFormedCode('k7qm-3xrp')).toBe(true);
    expect(isWellFormedCode('K7QM-3XR')).toBe(false);
    expect(isWellFormedCode('K7QM-3XRO')).toBe(false);
    expect(isWellFormedCode('K1QM-3XRP')).toBe(false);
    expect(isWellFormedCode('KUQM-3XRP')).toBe(false);
    expect(isWellFormedCode('')).toBe(false);
  });

  it('shows codes as the game does', () => {
    expect(formatCode('k7qm3xrp')).toBe('K7QM-3XRP');
    expect(formatCode(' abc ')).toBe('abc');
  });

  it('builds a link page address that survives the sign-in', () => {
    expect(linkUrl('k7qm3xrp')).toBe('/link/?code=K7QM-3XRP');
    expect(linkUrl(undefined)).toBe('/link/');
    expect(safeReturnTo(linkUrl('K7QM-3XRP'))).toBe('/link/?code=K7QM-3XRP');
  });
});

describe('describeLink', () => {
  it('tells an account that a Minecraft account joins it', () => {
    expect(describeLink({ issuer: { provider: 'minecraft', name: 'Steve' }, redeemer: { accountNames: ['Alex'] } })).toBe(
      'Your Minecraft account Steve will join this account.',
    );
  });

  it('tells a pending sign-in which account it joins', () => {
    expect(
      describeLink({
        issuer: { accountNames: ['Alex', 'Alex2'] },
        redeemer: { provider: 'github', name: 'alex-builds' },
      }),
    ).toBe('This GitHub sign-in alex-builds will join the account of Alex, Alex2.');
  });

  it('copes with missing names', () => {
    expect(describeLink({ issuer: { provider: 'minecraft' } })).toBe('Your Minecraft account will join this account.');
    expect(describeLink({ issuer: { accountNames: ['Alex'] } })).toBe('This sign-in will join the account of Alex.');
    expect(describeLink({ issuer: {}, redeemer: { provider: 'github' } })).toBe('Your sign-in will join this account.');
  });

  it('names an account by its Minecraft names and web sign-ins, never more', () => {
    expect(describeAccount({ accountNames: ['Alex'], accountProviders: ['github:alex-builds'] })).toBe('Alex, GitHub alex-builds');
    expect(describeAccount({ accountProviders: ['github:alex-builds', 'other'] })).toBe('GitHub alex-builds, other');
    expect(describeAccount({})).toBe('an account without names');
  });
});

describe('statusView', () => {
  it('keeps waiting while the game has not answered', () => {
    expect(statusView('redeemed', 'account')).toMatchObject({ waiting: true, failed: false });
    expect(statusView('redeemed', 'account').message).toMatch(/confirm the link in the game/);
  });

  it('reports a link, and signs a pending sign-in in', () => {
    expect(statusView('linked', 'account')).toMatchObject({ tone: 'success', message: 'Linked.', waiting: false, failed: false });
    expect(statusView('linked', 'pending').message).toMatch(/Signing you in/);
  });

  it.each(['declined', 'expired', 'cancelled', 'something_new'])('ends %s without a link', (status) => {
    const view = statusView(status, 'pending');
    expect(view).toMatchObject({ tone: 'warning', waiting: false, failed: true });
    expect(view.message).toMatch(/Nothing was linked|without linking/);
  });

  it('says why it ended', () => {
    expect(statusView('declined', 'account').message).toMatch(/declined/);
    expect(statusView('expired', 'account').message).toMatch(/expired/);
  });
});
