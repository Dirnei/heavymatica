import { describe, expect, it } from 'vitest';
import { fileBaseName, fitsUpload, maxUploadBytes } from './files';

describe('fileBaseName', () => {
  it('drops the extension', () => {
    expect(fileBaseName('castle.litematic')).toBe('castle');
    expect(fileBaseName('my.tower.schem')).toBe('my.tower');
  });

  it('keeps names without an extension or starting with a dot', () => {
    expect(fileBaseName('bridge')).toBe('bridge');
    expect(fileBaseName('.nbt')).toBe('.nbt');
  });

  it('caps the name at 100 characters', () => {
    expect(fileBaseName('x'.repeat(150) + '.nbt')).toHaveLength(100);
  });
});

describe('fitsUpload', () => {
  it('accepts at most 16 MiB', () => {
    expect(fitsUpload(maxUploadBytes)).toBe(true);
    expect(fitsUpload(maxUploadBytes + 1)).toBe(false);
  });
});
