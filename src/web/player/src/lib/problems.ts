import type { components } from '../api/schema';

export type ProblemCode = components['schemas']['ProblemCode'];

const messages: Record<ProblemCode, string> = {
  unsupported_format: 'This file format is not supported. Upload a .litematic, .schem, .schematic or .nbt file.',
  file_too_large: 'The file is too large. Schematics can be at most 16 MiB.',
  decompressed_too_large: 'The file is too large once unpacked.',
  dimensions_too_large: 'The schematic is too large in at least one direction.',
  volume_too_large: 'The schematic holds too many blocks.',
  malformed_nbt: 'The file is damaged and could not be read.',
  palette_index_out_of_range: 'The file is damaged: it refers to blocks it does not define.',
  size_mismatch: 'The file is damaged: its block data does not match its size.',
  challenge_expired: 'The sign-in took too long. Try again.',
  challenge_used: 'The sign-in was already used. Try again.',
  challenge_unknown: 'The sign-in is not known. Try again.',
  identity_not_confirmed: 'Your identity could not be confirmed.',
  identity_provider_unavailable: 'The sign-in provider is unavailable. Try again later.',
  unauthenticated: 'You are signed out. Sign in again.',
  refresh_token_invalid: 'Your session has ended. Sign in again.',
  request_forbidden: 'The request was refused. Reload the page and try again.',
  web_sign_in_unavailable: 'Sign-in on the website is not available right now.',
  not_found: 'This schematic does not exist (any more).',
  over_quota: 'Your storage is full. Delete schematics or versions to make room.',
  last_version: 'The only version of a schematic cannot be deleted. Delete the schematic instead.',
  thumbnail_exists: 'This version already has a thumbnail.',
  thumbnail_invalid: 'The thumbnail is not a valid image.',
  invalid_request: 'The request was not valid.',
  rate_limited: 'Too many requests. Wait a minute and try again.',
  unexpected: 'Something went wrong on the server. Try again later.',
};

export function problemMessage(code: string | null | undefined): string {
  return code && Object.hasOwn(messages, code) ? messages[code as ProblemCode] : 'Something went wrong. Try again.';
}

/** The message for an error answer body, or a generic one when the body is not problem details. */
export function messageOf(body: unknown): string {
  const code = body && typeof body === 'object' && 'code' in body ? (body as { code: unknown }).code : undefined;
  return problemMessage(typeof code === 'string' ? code : undefined);
}

export function codeOf(body: unknown): string | undefined {
  const code = body && typeof body === 'object' && 'code' in body ? (body as { code: unknown }).code : undefined;
  return typeof code === 'string' ? code : undefined;
}

const signInErrors: Record<string, string> = {
  cancelled: 'The sign-in was cancelled on GitHub.',
  no_account: 'There is no account for this GitHub user yet. Register to create one.',
  github_unavailable: 'GitHub could not be reached. Try again in a moment.',
  sign_in_failed: 'The sign-in could not be completed. Try again.',
  signed_out: 'You were signed out. Sign in again to continue.',
};

export function signInErrorMessage(code: string | null | undefined): string | undefined {
  if (!code) {
    return undefined;
  }
  return Object.hasOwn(signInErrors, code) ? signInErrors[code] : 'The sign-in failed. Try again.';
}
