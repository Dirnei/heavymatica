import type { components } from '../api/schema';

export type ProblemCode = components['schemas']['ProblemCode'];

const messages: Record<ProblemCode, string> = {
  unauthenticated: 'You are signed out. Sign in again.',
  request_forbidden: 'The request was refused. Reload the page and try again.',
  not_found: 'This does not exist (any more). Reload the page.',
  invalid_request: 'The request was not valid. Check the form and try again.',
  rate_limited: 'Too many attempts. Wait a few minutes and try again.',
  internal_error: 'Something went wrong on the server. Try again later.',
  setup_code_invalid: 'This setup code is not valid. It may have expired or been used. Run the setup command again.',
  invitation_invalid: 'This invitation is not valid. It may have expired, been used or been revoked. Ask for a new one.',
  second_passkey_required: 'Register a second passkey before you continue.',
  passkey_required: 'Every admin keeps at least two passkeys, so this passkey cannot be removed.',
  last_admin: 'The last admin cannot be removed.',
  cannot_remove_self: 'You cannot remove yourself. Ask another admin to do it.',
  passkey_failed: 'The passkey could not be verified. Try again.',
};

export function problemMessage(code: string | null | undefined): string {
  return code && Object.hasOwn(messages, code) ? messages[code as ProblemCode] : 'Something went wrong. Try again.';
}

export function codeOf(body: unknown): string | undefined {
  const code = body && typeof body === 'object' && 'code' in body ? (body as { code: unknown }).code : undefined;
  return typeof code === 'string' ? code : undefined;
}

/** The message for an error answer body, or a generic one when the body is not problem details. */
export function messageOf(body: unknown): string {
  return problemMessage(codeOf(body));
}

export const offlineMessage = 'The admin service could not be reached. Check your connection and try again.';
