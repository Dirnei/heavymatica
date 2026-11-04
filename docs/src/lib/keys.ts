import keys from '../data/keys.json';
import lang from '../../../src/mod/heavymatica/common/src/main/resources/assets/heavymatica/lang/en_us.json';

/** When a key acts, by the id of its key context in the mod. */
export const CONTEXTS: Record<string, string> = {
  IN_GAME: 'In the game, with no screen open.',
  NO_SCREEN: 'In the game, with no screen open.',
  HOLDING_REMOTE: 'While you hold the remote.',
  LAYER_KEYS: 'While you hold the remote, and without it while the selected placement is locked.',
  WITH_SELECTION: 'While you hold the remote, and without it while a placement is selected (not while capturing).',
};

const NAMED_KEYS: Record<string, string> = {
  up: 'Up arrow',
  down: 'Down arrow',
  left: 'Left arrow',
  right: 'Right arrow',
  'keypad.add': 'Numpad +',
  'keypad.subtract': 'Numpad -',
};

export interface Key {
  id: string;
  name: string;
  defaultKey: string | null;
  when: string;
}

/** Turns a Minecraft key name such as key.keyboard.h into what the controls screen shows. */
export function keyLabel(name: string): string {
  const key = name.replace(/^key\.keyboard\./, '');
  return NAMED_KEYS[key] ?? (key.length === 1 ? key.toUpperCase() : key.replace(/[._]/g, ' '));
}

export function loadKeys(): Key[] {
  const names: Record<string, string> = lang;
  return keys.map((key) => {
    const when = CONTEXTS[key.context];
    if (!when) throw new Error(`No description for the key context ${key.context} of ${key.id} in src/lib/keys.ts`);
    return {
      id: key.id,
      name: names[key.id] ?? key.id,
      defaultKey: key.default ? keyLabel(key.default) : null,
      when,
    };
  });
}
