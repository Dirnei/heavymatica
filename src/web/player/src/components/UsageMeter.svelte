<script lang="ts">
  import { formatBytes, usagePercent } from '../lib/format';

  let { usedBytes, quotaBytes }: { usedBytes: number; quotaBytes: number } = $props();

  const percent = $derived(usagePercent(usedBytes, quotaBytes));
</script>

<div class="usage" title={`${percent.toFixed(0)} % of your storage used`}>
  <span>{formatBytes(usedBytes)} of {formatBytes(quotaBytes)} used</span>
  <div class="meter" class:full={percent >= 95}><span style:width={`${percent}%`}></span></div>
</div>

<style>
  .usage {
    display: grid;
    gap: 0.2rem;
    min-width: 11rem;
    font-size: 0.85rem;
  }
</style>
