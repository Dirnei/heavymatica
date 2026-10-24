<script lang="ts">
  import type { FolderList } from '../lib/api';
  import { buildTree, dragType, parentsOf, type FolderNode } from '../lib/folders';
  import type { LibraryView } from '../lib/navigation';

  interface Props {
    list: FolderList;
    view: LibraryView;
    /** Opens a folder (`''` for the top level) or, with `all`, the flat view of everything. */
    onopen: (folder: string, all: boolean) => void;
    /** Receives the target folder ('' for no folder) and the dropped schematic IDs. */
    ondropids?: (folder: string, ids: string[]) => void;
    /** Receives files dropped onto a folder ('' for the top level), to upload them there. */
    ondropfiles?: (folder: string, files: File[]) => void;
    /** Opens the folder's right-click menu. */
    onfoldermenu?: (event: MouseEvent, path: string) => void;
  }

  const { list, view, onopen, ondropids, ondropfiles, onfoldermenu }: Props = $props();

  const storageKey = 'heavymatica.folders.expanded';
  const tree = $derived(buildTree(list.folders));
  const total = $derived(list.unfiledCount + tree.reduce((sum, node) => sum + node.totalCount, 0));
  const chosenPath = $derived(!view.all && view.folder ? view.folder : undefined);
  const chosenParents = $derived(chosenPath ? parentsOf(chosenPath) : []);

  let expanded = $state(readExpanded());
  let dropTarget = $state<string>();

  function readExpanded(): Set<string> {
    try {
      const stored: unknown = JSON.parse(localStorage.getItem(storageKey) ?? '[]');
      return new Set(Array.isArray(stored) ? stored.filter((value): value is string => typeof value === 'string') : []);
    } catch {
      return new Set();
    }
  }

  function isOpen(path: string): boolean {
    return expanded.has(path) || chosenParents.includes(path);
  }

  function toggle(path: string) {
    const next = new Set(expanded);
    if (isOpen(path)) {
      next.delete(path);
      if (chosenParents.includes(path)) {
        // The chosen folder's parents stay open, so collapsing one moves the choice up to it.
        onopen(path, false);
      }
    } else {
      next.add(path);
    }
    expanded = next;
    try {
      localStorage.setItem(storageKey, JSON.stringify([...next]));
    } catch {
      // Without storage the expanded folders reset on reload.
    }
  }

  function dragOver(event: DragEvent, target: string) {
    const types = event.dataTransfer?.types ?? [];
    if ((ondropids && types.includes(dragType)) || (ondropfiles && types.includes('Files'))) {
      event.preventDefault();
      event.dataTransfer!.dropEffect = types.includes('Files') ? 'copy' : 'move';
      dropTarget = target;
    }
  }

  function drop(event: DragEvent, target: string) {
    dropTarget = undefined;
    const files = [...(event.dataTransfer?.files ?? [])];
    if (ondropfiles && files.length > 0) {
      event.preventDefault();
      event.stopPropagation();
      ondropfiles(target, files);
      return;
    }
    const data = event.dataTransfer?.getData(dragType);
    if (!ondropids || !data) {
      return;
    }
    event.preventDefault();
    try {
      const ids: unknown = JSON.parse(data);
      if (Array.isArray(ids)) {
        const strings = ids.filter((id): id is string => typeof id === 'string');
        if (strings.length > 0) {
          ondropids(target, strings);
        }
      }
    } catch {
      // Not a drag from the library list.
    }
  }
</script>

{#snippet row(node: FolderNode, depth: number)}
  {@const chosen = chosenPath === node.path}
  {@const open = isOpen(node.path)}
  <li>
    <div class="row" class:chosen class:drop={dropTarget === node.path} style:--depth={depth}>
      {#if node.children.length > 0}
        <button type="button" class="toggle" aria-expanded={open} aria-label={`${open ? 'Collapse' : 'Expand'} ${node.name}`} onclick={() => toggle(node.path)}>
          {open ? '▾' : '▸'}
        </button>
      {:else}
        <span class="toggle" aria-hidden="true"></span>
      {/if}
      <button
        type="button"
        class="pick"
        aria-current={chosen ? 'true' : undefined}
        title={node.path}
        onclick={() => onopen(node.path, false)}
        oncontextmenu={(event) => onfoldermenu?.(event, node.path)}
        ondragover={(event) => dragOver(event, node.path)}
        ondragleave={() => (dropTarget = undefined)}
        ondrop={(event) => drop(event, node.path)}
      >
        <span class="name">{node.name}</span>
        <span class="count">{node.totalCount}</span>
      </button>
    </div>
    {#if node.children.length > 0 && open}
      <ul>
        {#each node.children as child (child.path)}
          {@render row(child, depth + 1)}
        {/each}
      </ul>
    {/if}
  </li>
{/snippet}

<nav class="tree" aria-label="Folders">
  <ul>
    <li>
      <div class="row" class:chosen={view.all} style:--depth={0}>
        <span class="toggle" aria-hidden="true"></span>
        <button type="button" class="pick" aria-current={view.all ? 'true' : undefined} onclick={() => onopen('', true)}>
          <span class="name">All schematics</span>
          <span class="count">{total}</span>
        </button>
      </div>
    </li>
    <li>
      <div class="row" class:chosen={!view.all && view.folder === ''} class:drop={dropTarget === ''} style:--depth={0}>
        <span class="toggle" aria-hidden="true"></span>
        <button
          type="button"
          class="pick"
          aria-current={!view.all && view.folder === '' ? 'true' : undefined}
          title="Top-level folders and the schematics in no folder"
          onclick={() => onopen('', false)}
          ondragover={(event) => dragOver(event, '')}
          ondragleave={() => (dropTarget = undefined)}
          ondrop={(event) => drop(event, '')}
        >
          <span class="name">Library</span>
          <span class="count">{list.unfiledCount}</span>
        </button>
      </div>
    </li>
    {#each tree as node (node.path)}
      {@render row(node, 0)}
    {/each}
  </ul>
</nav>

<style>
  ul {
    list-style: none;
    margin: 0;
    padding: 0;
  }

  .row {
    display: flex;
    align-items: center;
    gap: 0.15rem;
    padding-left: calc(var(--depth) * 0.9rem);
    border-radius: var(--radius);
  }

  .row.chosen {
    background: var(--surface-alt);
  }

  .row.drop {
    outline: 2px dashed var(--accent);
    outline-offset: -2px;
  }

  .toggle {
    flex: 0 0 1.4rem;
    width: 1.4rem;
    height: 1.6rem;
    padding: 0;
    border: none;
    background: none;
    color: var(--muted);
  }

  .pick {
    flex: 1 1 auto;
    min-width: 0;
    justify-content: space-between;
    padding: 0.25rem 0.4rem;
    border: none;
    background: none;
    text-align: left;
  }

  .chosen .pick {
    font-weight: 600;
  }

  .name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .count {
    flex: 0 0 auto;
    color: var(--muted);
    font-size: 0.85em;
    font-variant-numeric: tabular-nums;
  }

  .toggle:hover {
    color: var(--text);
  }
</style>
