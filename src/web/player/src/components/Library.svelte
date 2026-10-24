<script lang="ts">
  import { onMount } from 'svelte';
  import { SvelteSet } from 'svelte/reactivity';
  import { api, type FolderList, type SchematicSummary, type TagList } from '../lib/api';
  import { childFolders, chunk, dragType, folderName, parentsOf, renamedPath } from '../lib/folders';
  import { formatBytes, formatDimensions, formatTime } from '../lib/format';
  import {
    isBrowsing,
    libraryUrl,
    listingQuery,
    parseLibraryView,
    schematicUrl,
    thumbnailUrl,
    uploadUrl,
    type LibraryView,
  } from '../lib/navigation';
  import type { MenuItem } from '../lib/menu';
  import { codeOf, messageOf, problemMessage } from '../lib/problems';
  import { account, refreshAccount } from '../lib/session';
  import { fileBaseName, fitsUpload } from '../lib/files';
  import { newSchematicUrl, uploadFile, type QueuedUpload } from '../lib/upload';
  import UploadQueue from './UploadQueue.svelte';
  import ContextMenu from './ContextMenu.svelte';
  import FolderTree from './FolderTree.svelte';
  import MoveDialog from './MoveDialog.svelte';
  import RenameFolderDialog from './RenameFolderDialog.svelte';
  import RenameSchematicDialog from './RenameSchematicDialog.svelte';
  import TagListView from './TagList.svelte';
  import UsageMeter from './UsageMeter.svelte';

  const pageSize = 50;
  const searchDelayMs = 250;
  const moveBatch = 100;

  let search = $state('');
  let view = $state<LibraryView>(parseLibraryView(window.location.search));
  let items = $state<SchematicSummary[]>([]);
  let nextCursor = $state<string | null>(null);
  let loading = $state(false);
  let loaded = $state(false);
  let error = $state<string>();
  let folders = $state<FolderList>();
  let tags = $state<TagList>();
  let sidebarOpen = $state(false);
  let moving = $state<string[]>();
  let renaming = $state<string>();
  let renamingSchematic = $state<SchematicSummary>();
  let menu = $state<{ x: number; y: number; items: MenuItem[] }>();
  let uploads = $state<QueuedUpload[]>([]);
  let uploadKey = 0;
  let pickerFolder = '';
  let picker = $state<HTMLInputElement>();
  let fileDrag = $state(false);
  let dropTarget = $state<string>();
  let generation = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let sidebarDialog = $state<HTMLDialogElement>();

  const selected = new SvelteSet<string>();
  const browsing = $derived(isBrowsing(view, search));
  const folderCards = $derived(browsing && folders ? childFolders(folders.folders, view.folder) : []);
  const crumbs = $derived(view.folder ? [...parentsOf(view.folder), view.folder] : []);
  const allSelected = $derived(items.length > 0 && items.every((item) => selected.has(item.id)));
  const empty = $derived(loaded && items.length === 0 && folderCards.length === 0);
  /** Where uploads from this view go: the open folder, or the top level in "All schematics". */
  const uploadFolder = $derived(view.all ? '' : view.folder);
  const scope = $derived(view.all || !view.folder ? 'the whole library' : `“${view.folder}”`);

  async function load(reset: boolean) {
    const current = reset ? ++generation : generation;
    loading = true;
    error = undefined;
    const cursor = reset ? undefined : (nextCursor ?? undefined);
    try {
      const { data, error: problem } = await api.GET('/v1/schematics', {
        params: { query: { ...listingQuery(view, search), cursor, limit: pageSize } },
      });
      if (current !== generation) {
        return;
      }
      if (!data) {
        error = messageOf(problem);
        return;
      }
      items = reset ? data.items : [...items, ...data.items];
      nextCursor = data.nextCursor ?? null;
      loaded = true;
    } catch {
      if (current === generation) {
        error = 'The library could not be loaded. Check your connection and try again.';
      }
    } finally {
      if (current === generation) {
        loading = false;
      }
    }
  }

  /** Folders and tags feed the tree, the folder cards and the tag list; the cards still work without them. */
  async function loadSidebar() {
    try {
      const [folderAnswer, tagAnswer] = await Promise.all([api.GET('/v1/folders'), api.GET('/v1/tags')]);
      folders = folderAnswer.data ?? folders;
      tags = tagAnswer.data ?? tags;
    } catch {
      // The next change or reload tries again.
    }
  }

  function searchChanged() {
    clearTimeout(timer);
    selected.clear();
    timer = setTimeout(() => void load(true), searchDelayMs);
  }

  function go(next: LibraryView) {
    sidebarDialog?.close();
    if (libraryUrl(next) !== libraryUrl(view)) {
      history.pushState(null, '', libraryUrl(next));
      show(next);
    }
  }

  function show(next: LibraryView) {
    view = next;
    selected.clear();
    void load(true);
  }

  function open(folder: string, all = false) {
    go({ folder: all ? '' : folder, all, ...(view.tag ? { tag: view.tag } : {}) });
  }

  function chooseTag(tag: string | undefined) {
    go({ folder: view.folder, all: view.all, ...(tag ? { tag } : {}) });
  }

  function toggleSelected(id: string) {
    if (selected.has(id)) {
      selected.delete(id);
    } else {
      selected.add(id);
    }
  }

  function toggleAll() {
    if (allSelected) {
      selected.clear();
    } else {
      items.forEach((item) => selected.add(item.id));
    }
  }

  function dragStart(event: DragEvent, id: string) {
    const ids = selected.has(id) ? [...selected] : [id];
    event.dataTransfer?.setData(dragType, JSON.stringify(ids));
    if (event.dataTransfer) {
      event.dataTransfer.effectAllowed = 'move';
    }
  }

  function dragOverFolder(event: DragEvent, path: string) {
    const types = event.dataTransfer?.types ?? [];
    if (types.includes(dragType) || types.includes('Files')) {
      event.preventDefault();
      event.stopPropagation();
      event.dataTransfer!.dropEffect = types.includes('Files') ? 'copy' : 'move';
      dropTarget = path;
      fileDrag = false;
    }
  }

  function dropOnFolder(event: DragEvent, path: string) {
    dropTarget = undefined;
    const files = [...(event.dataTransfer?.files ?? [])];
    if (files.length > 0) {
      event.preventDefault();
      event.stopPropagation();
      void uploadFiles(files, path);
      return;
    }
    const data = event.dataTransfer?.getData(dragType);
    if (!data) {
      return;
    }
    event.preventDefault();
    try {
      const ids: unknown = JSON.parse(data);
      if (Array.isArray(ids)) {
        void dropped(path, ids.filter((id): id is string => typeof id === 'string'));
      }
    } catch {
      // Not a drag from the cards.
    }
  }

  /** Moves the schematics in batches and returns an error message when a batch failed. */
  async function move(ids: string[], folder: string): Promise<string | undefined> {
    let failure: string | undefined;
    try {
      for (const batch of chunk(ids, moveBatch)) {
        const { data, error: problem } = await api.POST('/v1/schematics/move', { body: { schematicIds: batch, folder } });
        if (!data) {
          failure = messageOf(problem);
          break;
        }
      }
    } catch {
      failure = 'The schematics could not be moved. Check your connection and try again.';
    }
    selected.clear();
    await Promise.all([loadSidebar(), load(true)]);
    return failure;
  }

  async function dropped(folder: string, ids: string[]) {
    if (ids.length > 0) {
      error = await move(ids, folder);
    }
  }

  async function renameFolder(from: string, to: string): Promise<string | undefined> {
    try {
      const { data, error: problem } = await api.POST('/v1/folders/rename', { body: { from, to } });
      if (!data) {
        const code = codeOf(problem);
        if (code === 'not_found') {
          await loadSidebar();
          return 'This folder no longer exists.';
        }
        return code === 'invalid_request' ? 'Folder paths can be at most 200 characters, including every subfolder.' : messageOf(problem);
      }
    } catch {
      return 'The folder could not be renamed. Check your connection and try again.';
    }
    const path = renamedPath(view.folder, from, to);
    if (!view.all && path !== view.folder) {
      view = { ...view, folder: path };
      history.replaceState(null, '', libraryUrl(view));
    }
    await Promise.all([loadSidebar(), load(true)]);
    return undefined;
  }

  function pickFiles(folder: string) {
    pickerFolder = folder;
    if (picker) {
      picker.value = '';
      picker.click();
    }
  }

  /** Uploads one file after the other into `folder`, then refreshes the cards, folders, tags and storage use. */
  async function uploadFiles(files: File[], folder: string) {
    if (files.length === 0) {
      return;
    }
    if (!uploads.some((upload) => upload.state === 'waiting' || upload.state === 'uploading')) {
      uploads = [];
    }
    const added = files.map((file) => ({ file, key: ++uploadKey }));
    uploads.push(...added.map(({ file, key }) => ({ key, name: fileBaseName(file.name), folder, progress: 0, state: 'waiting' as const })));
    for (const { file, key } of added) {
      const upload = uploads.find((candidate) => candidate.key === key);
      if (!upload) {
        continue;
      }
      if (!fitsUpload(file.size)) {
        upload.state = 'failed';
        upload.message = problemMessage('file_too_large');
        continue;
      }
      upload.state = 'uploading';
      const outcome = await uploadFile(newSchematicUrl(upload.name, folder), file, (fraction) => (upload.progress = fraction));
      if (outcome.kind === 'stored') {
        upload.state = 'done';
      } else {
        upload.state = 'failed';
        upload.message = outcome.kind === 'rejected' ? messageOf(outcome.problem) : 'The upload failed. Check your connection and try again.';
      }
    }
    await Promise.all([loadSidebar(), load(true), refreshAccount()]);
  }

  function hasFiles(event: DragEvent): boolean {
    return event.dataTransfer?.types.includes('Files') ?? false;
  }

  function fileDragOver(event: DragEvent) {
    if (hasFiles(event)) {
      event.preventDefault();
      event.dataTransfer!.dropEffect = 'copy';
      fileDrag = true;
    }
  }

  function fileDragLeave(event: DragEvent) {
    if (!(event.currentTarget as HTMLElement).contains(event.relatedTarget as Node | null)) {
      fileDrag = false;
    }
  }

  function fileDrop(event: DragEvent) {
    fileDrag = false;
    const files = [...(event.dataTransfer?.files ?? [])];
    if (files.length > 0) {
      event.preventDefault();
      void uploadFiles(files, uploadFolder);
    }
  }

  function contentMenu(event: MouseEvent) {
    if ((event.target as HTMLElement).closest('.grid > li, button, a, input')) {
      return;
    }
    openMenu(event, [{ label: 'Upload here…', run: () => pickFiles(uploadFolder) }]);
  }

  function openMenu(event: MouseEvent, items: MenuItem[]) {
    event.preventDefault();
    // The menu key and Shift+F10 report no pointer position, so the menu opens at the focused card instead.
    const box = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const byKeyboard = event.clientX === 0 && event.clientY === 0;
    menu = { x: byKeyboard ? box.left + 8 : event.clientX, y: byKeyboard ? box.top + 8 : event.clientY, items };
  }

  function folderMenu(event: MouseEvent, path: string) {
    openMenu(event, [
      { label: 'Open', run: () => open(path) },
      { label: 'Upload into this folder…', run: () => pickFiles(path) },
      { label: 'Rename or move…', run: () => (renaming = path) },
    ]);
  }

  function schematicMenu(event: MouseEvent, item: SchematicSummary) {
    if (selected.has(item.id) && selected.size > 1) {
      const ids = [...selected];
      openMenu(event, [
        { label: `Move ${ids.length} schematics…`, run: () => (moving = ids) },
        { label: 'Clear selection', run: () => selected.clear() },
      ]);
      return;
    }
    openMenu(event, [
      { label: 'Open', run: () => window.location.assign(schematicUrl(item.id)) },
      { label: 'Rename…', run: () => (renamingSchematic = item) },
      { label: 'Move…', run: () => (moving = [item.id]) },
      { label: 'Upload a new version…', run: () => window.location.assign(uploadUrl(item.id)) },
      { label: 'Delete…', danger: true, run: () => void deleteSchematic(item) },
    ]);
  }

  async function renameSchematic(item: SchematicSummary, name: string): Promise<string | undefined> {
    try {
      const { data, error: problem } = await api.PATCH('/v1/schematics/{schematicId}', { params: { path: { schematicId: item.id } }, body: { name } });
      if (!data) {
        return messageOf(problem);
      }
      items = items.map((other) => (other.id === item.id ? { ...other, name: data.name } : other));
    } catch {
      return 'The schematic could not be renamed. Check your connection and try again.';
    }
    return undefined;
  }

  async function deleteSchematic(item: SchematicSummary) {
    if (!window.confirm(`Delete “${item.name}” with all its versions? This cannot be undone.`)) {
      return;
    }
    error = undefined;
    try {
      const { error: problem, response } = await api.DELETE('/v1/schematics/{schematicId}', { params: { path: { schematicId: item.id } } });
      if (!response.ok) {
        error = messageOf(problem);
        return;
      }
    } catch {
      error = 'The schematic could not be deleted. Check your connection and try again.';
      return;
    }
    selected.delete(item.id);
    await Promise.all([loadSidebar(), load(true), refreshAccount()]);
  }

  function openSidebar() {
    sidebarOpen = true;
    sidebarDialog?.showModal();
  }

  onMount(() => {
    const popped = () => show(parseLibraryView(window.location.search));
    window.addEventListener('popstate', popped);
    void load(true);
    void loadSidebar();
    return () => {
      window.removeEventListener('popstate', popped);
      clearTimeout(timer);
    };
  });
