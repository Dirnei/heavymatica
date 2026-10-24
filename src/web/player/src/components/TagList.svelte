<script lang="ts">
  import type { TagList } from '../lib/api';

  interface Props {
    list: TagList;
    chosen?: string;
    /** Receives the tag to filter by, or undefined to clear the tag filter. */
    onchoose: (tag: string | undefined) => void;
  }

  const { list, chosen, onchoose }: Props = $props();
</script>

<section class="tags" aria-labelledby="tags-heading">
  <h2 id="tags-heading">Tags</h2>
  {#if list.tags.length === 0}
    <p class="muted">No tags yet. Add them on a schematic's page.</p>
  {:else}
    <ul>
      {#each list.tags as entry (entry.tag)}
        <li>
          <button
            type="button"
            class="tag-button"
            class:chosen={entry.tag === chosen}
            aria-pressed={entry.tag === chosen}
            onclick={() => onchoose(entry.tag === chosen ? undefined : entry.tag)}
          >
            #{entry.tag} <span class="count">{entry.count}</span>
          </button>
        </li>
      {/each}
    </ul>
  {/if}
</section>

<style>
  h2 {
    margin: 1.2rem 0 0.5rem;
    font-size: 0.85rem;
    color: var(--muted);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-wrap: wrap;
    gap: 0.35rem;
  }

  .tag-button {
    padding: 0.1rem 0.6rem;
    border-radius: 999px;
    background: var(--surface-alt);
    border-color: transparent;
    font-size: 0.85rem;
  }

  .tag-button.chosen {
    background: var(--accent);
    color: var(--accent-text);
  }

  .count {
    opacity: 0.7;
  }

  p {
    font-size: 0.85rem;
  }
</style>
