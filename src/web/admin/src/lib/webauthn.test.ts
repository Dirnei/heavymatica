import { describe, expect, it } from 'vitest';
import {
  base64UrlToBuffer,
  bufferToBase64Url,
  decodeCreationOptions,
  decodeRequestOptions,
  describePasskeyError,
  encodeCredential,
} from './webauthn';

function bytes(...values: number[]): ArrayBuffer {
  return new Uint8Array(values).buffer;
}

function list(buffer: BufferSource | undefined): number[] {
  if (!buffer) {
    return [];
  }
  return Array.from(buffer instanceof ArrayBuffer ? new Uint8Array(buffer) : new Uint8Array(buffer.buffer, buffer.byteOffset, buffer.byteLength));
}

describe('base64url', () => {
  it('round-trips bytes without padding', () => {
    const data = bytes(0, 1, 2, 250, 251, 252, 253, 254, 255);
    const text = bufferToBase64Url(data);
    expect(text).not.toMatch(/[+/=]/);
    expect(list(base64UrlToBuffer(text))).toEqual(list(data));
  });

  it('decodes the URL-safe alphabet and missing padding', () => {
    expect(list(base64UrlToBuffer('-_8'))).toEqual([251, 255]);
    expect(list(base64UrlToBuffer('AQ'))).toEqual([1]);
    expect(bufferToBase64Url(bytes(251, 255))).toBe('-_8');
  });

  it('encodes typed array views by their own range', () => {
    const view = new Uint8Array([9, 1, 2, 9]).subarray(1, 3);
    expect(bufferToBase64Url(view)).toBe(bufferToBase64Url(bytes(1, 2)));
  });
});

describe('decodeCreationOptions', () => {
  const json = {
    rp: { id: 'localhost', name: 'Heavymatica Admin' },
    user: { id: 'AQID', name: 'alex', displayName: 'Alex' },
    challenge: 'BAUG',
    pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
    timeout: 60000,
    excludeCredentials: [{ type: 'public-key', id: 'BwgJ', transports: ['usb'] }],
    authenticatorSelection: { residentKey: 'required', userVerification: 'required', authenticatorAttachment: null },
    attestation: 'none',
    hints: null,
  };

  it('turns the challenge, user ID and excluded credential IDs into bytes', () => {
    const options = decodeCreationOptions(json);
    expect(list(options.challenge)).toEqual([4, 5, 6]);
    expect(list(options.user.id)).toEqual([1, 2, 3]);
    expect(options.user.name).toBe('alex');
    expect(options.user.displayName).toBe('Alex');
    expect(list(options.excludeCredentials?.[0]?.id)).toEqual([7, 8, 9]);
    expect(options.excludeCredentials?.[0]?.transports).toEqual(['usb']);
  });

  it('keeps the other options and drops null ones', () => {
    const options = decodeCreationOptions(json);
    expect(options.rp).toEqual(json.rp);
    expect(options.pubKeyCredParams).toEqual(json.pubKeyCredParams);
    expect(options.authenticatorSelection).toEqual({ residentKey: 'required', userVerification: 'required' });
    expect(options.attestation).toBe('none');
    expect(options.timeout).toBe(60000);
    expect('hints' in options).toBe(false);
  });

  it('accepts options wrapped in publicKey and without excluded credentials', () => {
    const { excludeCredentials: _, ...bare } = json;
    const options = decodeCreationOptions({ publicKey: bare });
    expect(list(options.challenge)).toEqual([4, 5, 6]);
    expect(options.excludeCredentials).toBeUndefined();
  });
});

describe('decodeRequestOptions', () => {
  it('turns the challenge and allowed credential IDs into bytes', () => {
    const options = decodeRequestOptions({
      challenge: 'CgsM',
      rpId: 'localhost',
      userVerification: 'required',
      allowCredentials: [{ type: 'public-key', id: 'DQ4P' }],
    });
    expect(list(options.challenge)).toEqual([10, 11, 12]);
    expect(options.rpId).toBe('localhost');
    expect(options.userVerification).toBe('required');
    expect(list(options.allowCredentials?.[0]?.id)).toEqual([13, 14, 15]);
  });

  it('leaves out allowed credentials the server did not send', () => {
    const options = decodeRequestOptions({ challenge: 'CgsM', allowCredentials: null });
    expect(options.allowCredentials).toBeUndefined();
  });
});

describe('encodeCredential', () => {
  it('serializes a registration', () => {
    const json = encodeCredential({
      id: 'AQI',
      rawId: bytes(1, 2),
      type: 'public-key',
      authenticatorAttachment: 'platform',
      response: {
        clientDataJSON: bytes(3),
        attestationObject: bytes(4),
        getAuthenticatorData: () => bytes(5),
        getPublicKey: () => bytes(6),
        getPublicKeyAlgorithm: () => -7,
        getTransports: () => ['internal', 'hybrid'],
      },
      getClientExtensionResults: () => ({ credProps: { rk: true } }),
    });
    expect(json).toEqual({
      id: 'AQI',
      rawId: 'AQI',
      type: 'public-key',
      authenticatorAttachment: 'platform',
      clientExtensionResults: { credProps: { rk: true } },
      response: {
        clientDataJSON: 'Aw',
        attestationObject: 'BA',
        authenticatorData: 'BQ',
        publicKey: 'Bg',
        publicKeyAlgorithm: -7,
        transports: ['internal', 'hybrid'],
      },
    });
  });

  it('serializes an assertion', () => {
    const json = encodeCredential({
      id: 'AQI',
      rawId: bytes(1, 2),
      type: 'public-key',
      response: {
        clientDataJSON: bytes(3),
        authenticatorData: bytes(4),
        signature: bytes(5),
        userHandle: bytes(6),
      },
    });
    expect(json).toEqual({
      id: 'AQI',
      rawId: 'AQI',
      type: 'public-key',
      authenticatorAttachment: null,
      clientExtensionResults: {},
      response: { clientDataJSON: 'Aw', authenticatorData: 'BA', signature: 'BQ', userHandle: 'Bg' },
    });
  });

  it('sends a missing user handle as null', () => {
    const json = encodeCredential({
      id: 'AQI',
      rawId: bytes(1, 2),
      type: 'public-key',
      response: { clientDataJSON: bytes(3), authenticatorData: bytes(4), signature: bytes(5), userHandle: null },
    });
    expect((json.response as Record<string, unknown>).userHandle).toBeNull();
  });
});

describe('describePasskeyError', () => {
  it('treats a cancelled or timed out prompt as cancelled', () => {
    const cancelled = describePasskeyError(new DOMException('cancelled', 'NotAllowedError'), 'sign-in');
    expect(cancelled.cancelled).toBe(true);
    expect(cancelled.message).toMatch(/cancelled/);
    expect(describePasskeyError({ name: 'AbortError' }, 'register')).toMatchObject({ cancelled: true });
    expect(describePasskeyError({ name: 'NotAllowedError' }, 'register').message).toMatch(/Nothing was registered/);
  });

  it('explains other failures', () => {
    expect(describePasskeyError({ name: 'InvalidStateError' }, 'register')).toMatchObject({ cancelled: false });
    expect(describePasskeyError({ name: 'InvalidStateError' }, 'register').message).toMatch(/already holds/);
    expect(describePasskeyError(new Error('boom'), 'sign-in')).toMatchObject({ cancelled: false });
    expect(describePasskeyError(undefined, 'sign-in').message).toMatch(/failed/);
  });
});
