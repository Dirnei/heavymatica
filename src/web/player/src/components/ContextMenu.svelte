<script lang="ts">
  import { onMount, tick } from 'svelte';
  import type { MenuItem } from '../lib/menu';

  interface Props {
    x: number;
    y: number;
    items: MenuItem[];
    onclose: () => void;
  }

  const { x, y, items, onclose }: Props = $props();

  let menu: HTMLUListElement;
  let left = $state(0);
  let top = $state(0);

  function buttons(): HTMLButtonElement[] {
    return [...menu.querySelectorAll('button')];
  }

  function choose(item: MenuItem) {
    onclose();
    item.run();
  }

  function keydown(event: KeyboardEvent) {
    const all = buttons();
    const index = all.indexOf(document.activeElement as HTMLButtonElement);
    if (event.key === 'Escape') {
      event.preventDefault();
      onclose();
    } else if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const step = event.key === 'ArrowDown' ? 1 : -1;
      all[(index + step + all.length) % all.length]?.focus();
    } else if (event.key === 'Tab') {
      onclose();
    }
  }

  function outside(event: Event) {
    if (!menu.contains(event.target as Node)) {
      onclose();
    }
  }

  onMount(() => {
    // Keep the menu inside the window when it opens near an edge.
    left = Math.max(4, Math.min(x, window.innerWidth - menu.offsetWidth - 4));
    top = Math.max(4, Math.min(y, window.innerHeight - menu.offsetHeight - 4));
    void tick().then(() => buttons()[0]?.focus());
    const options = { capture: true };
    window.addEventListener('pointerdown', outside, options);
    window.addEventListener('scroll', onclose, options);
    window.addEventListener('resize', onclose);
    window.addEventListener('blur', onclose);
    return () => {
      window.removeEventListener('pointerdown', outside, options);
      window.removeEventListener('scroll', onclose, options);
      window.removeEventListener('resize', onclose);
      window.removeEventListener('blur', onclose);
    };
  });
</script>

<ul class="menu" role="menu" bind:this={menu} style:left={`${left}px`} style:top={`${top}px`} onkeydown={keydown} tabindex="-1">
  {#each items as item (item.label)}
    <li role="none">
      <button type="button" role="menuitem" class:danger={item.danger} onclick={() => choose(item)}>{item.label}</button>
    </li>
  {/each}
</ul>

<style>
  .menu {
    position: fixed;
    z-index: 10;
    min-width: 11rem;
    margin: 0;
    padding: 0.3rem;
    list-style: none;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    box-shadow: 0 6px 20px rgb(0 0 0 / 0.25);
  }

  button {
    width: 100%;
    justify-content: flex-start;
    padding: 0.35rem 0.6rem;
    border: none;
    border-radius: calc(var(--radius) - 3px);
    background: none;
    text-align: left;
  }

  button:hover,
  button:focus-visible {
    background: var(--surface-alt);
    outline: none;
  }
</style>
