export type Json = Record<string, unknown>;

export function base64UrlToBuffer(value: string): ArrayBuffer {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

export function bufferToBase64Url(buffer: ArrayBuffer | ArrayBufferView): string {
  const bytes =
    buffer instanceof ArrayBuffer ? new Uint8Array(buffer) : new Uint8Array(buffer.buffer, buffer.byteOffset, buffer.byteLength);
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Accepts the options either bare or wrapped in `publicKey`, as `navigator.credentials` takes them. */
function unwrap(options: Json): Json {
  const inner = options.publicKey;
  return inner && typeof inner === 'object' ? (inner as Json) : options;
}

function decodeDescriptors(value: unknown): PublicKeyCredentialDescriptor[] | undefined {
  if (!Array.isArray(value)) {
    return undefined;
  }
  return value.map((descriptor: Json) => ({
    ...descriptor,
    id: base64UrlToBuffer(String(descriptor.id)),
  })) as PublicKeyCredentialDescriptor[];
}

/** Servers may send absent members as null, which the browser's dictionary conversion rejects. */
function withoutNulls<T>(value: T): T {
  if (Array.isArray(value)) {
    return value.map(withoutNulls) as T;
  }
  if (value && typeof value === 'object' && !(value instanceof ArrayBuffer) && !ArrayBuffer.isView(value)) {
    return Object.fromEntries(
      Object.entries(value)
        .filter(([, member]) => member !== null && member !== undefined)
        .map(([key, member]) => [key, withoutNulls(member)]),
    ) as T;
  }
  return value;
}

export function decodeCreationOptions(json: Json): PublicKeyCredentialCreationOptions {
  const options = withoutNulls(unwrap(json));
  const user = options.user as Json;
  const excludeCredentials = decodeDescriptors(options.excludeCredentials);
  return {
    ...options,
    challenge: base64UrlToBuffer(String(options.challenge)),
    user: { ...user, id: base64UrlToBuffer(String(user.id)) },
    ...(excludeCredentials ? { excludeCredentials } : {}),
  } as PublicKeyCredentialCreationOptions;
}

export function decodeRequestOptions(json: Json): PublicKeyCredentialRequestOptions {
  const options = withoutNulls(unwrap(json));
  const allowCredentials = decodeDescriptors(options.allowCredentials);
  return {
    ...options,
    challenge: base64UrlToBuffer(String(options.challenge)),
    ...(allowCredentials ? { allowCredentials } : {}),
  } as PublicKeyCredentialRequestOptions;
}

/** The parts of a `PublicKeyCredential` the JSON form is made of, so tests can pass plain objects. */
export interface CredentialLike {
  id: string;
  rawId: ArrayBuffer;
  type: string;
  authenticatorAttachment?: string | null;
  response: {
    clientDataJSON: ArrayBuffer;
    attestationObject?: ArrayBuffer;
    getAuthenticatorData?: () => ArrayBuffer;
    getPublicKey?: () => ArrayBuffer | null;
    getPublicKeyAlgorithm?: () => number;
    getTransports?: () => string[];
    authenticatorData?: ArrayBuffer;
    signature?: ArrayBuffer;
    userHandle?: ArrayBuffer | null;
  };
  getClientExtensionResults?: () => unknown;
}

export function encodeCredential(credential: CredentialLike): Json {
  const source = credential.response;
  const response: Json = { clientDataJSON: bufferToBase64Url(source.clientDataJSON) };
  if (source.attestationObject) {
    response.attestationObject = bufferToBase64Url(source.attestationObject);
    if (source.getAuthenticatorData) {
      response.authenticatorData = bufferToBase64Url(source.getAuthenticatorData());
    }
    const publicKey = source.getPublicKey?.();
    if (publicKey) {
      response.publicKey = bufferToBase64Url(publicKey);
    }
    if (source.getPublicKeyAlgorithm) {
      response.publicKeyAlgorithm = source.getPublicKeyAlgorithm();
    }
    response.transports = source.getTransports?.() ?? [];
  } else {
    if (source.authenticatorData) {
      response.authenticatorData = bufferToBase64Url(source.authenticatorData);
    }
    if (source.signature) {
      response.signature = bufferToBase64Url(source.signature);
    }
    response.userHandle = source.userHandle ? bufferToBase64Url(source.userHandle) : null;
  }
  return {
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type,
    authenticatorAttachment: credential.authenticatorAttachment ?? null,
    clientExtensionResults: credential.getClientExtensionResults?.() ?? {},
    response,
  };
}

interface NativeJson {
  parseCreationOptionsFromJSON?: (options: Json) => PublicKeyCredentialCreationOptions;
  parseRequestOptionsFromJSON?: (options: Json) => PublicKeyCredentialRequestOptions;
}

function native(): NativeJson | undefined {
  return typeof PublicKeyCredential === 'undefined' ? undefined : (PublicKeyCredential as unknown as NativeJson);
}

function toJson(credential: Credential | null): Json {
  if (!credential) {
    throw new DOMException('The browser returned no passkey.', 'NotAllowedError');
  }
  const withJson = credential as Credential & { toJSON?: () => Json };
  return typeof withJson.toJSON === 'function' ? withJson.toJSON() : encodeCredential(credential as unknown as CredentialLike);
}

export function passkeysSupported(): boolean {
  return typeof window !== 'undefined' && typeof PublicKeyCredential !== 'undefined' && !!navigator.credentials;
}

/** Runs the browser's registration prompt for the server's creation options and returns the credential as JSON. */
export async function createPasskey(options: Json): Promise<Json> {
  const parse = native()?.parseCreationOptionsFromJSON;
  const publicKey = parse ? parse(withoutNulls(unwrap(options))) : decodeCreationOptions(options);
  return toJson(await navigator.credentials.create({ publicKey }));
}

/** Runs the browser's sign-in prompt for the server's request options and returns the assertion as JSON. */
export async function getPasskey(options: Json): Promise<Json> {
  const parse = native()?.parseRequestOptionsFromJSON;
  const publicKey = parse ? parse(withoutNulls(unwrap(options))) : decodeRequestOptions(options);
  return toJson(await navigator.credentials.get({ publicKey }));
}

export type PasskeyAction = 'sign-in' | 'register';

export interface PasskeyError {
  /** The person closed or ignored the prompt: nothing happened and nothing needs fixing. */
  cancelled: boolean;
  message: string;
}

export function describePasskeyError(error: unknown, action: PasskeyAction): PasskeyError {
  const name = error && typeof error === 'object' && 'name' in error ? String((error as { name: unknown }).name) : '';
  switch (name) {
    case 'NotAllowedError':
    case 'AbortError':
      return {
        cancelled: true,
        message:
          action === 'sign-in'
            ? 'The sign-in was cancelled. Nothing was changed.'
            : 'The passkey prompt was cancelled. Nothing was registered.',
      };
    case 'InvalidStateError':
      return {
        cancelled: false,
        message: 'This device or security key already holds a passkey for this admin. Use another one.',
      };
    case 'NotSupportedError':
      return { cancelled: false, message: 'This browser or device cannot create a passkey of the kind the admin site needs.' };
    case 'SecurityError':
      return { cancelled: false, message: 'The browser does not allow passkeys for this address.' };
    default:
      return { cancelled: false, message: 'The passkey prompt failed. Try again.' };
  }
}
