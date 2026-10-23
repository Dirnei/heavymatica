export const maxUploadBytes = 16 * 1024 * 1024;

const maxNameLength = 100;

/** The schematic name for an uploaded file: its name without the extension. */
export function fileBaseName(fileName: string): string {
  const base = fileName.replace(/^.*[\\/]/, '').trim();
  const dot = base.lastIndexOf('.');
  const name = (dot > 0 ? base.slice(0, dot).trim() : base) || 'Schematic';
  return name.slice(0, maxNameLength);
}

export function fitsUpload(size: number): boolean {
  return size <= maxUploadBytes;
}
