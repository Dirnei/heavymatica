<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type SchematicDetails } from '../lib/api';
  import { blockName, sortBlockCounts } from '../lib/blocks';
  import { formatBytes, formatCount, formatDimensions, formatTime } from '../lib/format';
  import { libraryPath, uploadUrl } from '../lib/navigation';
  import { messageOf, problemMessage } from '../lib/problems';
  import { refreshAccount } from '../lib/session';

  const id = new URLSearchParams(window.location.search).get('id') ?? '';

  let details = $state<SchematicDetails>();
  let missing = $state(false);
  let error = $state<string>();
  let selected = $state<number>();
  let thumbnail = $state<string>();
  let thumbnailFor = $state<number>();
  let renaming = $state(false);
  let newName = $state('');
  let busy = $state(false);

  const version = $derived(details?.versions.find((candidate) => candidate.number === selected));
  const blocks = $derived(version ? sortBlockCounts(version.blockCounts) : []);
  const formats: Record<string, string> = { litematica: 'Litematica', sponge: 'Sponge schematic', structure: 'Structure (NBT)' };

  async function load() {
    if (!id) {
      missing = true;
      return;
    }
    try {
      const { data, error: problem, response } = await api.GET('/v1/schematics/{schematicId}', { params: { path: { schematicId: id } } });
      if (!data) {
        missing = response.status === 404;
        error = missing ? undefined : messageOf(problem);
        return;
      }
      details = data;
      if (!data.versions.some((candidate) => candidate.number === selected)) {
        selected = data.versions.at(-1)?.number;
      }
    } catch {
      error = 'The schematic could not be loaded. Check your connection and try again.';
    }
  }

  async function links(number: number) {
    const { data, error: problem } = await api.POST('/v1/schematics/{schematicId}/versions/{versionNumber}/links', {
      params: { path: { schematicId: id, versionNumber: number } },
    });
    if (!data) {
      error = messageOf(problem);
    }
    return data;
  }

  $effect(() => {
    const current = version;
    thumbnail = undefined;
    thumbnailFor = current?.number;
    if (current?.hasThumbnail) {
      void links(current.number).then((data) => {
        if (thumbnailFor === current.number) {
          thumbnail = data?.thumbnailUrl ?? undefined;
        }
      });
    }
  });

  async function download() {
    if (!version) {
      return;
    }
    busy = true;
    try {
      const data = await links(version.number);
      if (data) {
        window.location.assign(data.fileUrl);
      }
    } finally {
      busy = false;
    }
  }

  function startRename() {
    newName = details?.name ?? '';
    renaming = true;
  }

  async function rename(event: SubmitEvent) {
    event.preventDefault();
    const name = newName.trim();
    if (!details || !name || name === details.name) {
      renaming = false;
      return;
    }
    busy = true;
    error = undefined;
    try {
      const { data, error: problem } = await api.PATCH('/v1/schematics/{schematicId}', {
        params: { path: { schematicId: id } },
        body: { name },
      });
      if (data) {
        details = data;
        renaming = false;
      } else {
        error = messageOf(problem);
      }
    } finally {
      busy = false;
    }
  }

  async function deleteSchematic() {
    if (!details) {
      return;
    }
    const count = details.versions.length;
    if (!window.confirm(`Delete “${details.name}” with ${count === 1 ? 'its only version' : `all ${count} versions`}? This cannot be undone.`)) {
      return;
    }
    busy = true;
    error = undefined;
    try {
      const { error: problem, response } = await api.DELETE('/v1/schematics/{schematicId}', { params: { path: { schematicId: id } } });
      if (response.ok) {
        window.location.assign(libraryPath);
        return;
      }
      error = messageOf(problem);
    } finally {
      busy = false;
    }
  }

  async function deleteVersion(number: number) {
    if (!details || !window.confirm(`Delete version ${number} of “${details.name}”? This cannot be undone.`)) {
      return;
    }
    busy = true;
    error = undefined;
    try {
      const { error: problem, response } = await api.DELETE('/v1/schematics/{schematicId}/versions/{versionNumber}', {
        params: { path: { schematicId: id, versionNumber: number } },
      });
      if (!response.ok) {
        error = messageOf(problem);
      }
      await load();
      await refreshAccount();
    } finally {
      busy = false;
    }
  }

  onMount(() => {
    void load();
  });
</script>

<p><a href={libraryPath}>← Library</a></p>

