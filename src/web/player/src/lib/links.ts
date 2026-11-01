import type { components } from '../api/schema';

export type LinkCode = components['schemas']['LinkCode'];
export type LinkCodeStatus = components['schemas']['LinkCodeStatus'];
export type LinkParty = components['schemas']['LinkParty'];

/** How often the link code page asks whether the game has confirmed. */
export const pollIntervalMs = 2000;

export const linkPath = '/link/';

const codeAlphabet = /^[23456789ABCDEFGHJKMNPQRSTVWXYZ]{8}$/;

/** Upper-cases the code and drops spaces and dashes, as the cloud does before comparing. */
export function normalizeCode(input: string | null | undefined): string {
  return (input ?? '').toUpperCase().replace(/[\s-]/g, '');
}

/** True when the input can be a code at all, so a typo is caught without spending a redeem attempt. */
export function isWellFormedCode(input: string | null | undefined): boolean {
  return codeAlphabet.test(normalizeCode(input));
}

/** The code as the game shows it, XXXX-XXXX, or the input unchanged when it is not a code. */
export function formatCode(input: string | null | undefined): string {
  const code = normalizeCode(input);
  return code.length === 8 ? `${code.slice(0, 4)}-${code.slice(4)}` : (input ?? '').trim();
}

/** The link code page, with the code filled in when given. */
export function linkUrl(code?: string | null): string {
  const normalized = normalizeCode(code);
  return normalized ? `${linkPath}?code=${encodeURIComponent(formatCode(normalized))}` : linkPath;
}

const providerNames: Record<string, string> = {
  minecraft: 'Minecraft',
  github: 'GitHub',
  microsoft: 'Microsoft',
  google: 'Google',
};

export function providerName(provider: string | null | undefined): string {
  if (!provider) {
    return 'unknown';
  }
  return Object.hasOwn(providerNames, provider) ? providerNames[provider] : provider;
}

function isAccount(party: LinkParty): boolean {
  return (party.accountNames?.length ?? 0) > 0 || (party.accountProviders?.length ?? 0) > 0;
}

/** An account as people know it: its Minecraft names, then its web sign-ins as "GitHub alex-builds". */
export function describeAccount(party: LinkParty): string {
  const webSignIns = (party.accountProviders ?? []).map((entry) => {
    const split = entry.indexOf(':');
    return split < 0 ? providerName(entry) : `${providerName(entry.slice(0, split))} ${entry.slice(split + 1)}`;
  });
  const parts = [...(party.accountNames ?? []), ...webSignIns];
  return parts.length > 0 ? parts.join(', ') : 'an account without names';
}

/** One identity, such as "Minecraft account Steve" or "GitHub sign-in alex-builds". */
function describeIdentity(party: LinkParty | undefined): string {
  const provider = party?.provider;
  const noun = provider === 'minecraft' ? 'account' : 'sign-in';
  const name = party?.name ? ` ${party.name}` : '';
  return provider ? `${providerName(provider)} ${noun}${name}` : `sign-in${name}`;
}

/**
 * What the issuer's confirmation will link, told from the redeemer's side: an account that issued the code takes in
 * the redeemer's sign-in; a pending sign-in that issued it joins the redeemer's account.
 */
export function describeLink(code: Pick<LinkCode, 'issuer' | 'redeemer'>): string {
  if (isAccount(code.issuer)) {
    return `This ${describeIdentity(code.redeemer)} will join the account of ${describeAccount(code.issuer)}.`;
  }
  return `Your ${describeIdentity(code.issuer)} will join this account.`;
}

export type RedeemerKind = 'account' | 'pending';

export interface StatusView {
  tone: 'info' | 'success' | 'warning';
  message: string;
  /** Still waiting for the game: keep polling. */
  waiting: boolean;
  /** Over without a link: offer another code. */
  failed: boolean;
}

export function statusView(status: LinkCodeStatus | string, redeemer: RedeemerKind): StatusView {
  switch (status) {
    case 'open':
    case 'redeemed':
      return {
        tone: 'info',
        message: 'Now confirm the link in the game. This page updates once you have.',
        waiting: true,
        failed: false,
      };
    case 'linked':
      return {
        tone: 'success',
        message: redeemer === 'pending' ? 'Linked. Signing you in to the account…' : 'Linked.',
        waiting: false,
        failed: false,
      };
    case 'declined':
      return { tone: 'warning', message: 'The link was declined in the game. Nothing was linked.', waiting: false, failed: true };
    case 'expired':
      return {
        tone: 'warning',
        message: 'The code expired before the game confirmed it. Nothing was linked.',
        waiting: false,
        failed: true,
      };
    case 'cancelled':
      return { tone: 'warning', message: 'The code was cancelled in the game. Nothing was linked.', waiting: false, failed: true };
    default:
      return { tone: 'warning', message: 'The link ended without linking anything.', waiting: false, failed: true };
  }
}
