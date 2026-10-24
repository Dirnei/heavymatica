export const libraryPath = '/library/';

/** Keeps only same-site paths, so a sign-in can never redirect to another site. */
export function safeReturnTo(value: string | null | undefined): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return libraryPath;
  }
  for (let i = 0; i < value.length; i++) {
    if (value.charCodeAt(i) < 0x20) {
      return libraryPath;
    }
  }
  return value;
}

export type SignInIntent = 'register' | 'login';

export function signInUrl(intent: SignInIntent, returnTo: string | null | undefined): string {
  return `/v1/web/sign-in/github?intent=${intent}&returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`;
}

/** The landing page, returning to `current` after the sign-in. */
export function landingUrl(current: string, signedOut: boolean): string {
  const query = new URLSearchParams({ returnTo: safeReturnTo(current) });
  if (signedOut) {
    query.set('error', 'signed_out');
  }
  return `/?${query.toString()}`;
}

/**
 * Where the library is: an open folder (`''` for the top level) or the flat "All schematics" view, and the chosen
 * tag, if any.
 */
export interface LibraryView {
  folder: string;
  all: boolean;
  tag?: string;
}

export const topLevel: LibraryView = { folder: '', all: false };

export function libraryUrl(view: LibraryView): string {
  const query = new URLSearchParams();
  if (view.all) {
    query.set('all', '1');
  } else if (view.folder) {
    query.set('folder', view.folder);
  }
  if (view.tag) {
    query.set('tag', view.tag);
  }
  const search = query.toString();
  return search ? `${libraryPath}?${search}` : libraryPath;
}

export function parseLibraryView(search: string): LibraryView {
  const query = new URLSearchParams(search);
  const all = query.get('all') === '1';
  const tag = query.get('tag') || undefined;
  return { folder: all ? '' : (query.get('folder') ?? ''), all, ...(tag ? { tag } : {}) };
}

/** True when the view browses folders: folder cards and only the schematics directly in the open folder. */
export function isBrowsing(view: LibraryView, search: string): boolean {
  return !view.all && !view.tag && search.trim() === '';
}

export interface ListingQuery {
  q?: string;
  tag?: string;
  folder?: string;
  includeSubfolders?: boolean;
  unfiled?: boolean;
}

/** The `listSchematics` filters for a view: browsing lists one folder, search and tags its whole subtree. */
export function listingQuery(view: LibraryView, search: string): ListingQuery {
  const q = search.trim() || undefined;
  const query: ListingQuery = { ...(q ? { q } : {}), ...(view.tag ? { tag: view.tag } : {}) };
  if (view.all) {
    return query;
  }
  if (isBrowsing(view, search)) {
    return view.folder ? { ...query, folder: view.folder } : { ...query, unfiled: true };
  }
  return view.folder ? { ...query, folder: view.folder, includeSubfolders: true } : query;
}

export function schematicUrl(id: string): string {
  return `/schematic/?id=${encodeURIComponent(id)}`;
}

export function uploadUrl(schematicId?: string): string {
  return schematicId ? `/upload/?schematic=${encodeURIComponent(schematicId)}` : '/upload/';
}

/** The address of a version's thumbnail; the cloud redirects it to a short-lived image link. */
export function thumbnailUrl(schematicId: string, version: number): string {
  return `/v1/schematics/${encodeURIComponent(schematicId)}/versions/${version}/thumbnail`;
}
