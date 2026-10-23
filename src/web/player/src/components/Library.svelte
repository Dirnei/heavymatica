<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type SchematicSummary } from '../lib/api';
  import { formatBytes, formatDimensions, formatTime } from '../lib/format';
  import { schematicUrl, uploadUrl } from '../lib/navigation';
  import { messageOf } from '../lib/problems';
  import { account } from '../lib/session';
  import UsageMeter from './UsageMeter.svelte';

  const pageSize = 50;
  const searchDelayMs = 250;

  let search = $state('');
  let items = $state<SchematicSummary[]>([]);
  let nextCursor = $state<string | null>(null);
  let loading = $state(false);
  let loaded = $state(false);
  let error = $state<string>();
  let generation = 0;
  let timer: ReturnType<typeof setTimeout> | undefined;

  async function load(reset: boolean) {
    const current = reset ? ++generation : generation;
    loading = true;
    error = undefined;
    const q = search.trim() || undefined;
    const cursor = reset ? undefined : (nextCursor ?? undefined);
    try {
      const { data, error: problem } = await api.GET('/v1/schematics', { params: { query: { q, cursor, limit: pageSize } } });
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

  function searchChanged() {
    clearTimeout(timer);
    timer = setTimeout(() => void load(true), searchDelayMs);
  }

  onMount(() => {
    void load(true);
    return () => clearTimeout(timer);
  });
</script>

<div class="toolbar">
  <h1>Library</h1>
  <input type="search" placeholder="Search by name" aria-label="Search by name" bind:value={search} oninput={searchChanged} maxlength="100" />
  <a class="button primary" href={uploadUrl()}>Upload</a>
</div>

{#if $account}
  <div class="usage"><UsageMeter usedBytes={$account.usedBytes} quotaBytes={$account.quotaBytes} /></div>
{/if}

{#if error}
  <p class="notice error" role="alert">{error}</p>
{/if}

{#if loaded && items.length === 0}
  {#if search.trim()}
    <p class="notice">No schematics match “{search.trim()}”.</p>
  {:else}
    <div class="card empty">
      <h2>Your library is empty</h2>
      <p>Upload a .litematic, .schem, .schematic or .nbt file to start.</p>
      <p class="muted">The website's library is not connected to the game yet, so schematics from Minecraft do not show up here.</p>
      <a class="button primary" href={uploadUrl()}>Upload a schematic</a>
    </div>
  {/if}
{:else}
  <ul class="list">
    {#each items as item (item.id)}
      <li>
        <a class="card entry" href={schematicUrl(item.id)}>
          <span class="title">
            <strong>{item.name}</strong>
            <span class="muted">v{item.latest.number}</span>
          </span>
          <span class="meta">
            {#if item.folder}<span class="folder">{item.folder}</span>{/if}
            {#each item.tags as tag (tag)}<span class="tag">{tag}</span>{/each}
          </span>
          <span class="facts muted">
            <span>{formatDimensions(item.latest.dimensions)}</span>
            <span>{formatBytes(item.latest.sizeBytes)}</span>
            <span>Changed {formatTime(item.updatedAt)}</span>
          </span>
        </a>
      </li>
    {/each}
  </ul>
{/if}

{#if loading}
  <p class="muted">Loading…</p>
{:else if nextCursor}
  <div class="more"><button type="button" onclick={() => load(false)}>Load more</button></div>
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

  .list {
    list-style: none;
    padding: 0;
    margin: 1rem 0;
    display: grid;
    gap: 0.6rem;
  }

  .entry {
    display: grid;
    grid-template-columns: minmax(10rem, 2fr) minmax(6rem, 1.5fr) auto;
    gap: 0.4rem 1rem;
    align-items: center;
    color: inherit;
    text-decoration: none;
  }

  .entry:hover {
    border-color: var(--accent);
  }

  .title {
    display: flex;
    gap: 0.5rem;
    align-items: baseline;
    min-width: 0;
  }

  .title strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .meta {
    display: flex;
    gap: 0.35rem;
    flex-wrap: wrap;
    align-items: center;
  }

  .folder::before {
    content: '📁 ';
  }

  .facts {
    display: flex;
    gap: 1rem;
    font-size: 0.85rem;
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .empty {
    margin-top: 1rem;
    text-align: center;
  }

  .more {
    text-align: center;
  }

  @media (max-width: 720px) {
    .entry {
      grid-template-columns: 1fr;
    }

    .facts {
      justify-content: flex-start;
    }
  }
</style>