</script>

{#snippet sidebar()}
  {#if folders}
    <FolderTree list={folders} {view} onopen={open} ondropids={dropped} ondropfiles={(folder, files) => uploadFiles(files, folder)} onfoldermenu={folderMenu} />
  {:else}
    <p class="muted">Loading folders…</p>
  {/if}
  {#if tags}
    <TagListView list={tags} chosen={view.tag} onchoose={chooseTag} />
  {/if}
{/snippet}

<div class="toolbar">
  <h1>Library</h1>
  <input type="search" placeholder="Search by name" aria-label="Search by name" bind:value={search} oninput={searchChanged} maxlength="100" />
</div>
<input
  class="picker"
  type="file"
  multiple
  accept=".litematic,.schem,.schematic,.nbt"
  bind:this={picker}
  onchange={(event) => uploadFiles([...(event.currentTarget.files ?? [])], pickerFolder)}
  tabindex="-1"
  aria-hidden="true"
/>

{#if $account}
  <div class="usage"><UsageMeter usedBytes={$account.usedBytes} quotaBytes={$account.quotaBytes} /></div>
{/if}

<div class="layout">
  <aside class="sidebar">
    {@render sidebar()}
  </aside>

  <section
    class="content"
    class:file-drag={fileDrag}
    aria-label="Schematics"
    ondragover={fileDragOver}
    ondragleave={fileDragLeave}
    ondrop={fileDrop}
    oncontextmenu={contentMenu}
  >
    {#if fileDrag}
      <div class="drop-note" aria-hidden="true">Drop to upload into {uploadFolder ? `“${uploadFolder}”` : 'the top level'}</div>
    {/if}
    <div class="location">
      <button type="button" class="sidebar-button" onclick={openSidebar} aria-haspopup="dialog">📁 Folders & tags</button>
      <nav class="crumbs" aria-label="Folder">
        {#if view.all}
          <strong>All schematics</strong>
        {:else}
          {#if crumbs.length === 0}
            <strong>Library</strong>
          {:else}
            <button type="button" class="crumb" onclick={() => open('')}>Library</button>
          {/if}
          {#each crumbs as crumb, index (crumb)}
            <span class="separator" aria-hidden="true">›</span>
            {#if index === crumbs.length - 1}
              <strong aria-current="page">{folderName(crumb)}</strong>
            {:else}
              <button type="button" class="crumb" onclick={() => open(crumb)}>{folderName(crumb)}</button>
            {/if}
          {/each}
        {/if}
        {#if view.tag}
          <span class="tag chosen-tag">#{view.tag} <button type="button" class="clear" onclick={() => chooseTag(undefined)} aria-label={`Clear tag ${view.tag}`}>×</button></span>
        {/if}
      </nav>
    </div>

    {#if !browsing && !view.all}
      <p class="muted scope">Results from {scope}{view.folder ? ' and its subfolders' : ''}.</p>
    {/if}

    {#if items.length > 0}
      <div class="select-bar">
        <label class="select-all">
          <input type="checkbox" checked={allSelected} indeterminate={selected.size > 0 && !allSelected} onchange={toggleAll} />
          Select all loaded
        </label>
        {#if selected.size > 0}
          <strong>{selected.size} selected</strong>
          <span class="muted hint">Right-click to move them, or drag them onto a folder.</span>
        {/if}
      </div>
    {/if}

    {#if error}
      <p class="notice error" role="alert">{error}</p>
    {/if}

    {#if empty}
      {#if !browsing}
        <p class="notice">No schematics match{search.trim() ? ` “${search.trim()}”` : ''}{view.tag ? ` with tag #${view.tag}` : ''} in {scope}.</p>
      {:else if view.folder}
        <p class="notice">“{view.folder}” is empty.</p>
      {:else}
        <div class="card empty">
          <h2>Your library is empty</h2>
          <p>Upload a .litematic, .schem, .schematic or .nbt file to start.</p>
          <p class="muted">The website's library is not connected to the game yet, so schematics from Minecraft do not show up here.</p>
          <button type="button" class="primary" onclick={() => pickFiles(uploadFolder)}>Upload a schematic</button>
          <p class="muted">Or drop files here.</p>
        </div>
      {/if}
    {:else}
      <ul class="grid">
        {#each folderCards as folder (folder.path)}
          <li>
            <button
              type="button"
              class="card folder-card"
              class:drop={dropTarget === folder.path}
              title={folder.path}
              onclick={() => open(folder.path)}
              oncontextmenu={(event) => folderMenu(event, folder.path)}
              ondragover={(event) => dragOverFolder(event, folder.path)}
              ondragleave={() => (dropTarget = undefined)}
              ondrop={(event) => dropOnFolder(event, folder.path)}
            >
              <span class="folder-icon" aria-hidden="true">📁</span>
              <strong class="name">{folderName(folder.path)}</strong>
              <span class="muted">{folder.totalCount === 1 ? '1 schematic' : `${folder.totalCount} schematics`}</span>
            </button>
          </li>
        {/each}
        {#each items as item (item.id)}
          <li
            class="card schematic-card"
            class:checked={selected.has(item.id)}
            title={`Changed ${formatTime(item.updatedAt)}`}
            draggable="true"
            ondragstart={(event) => dragStart(event, item.id)}
            oncontextmenu={(event) => schematicMenu(event, item)}
          >
            <a class="thumb" href={schematicUrl(item.id)} draggable="false" tabindex="-1" aria-hidden="true">
              {#if item.latest.hasThumbnail}
                <img src={thumbnailUrl(item.id, item.latest.number)} alt="" loading="lazy" draggable="false" />
              {:else}
                <span class="placeholder">No thumbnail</span>
              {/if}
            </a>
            <input class="check" type="checkbox" checked={selected.has(item.id)} onchange={() => toggleSelected(item.id)} aria-label={`Select ${item.name}`} />
            <a class="name-line" href={schematicUrl(item.id)} draggable="false">
              <strong class="name">{item.name}</strong>
              <span class="muted">v{item.latest.number}</span>
            </a>
            <span class="facts muted">{formatDimensions(item.latest.dimensions)} · {formatBytes(item.latest.sizeBytes)}</span>
            {#if item.tags.length > 0}
              <span class="card-tags">{#each item.tags as tag (tag)}<span class="tag">{tag}</span>{/each}</span>
            {/if}
          </li>
        {/each}
        {#if browsing}
          <li>
            <button
              type="button"
              class="card upload-card"
              onclick={() => pickFiles(uploadFolder)}
              title="Choose files, or drop them anywhere here"
            >
              <span class="plus" aria-hidden="true">+</span>
              <strong>Upload here</strong>
              <span class="muted">into {uploadFolder ? folderName(uploadFolder) : 'Library'}</span>
            </button>
          </li>
        {/if}
      </ul>
    {/if}

    {#if loading}
      <p class="muted">Loading…</p>
    {:else if nextCursor}
      <div class="more"><button type="button" onclick={() => load(false)}>Load more</button></div>
    {/if}
  </section>
</div>

<dialog class="sidebar-dialog" bind:this={sidebarDialog} onclose={() => (sidebarOpen = false)} aria-label="Folders and tags">
  {#if sidebarOpen}
    {@render sidebar()}
  {/if}
  <div class="dialog-actions">
    <button type="button" onclick={() => sidebarDialog?.close()}>Close</button>
  </div>
</dialog>

{#if moving && folders}
  {@const ids = moving}
  <MoveDialog
    {folders}
    subject={ids.length === 1 ? (items.find((item) => item.id === ids[0])?.name ?? '1 schematic') : `${ids.length} schematics`}
    initial={view.all ? '' : view.folder}
    onmove={(folder) => move(ids, folder)}
    onclose={() => (moving = undefined)}
  />
{/if}

{#if uploads.length > 0}
  <UploadQueue {uploads} ondismiss={() => (uploads = [])} />
{/if}

{#if menu}
  <ContextMenu x={menu.x} y={menu.y} items={menu.items} onclose={() => (menu = undefined)} />
{/if}

{#if renamingSchematic}
  {@const item = renamingSchematic}
  <RenameSchematicDialog name={item.name} onrename={(name) => renameSchematic(item, name)} onclose={() => (renamingSchematic = undefined)} />
{/if}

{#if renaming && folders}
  {@const from = renaming}
  <RenameFolderDialog {folders} path={from} onrename={(to) => renameFolder(from, to)} onclose={() => (renaming = undefined)} />
{/if}

<style>
  .toolbar {
    display: flex;
    align-items: center;
    gap: 1rem;
    flex-wrap: wrap;
  }

  .toolbar h1 {
    margin: 0;
    flex: 0 0 auto;
  }

  .toolbar input {
    flex: 1 1 14rem;
    width: auto;
  }

  .usage {
    margin: 1rem 0;
    max-width: 20rem;
  }

  .layout {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 1.5rem;
  }

  .sidebar {
    display: none;
  }

  .content {
    position: relative;
    min-width: 0;
    min-height: 20rem;
    border-radius: var(--radius);
  }

  .content.file-drag {
    outline: 2px dashed var(--accent);
    outline-offset: 4px;
  }

  .drop-note {
    position: sticky;
    top: 0.5rem;
    z-index: 2;
    margin-bottom: 0.5rem;
    padding: 0.5rem 0.8rem;
    border-radius: var(--radius);
    background: var(--accent);
    color: var(--accent-text);
    text-align: center;
    pointer-events: none;
  }

  .picker {
    display: none;
  }

  .upload-card {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 0.3rem;
    flex: 1 1 auto;
    min-height: 12rem;
    border-style: dashed;
    text-align: center;
  }

  .upload-card:hover {
    border-color: var(--accent);
  }

  .plus {
    font-size: 2.6rem;
    line-height: 1;
    color: var(--accent);
  }

  .location {
    display: flex;
    align-items: center;
    gap: 0.6rem;
    flex-wrap: wrap;
  }

  .crumbs {
    display: flex;
    align-items: center;
    gap: 0.3rem;
    flex-wrap: wrap;
    flex: 1 1 auto;
    min-width: 0;
    font-size: 1.05rem;
  }

  .crumb {
    padding: 0.1rem 0.3rem;
    border: none;
    background: none;
    color: var(--accent);
  }

  .crumb:hover {
    text-decoration: underline;
  }

  .separator {
    color: var(--muted);
  }

  .chosen-tag {
    margin-left: 0.5rem;
  }

  .clear {
    padding: 0 0.2rem;
    border: none;
    background: none;
    font-size: 1rem;
    line-height: 1;
  }

  .scope {
    margin: 0.4rem 0 0;
    font-size: 0.9rem;
  }

  .select-bar {
    position: sticky;
    top: 0;
    z-index: 1;
    display: flex;
    align-items: center;
    gap: 0.6rem;
    flex-wrap: wrap;
    margin-top: 0.8rem;
    padding: 0.4rem 0;
    background: var(--bg);
  }

  .hint {
    font-size: 0.85rem;
  }

  .select-all {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    margin-right: auto;
    font-size: 0.9rem;
  }

  .grid {
    list-style: none;
    padding: 0;
    margin: 0.8rem 0;
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(10rem, 1fr));
    gap: 0.8rem;
  }

  .grid > li {
    display: flex;
  }

  .folder-card,
  .schematic-card {
    flex: 1 1 auto;
    min-width: 0;
    padding: 0.6rem;
  }

  .folder-card {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 0.3rem;
    min-height: 12rem;
    text-align: center;
  }

  .folder-card:hover,
  .schematic-card:hover,
  .schematic-card.checked {
    border-color: var(--accent);
  }

  .folder-card.drop {
    outline: 2px dashed var(--accent);
    outline-offset: -4px;
  }

  .folder-icon {
    font-size: 3rem;
    line-height: 1;
  }

  .schematic-card {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
  }

  .thumb {
    display: block;
    aspect-ratio: 1;
    border-radius: calc(var(--radius) - 2px);
    background: var(--surface-alt);
    overflow: hidden;
  }

  .thumb img {
    width: 100%;
    height: 100%;
    object-fit: contain;
    image-rendering: pixelated;
  }

  .placeholder {
    display: flex;
    height: 100%;
    align-items: center;
    justify-content: center;
    color: var(--muted);
    font-size: 0.85rem;
  }

  .check {
    position: absolute;
    top: 0.9rem;
    left: 0.9rem;
    width: 1.1rem;
    height: 1.1rem;
  }

  .name-line {
    display: flex;
    gap: 0.4rem;
    align-items: baseline;
    min-width: 0;
    color: inherit;
    text-decoration: none;
  }

  .name-line:hover .name {
    text-decoration: underline;
  }

  .name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .facts {
    font-size: 0.85rem;
  }

  .card-tags {
    display: flex;
    flex-wrap: wrap;
    gap: 0.25rem;
  }

  .empty {
    margin-top: 1rem;
    text-align: center;
  }

  .more {
    text-align: center;
  }

  @media (min-width: 48rem) {
    .layout {
      grid-template-columns: 15rem minmax(0, 1fr);
    }

    .sidebar {
      display: block;
      position: sticky;
      top: 1rem;
      align-self: start;
      max-height: calc(100vh - 2rem);
      overflow-y: auto;
    }

    .sidebar-button {
      display: none;
    }
  }
</style>
