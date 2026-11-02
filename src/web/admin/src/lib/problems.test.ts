import { describe, expect, it } from 'vitest';
import contract from '../../../../api/heavymatica-admin.v1.yaml?raw';
import { codeOf, messageOf, problemMessage } from './problems';

const adminCodes = [
  'unauthenticated',
  'request_forbidden',
  'not_found',
  'invalid_request',
  'rate_limited',
  'internal_error',
  'setup_code_invalid',
  'invitation_invalid',
  'passkey_required',
  'last_admin',
  'cannot_remove_self',
  'passkey_failed',
];

function contractCodes(): string[] {
  const block = /\n {4}ProblemCode:\n[\s\S]*?\n {6}enum:\n((?: {8}- .+\n)+)/.exec(contract.replace(/\r\n/g, '\n'));
  if (!block) {
    throw new Error('ProblemCode enum not found in the admin contract');
  }
  return block[1]!.split('\n').filter(Boolean).map((line) => line.replace(/^ {8}- /, '').trim());
}

describe('problemMessage', () => {
  const fallback = problemMessage('something_new');

  it('knows every code of the admin contract', () => {
    expect([...contractCodes()].sort()).toEqual([...adminCodes].sort());
  });

  it.each(adminCodes)('has its own message for %s', (code) => {
    const message = problemMessage(code);
    expect(message).not.toBe(fallback);
    expect(message.length).toBeGreaterThan(10);
  });

  it('names what the admin needs to do', () => {
    expect(problemMessage('setup_code_invalid')).toMatch(/setup command/);
    expect(problemMessage('invitation_invalid')).toMatch(/new one/);
    expect(problemMessage('passkey_required')).toMatch(/at least one passkey/);
    expect(problemMessage('last_admin')).toMatch(/last admin/);
    expect(problemMessage('cannot_remove_self')).toMatch(/another admin/);
    expect(problemMessage('rate_limited')).toMatch(/Wait/);
  });

  it('falls back for unknown or missing codes', () => {
    expect(problemMessage(undefined)).toBe(fallback);
    expect(problemMessage(null)).toBe(fallback);
    expect(problemMessage('toString')).toBe(fallback);
  });
});

describe('messageOf', () => {
  it('reads the code of a problem body', () => {
    expect(messageOf({ code: 'last_admin', status: 409 })).toBe(problemMessage('last_admin'));
    expect(codeOf({ code: 'last_admin' })).toBe('last_admin');
  });

  it('falls back for other bodies', () => {
    expect(messageOf('<html>')).toBe(problemMessage(undefined));
    expect(messageOf(null)).toBe(problemMessage(undefined));
    expect(codeOf({ code: 42 })).toBeUndefined();
    expect(codeOf(undefined)).toBeUndefined();
  });
});
