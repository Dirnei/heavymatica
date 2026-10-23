<script lang="ts">
  import { onMount } from 'svelte';
  import { api, type UploadResult } from '../lib/api';
  import { fileBaseName, fitsUpload } from '../lib/files';
  import { formatBytes } from '../lib/format';
  import { libraryPath, schematicUrl } from '../lib/navigation';
  import { codeOf, messageOf, problemMessage } from '../lib/problems';
  import { account, refreshAccount } from '../lib/session';
  import { newSchematicUrl, newVersionUrl, uploadFile } from '../lib/upload';

  const schematicId = new URLSearchParams(window.location.search).get('schematic') ?? undefined;

  let target = $state<{ name: string; latest: number }>();
  let progress = $state<number>();
  let uploading = $state(false);
  let dragging = $state(false);
  let error = $state<string>();
  let overQuota = $state(false);
  let result = $state<{ uploaded: UploadResult; unchanged: boolean; name: string }>();
  let input = $state<HTMLInputElement>();

  onMount(async () => {
    if (!schematicId) {
      return;
    }
    const { data, error: problem } = await api.GET('/v1/schematics/{schematicId}', { params: { path: { schematicId } } });
    if (data) {
      target = { name: data.name, latest: data.versions.at(-1)?.number ?? 0 };
    } else {
      error = messageOf(problem);
    }
  });

  async function send(file: File | undefined) {
    if (!file || uploading) {
      return;
    }
    error = undefined;
    overQuota = false;
    result = undefined;
    if (!fitsUpload(file.size)) {
      error = problemMessage('file_too_large');
      return;
    }

    const name = fileBaseName(file.name);
    const previousLatest = target?.latest;
    uploading = true;
    progress = 0;
    try {
      const outcome = await uploadFile(schematicId ? newVersionUrl(schematicId) : newSchematicUrl(name), file, (fraction) => (progress = fraction));
      if (outcome.kind === 'stored') {
        const unchanged = !!schematicId && (outcome.status === 200 || outcome.result.version.number === previousLatest);
        result = { uploaded: outcome.result, unchanged, name: target?.name ?? name };
        if (target) {
          target = { ...target, latest: outcome.result.version.number };
        }
      } else if (outcome.kind === 'rejected') {
        error = messageOf(outcome.problem);
        overQuota = codeOf(outcome.problem) === 'over_quota';
      } else {
        error = 'The upload failed. Check your connection and try again.';
      }
      await refreshAccount();
    } finally {
      uploading = false;
      progress = undefined;
      if (input) {
        input.value = '';
      }
    }
  }

  function dropped(event: DragEvent) {
    event.preventDefault();
    dragging = false;
    void send(event.dataTransfer?.files[0]);
  }
</script>

<p>
  {#if schematicId}
    <a href={schematicUrl(schematicId)}>← {target?.name ?? 'Schematic'}</a>
  {:else}
    <a href={libraryPath}>← Library</a>
  {/if}
</p>

<h1>{schematicId ? `New version of ${target?.name ?? '…'}` : 'Upload a schematic'}</h1>

<label
  class="drop card"
  class:dragging
  ondragover={(event) => {
    event.preventDefault();
    dragging = true;
  }}
  ondragleave={() => (dragging = false)}
  ondrop={dropped}
>
  <input
    bind:this={input}
    type="file"
    accept=".litematic,.schem,.schematic,.nbt"
    disabled={uploading}
    onchange={(event) => send(event.currentTarget.files?.[0])}
  />
  <strong>Choose a file or drop it here</strong>
  <span class="muted">.litematic, .schem, .schematic or .nbt, at most 16 MiB</span>
</label>

{#if progress !== undefined}
  <div class="progress">
    <div class="meter"><span style:width={`${Math.round(progress * 100)}%`}></span></div>
    <span class="muted">{progress < 1 ? `Uploading… ${Math.round(progress * 100)} %` : 'Checking the file…'}</span>
  </div>
{/if}

{#if error}
  <div class="notice error" role="alert">
    <p>{error}</p>
    {#if overQuota && $account}
      <p>{formatBytes($account.usedBytes)} of {formatBytes($account.quotaBytes)} used. Nothing was stored.</p>
    {/if}
  </div>
{/if}

{#if result}
  <div class="notice" class:success={!result.unchanged} role="status">
    {#if result.unchanged}
      <p>The file is unchanged: it is identical to version {result.uploaded.version.number}, so no version was added.</p>
    {:else if schematicId}
      <p>Uploaded version {result.uploaded.version.number} of {result.name}.</p>
    {:else}
      <p>Uploaded {result.name} as version {result.uploaded.version.number}.</p>
    {/if}
    <a class="button" href={schematicUrl(result.uploaded.schematicId)}>Open {result.name}</a>
  </div>
{/if}

<style>
  .drop {
    display: grid;
    gap: 0.4rem;
    place-items: center;
    padding: 3rem 1rem;
    border: 2px dashed var(--border);
    text-align: center;
    cursor: pointer;
  }

  .drop.dragging {
    border-color: var(--accent);
    background: var(--surface-alt);
  }

  .progress {
    display: grid;
    gap: 0.3rem;
    margin: 1rem 0;
  }

  .notice {
    margin-top: 1rem;
  }

  .notice p {
    margin: 0 0 0.5rem;
  }
</style>
