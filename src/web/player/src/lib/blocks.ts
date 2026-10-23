export interface BlockCount {
  block: string;
  count: number;
}

/** Largest count first; equal counts by block ID. */
export function sortBlockCounts<T extends BlockCount>(counts: readonly T[]): T[] {
  return [...counts].sort((a, b) => b.count - a.count || (a.block < b.block ? -1 : a.block > b.block ? 1 : 0));
}

/** `minecraft:oak_planks` reads as `oak planks`; blocks of other namespaces keep theirs. */
export function blockName(block: string): string {
  const colon = block.indexOf(':');
  const namespace = colon >= 0 ? block.slice(0, colon) : 'minecraft';
  const name = (colon >= 0 ? block.slice(colon + 1) : block).replaceAll('_', ' ');
  return namespace === 'minecraft' ? name : `${name} (${namespace})`;
}
