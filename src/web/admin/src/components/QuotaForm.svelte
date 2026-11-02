<script lang="ts">
  import { untrack } from 'svelte';
  import { api, type AccountOverview } from '../lib/api';
  import { formatBytes } from '../lib/format';
  import { messageOf, offlineMessage } from '../lib/problems';
  import { parseQuota, quotaInput, reasonMaxLength, reasonProblem, type QuotaUnit } from '../lib/quota';
  import Notice from './Notice.svelte';

  let { account, onchange }: { account: AccountOverview; onchange: (account: AccountOverview) => void } = $props();

  const initial = untrack(() => quotaInput(account.quotaBytes));
  let value = $state(initial.value);
  let unit = $state<QuotaUnit>(initial.unit);
  let reason = $state('');
  let busy = $state(false);
  let error = $state<string>();
  let saved = $state<string>();

  const parsed = $derived(parseQuota(value, unit));
  const belowUsage = $derived(parsed.ok && parsed.bytes < account.usedBytes);

  async function save(event: SubmitEvent) {
    event.preventDefault();
    error = undefined;
    saved = undefined;
    if (!parsed.ok) {
      error = parsed.message;
      return;
    }
    const problem = reasonProblem(reason);
    if (problem) {
      error = problem;
      return;
    }
    busy = true;
    try {
      const { data, error: answer } = await api.PUT('/v1/accounts/{accountId}/quota', {
        params: { path: { accountId: account.id } },
        body: { quotaBytes: parsed.bytes, reason: reason.trim() },
      });
      if (data) {
        onchange(data);
        reason = '';
        saved = `The quota is now ${formatBytes(data.quotaBytes)}.`;
      } else {
        error = messageOf(answer);
      }
    } catch {
      error = offlineMessage;
    }
    busy = false;
  }
</script>

<form class="card form" onsubmit={save}>
  <h2>Change the quota</h2>
  <div class="amount">
    <label class="field">
      <span>New quota</span>
      <input type="text" inputmode="decimal" bind:value autocomplete="off" required aria-describedby="quota-hint" />
    </label>
    <label class="field">
      <span>Unit</span>
      <select bind:value={unit}>
        <option value="MiB">MiB</option>
        <option value="GiB">GiB</option>
      </select>
    </label>
  </div>
  <span id="quota-hint" class="hint">
    0 to 100 GiB.
    {#if parsed.ok}That is {formatBytes(parsed.bytes)}.{/if}
  </span>
  {#if belowUsage}
    <p class="notice warning">
      This is below the current usage of {formatBytes(account.usedBytes)}. The account keeps its schematics, but further
      uploads are refused.
    </p>
  {/if}
  <label class="field">
    <span>Reason</span>
    <input type="text" bind:value={reason} minlength="3" maxlength={reasonMaxLength} required autocomplete="off" />
    <span class="hint">3 to {reasonMaxLength} characters, kept in the audit log. {reason.trim().length}/{reasonMaxLength}</span>
  </label>
  <Notice message={error} />
  <Notice tone="success" message={saved} />
  <div class="actions">
    <button type="submit" class="primary" disabled={busy}>Save the quota</button>
  </div>
</form>

<style>
  .amount {
    display: grid;
    grid-template-columns: minmax(8rem, 14rem) 7rem;
    gap: 0.75rem;
  }
</style>