{#if error}
  <p class="notice error" role="alert">{error}</p>
{/if}

{#if missing}
  <p class="notice">{problemMessage('not_found')}</p>
{:else if details}
  <header class="head">
    {#if renaming}
      <form class="rename" onsubmit={rename}>
        <input type="text" bind:value={newName} maxlength="100" required aria-label="New name" />
        <button type="submit" class="primary" disabled={busy}>Save</button>
        <button type="button" onclick={() => (renaming = false)}>Cancel</button>
      </form>
    {:else}
      <h1>{details.name}</h1>
      <div class="head-actions">
        <button type="button" onclick={startRename} disabled={busy}>Rename</button>
        <a class="button" href={uploadUrl(id)}>Upload new version</a>
        <button type="button" class="danger" onclick={deleteSchematic} disabled={busy}>Delete schematic</button>
      </div>
    {/if}
    <div class="meta">
      {#if details.folder}<span>📁 {details.folder}</span>{/if}
      {#each details.tags as tag (tag)}<span class="tag">{tag}</span>{/each}
      <span class="muted">Changed {formatTime(details.updatedAt)}</span>
    </div>
  </header>

  {#if version}
    <section class="version">
      <div class="preview card">
        {#if thumbnail}
          <img src={thumbnail} alt={`Thumbnail of ${details.name}, version ${version.number}`} />
        {:else}
          <div class="placeholder" aria-label="No thumbnail">
            <span>No thumbnail</span>
          </div>
        {/if}
      </div>
      <div class="card facts">
        <h2>Version {version.number}</h2>
        <dl>
          <dt>Uploaded</dt><dd>{formatTime(version.createdAt)}</dd>
          <dt>Format</dt><dd>{formats[version.format] ?? version.format}</dd>
          <dt>Size</dt><dd>{formatBytes(version.sizeBytes)}</dd>
          <dt>Dimensions</dt><dd>{formatDimensions(version.dimensions)}</dd>
          <dt>Data version</dt><dd>{version.dataVersion ?? 'not recorded'}</dd>
          <dt>Blocks</dt><dd>{formatCount(version.blockTotal)}</dd>
        </dl>
        <button type="button" class="primary" onclick={download} disabled={busy}>Download</button>
      </div>
    </section>

    <section class="card">
      <h2>Blocks of version {version.number}</h2>
      {#if blocks.length === 0}
        <p class="muted">No blocks.</p>
      {:else}
        <ul class="blocks">
          {#each blocks as block (block.block)}
            <li>
              <span title={block.block}>{blockName(block.block)}</span>
              <span class="count">{formatCount(block.count)}</span>
            </li>
          {/each}
        </ul>
      {/if}
    </section>
  {/if}

  <section class="card versions">
    <h2>Versions</h2>
    <div class="table">
      <table>
        <thead>
          <tr>
            <th>Version</th>
            <th>Uploaded</th>
            <th>Format</th>
            <th>Size</th>
            <th>Dimensions</th>
            <th>Data version</th>
            <th>Blocks</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {#each [...details.versions].reverse() as row (row.number)}
            <tr class:selected={row.number === selected}>
              <td>
                <button type="button" class="pick" onclick={() => (selected = row.number)} aria-pressed={row.number === selected}>
                  v{row.number}
                </button>
              </td>
              <td>{formatTime(row.createdAt)}</td>
              <td>{formats[row.format] ?? row.format}</td>
              <td>{formatBytes(row.sizeBytes)}</td>
              <td>{formatDimensions(row.dimensions)}</td>
              <td>{row.dataVersion ?? '–'}</td>
              <td>{formatCount(row.blockTotal)}</td>
              <td>
                {#if details.versions.length > 1}
                  <button type="button" class="danger" onclick={() => deleteVersion(row.number)} disabled={busy}>Delete</button>
                {/if}
              </td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  </section>
{:else if !error}
  <p class="muted">Loading…</p>
{/if}

<style>
  .head {
    display: grid;
    gap: 0.6rem;
    margin-bottom: 1.2rem;
  }

  .head h1 {
    margin: 0;
    overflow-wrap: anywhere;
  }

  .head-actions,
  .rename,
  .meta {
    display: flex;
    gap: 0.5rem;
    flex-wrap: wrap;
    align-items: center;
  }

  .rename input {
    flex: 1 1 16rem;
    width: auto;
  }

  .version {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 1rem;
    margin-bottom: 1rem;
  }

  .preview {
    display: grid;
    place-items: center;
    min-height: 16rem;
  }

  .preview img {
    max-width: 100%;
    image-rendering: pixelated;
  }

  .placeholder {
    display: grid;
    place-items: center;
    width: 100%;
    height: 100%;
    min-height: 14rem;
    border: 2px dashed var(--border);
    border-radius: var(--radius);
    color: var(--muted);
  }

  .facts h2,
  section h2 {
    margin-top: 0;
    font-size: 1.15rem;
  }

  dl {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: 0.25rem 1rem;
    margin: 0 0 1rem;
  }

  dt {
    color: var(--muted);
  }

  dd {
    margin: 0;
  }

  .blocks {
    list-style: none;
    margin: 0;
    padding: 0;
    columns: 16rem;
    column-gap: 2rem;
  }

  .blocks li {
    display: flex;
    justify-content: space-between;
    gap: 1rem;
    padding: 0.15rem 0;
    break-inside: avoid;
    border-bottom: 1px solid var(--border);
  }

  .count {
    font-family: var(--mono);
  }

  .versions {
    margin-top: 1rem;
  }

  .table {
    overflow-x: auto;
  }

  tr.selected {
    background: var(--surface-alt);
  }

  .pick {
    padding: 0.15rem 0.6rem;
  }

  .pick[aria-pressed='true'] {
    border-color: var(--accent);
    font-weight: 600;
  }

  @media (max-width: 720px) {
    .version {
      grid-template-columns: 1fr;
    }
  }
</style>
