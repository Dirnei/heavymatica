import { describe, expect, it } from 'vitest';
import { newSchematicUrl } from './upload';

describe('newSchematicUrl', () => {
  it('names the schematic and only adds a folder when there is one', () => {
    expect(newSchematicUrl('Keep & Wall')).toBe('/v1/schematics?name=Keep%20%26%20Wall');
    expect(newSchematicUrl('Keep', 'builds/medieval')).toBe('/v1/schematics?name=Keep&folder=builds%2Fmedieval');
    expect(newSchematicUrl('Keep', '')).toBe('/v1/schematics?name=Keep');
  });
});
