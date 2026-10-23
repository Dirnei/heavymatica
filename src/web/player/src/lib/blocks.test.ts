import { describe, expect, it } from 'vitest';
import { blockName, sortBlockCounts } from './blocks';

describe('sortBlockCounts', () => {
  it('puts the largest count first', () => {
    const sorted = sortBlockCounts([
      { block: 'minecraft:glass', count: 40 },
      { block: 'minecraft:oak_planks', count: 120 },
      { block: 'minecraft:stone', count: 7 },
    ]);
    expect(sorted.map((count) => count.block)).toEqual(['minecraft:oak_planks', 'minecraft:glass', 'minecraft:stone']);
  });

  it('orders equal counts by block ID and leaves the input alone', () => {
    const input = [
      { block: 'minecraft:b', count: 1 },
      { block: 'minecraft:a', count: 1 },
    ];
    expect(sortBlockCounts(input).map((count) => count.block)).toEqual(['minecraft:a', 'minecraft:b']);
    expect(input[0].block).toBe('minecraft:b');
  });
});

describe('blockName', () => {
  it('drops the minecraft namespace and underscores', () => {
    expect(blockName('minecraft:oak_planks')).toBe('oak planks');
    expect(blockName('create:shaft')).toBe('shaft (create)');
  });
});
