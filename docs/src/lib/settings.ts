// The default settings file the mod's tests compare with what both loaders write.
import source from '../../../src/mod/heavymatica/common/src/test/resources/settings/heavymatica-client.default.toml?raw';

export interface Setting {
  name: string;
  description: string;
  value: string;
  range?: string;
  allowed?: string[];
}

export interface Section {
  name: string;
  description: string;
  settings: Setting[];
}

/** Reads sections and settings with their comments; the mod writes comments right above what they describe. */
export function loadSettings(): Section[] {
  const lines = source.split(/\r?\n/);
  const sections: Section[] = [];
  let comments: string[] = [];
  for (const raw of lines) {
    const line = raw.trim();
    if (line === '') {
      comments = [];
    } else if (line.startsWith('#')) {
      comments.push(line.slice(1).trim());
    } else if (line.startsWith('[')) {
      sections.push({ name: line.slice(1, -1), description: comments.join(' '), settings: [] });
      comments = [];
    } else {
      const equals = line.indexOf('=');
      const setting: Setting = { name: line.slice(0, equals).trim(), value: line.slice(equals + 1).trim(), description: '' };
      const text: string[] = [];
      for (const comment of comments) {
        if (comment.startsWith('Default:')) continue;
        if (comment.startsWith('Range:')) setting.range = comment.slice('Range:'.length).trim().replace(' ~ ', ' to ');
        else if (comment.startsWith('Allowed Values:'))
          setting.allowed = comment.slice('Allowed Values:'.length).split(',').map((value) => value.trim());
        else text.push(comment);
      }
      setting.description = text.join(' ');
      sections.at(-1)?.settings.push(setting);
      comments = [];
    }
  }
  return sections;
}
