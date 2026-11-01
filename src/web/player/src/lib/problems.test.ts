import { describe, expect, it } from 'vitest';
import { codeOf, messageOf, problemMessage, signInErrorMessage } from './problems';

const websiteCodes = [
  'unsupported_format',
  'file_too_large',
  'decompressed_too_large',
  'dimensions_too_large',
  'volume_too_large',
  'malformed_nbt',
  'palette_index_out_of_range',
  'size_mismatch',
  'over_quota',
  'last_version',
  'not_found',
  'unauthenticated',
  'request_forbidden',
  'rate_limited',
  'invalid_request',
  'web_sign_in_unavailable',
  'account_choice_required',
  'link_code_invalid',
  'link_code_not_applicable',
  'identity_already_linked',
  'too_many_identities',
  'unexpected',
];

describe('problemMessage', () => {
  const fallback = problemMessage('something_new');

  it.each(websiteCodes)('has its own message for %s', (code) => {
    const message = problemMessage(code);
    expect(message).not.toBe(fallback);
    expect(message.length).toBeGreaterThan(10);
  });

  it('names the reasons players need to act on', () => {
    expect(problemMessage('unsupported_format')).toMatch(/not supported/);
    expect(problemMessage('file_too_large')).toMatch(/too large/);
    expect(problemMessage('over_quota')).toMatch(/storage is full/);
  });

  it('falls back for unknown or missing codes', () => {
    expect(problemMessage(undefined)).toBe(fallback);
    expect(problemMessage('toString')).toBe(fallback);
  });
});

describe('messageOf', () => {
  it('reads the code of a problem body', () => {
    expect(messageOf({ code: 'over_quota', status: 422 })).toBe(problemMessage('over_quota'));
    expect(codeOf({ code: 'over_quota' })).toBe('over_quota');
  });

  it('falls back for other bodies', () => {
    expect(messageOf('<html>')).toBe(problemMessage(undefined));
    expect(messageOf(null)).toBe(problemMessage(undefined));
    expect(codeOf(undefined)).toBeUndefined();
  });
});

describe('signInErrorMessage', () => {
  it('explains every error the sign-in sends back', () => {
    for (const code of ['cancelled', 'no_account', 'github_unavailable', 'sign_in_failed', 'signed_out']) {
      expect(signInErrorMessage(code)).toBeTruthy();
    }
    expect(signInErrorMessage('no_account')).toMatch(/Register/);
    expect(signInErrorMessage('cancelled')).toMatch(/cancelled/);
  });

  it('is empty without an error and generic for unknown ones', () => {
    expect(signInErrorMessage(null)).toBeUndefined();
    expect(signInErrorMessage('weird')).toMatch(/failed/);
  });
});
