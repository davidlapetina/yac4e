/**
 * Copies text, returning whether it succeeded.
 *
 * navigator.clipboard only exists in a secure context, which a LAN deployment served over plain
 * HTTP is not, so the legacy selection-based path is the one that actually runs there. Callers
 * must handle a false result by offering the value for manual selection.
 */
export async function copyText(value: string): Promise<boolean> {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(value);
      return true;
    } catch {
      // Permission denied or unavailable: fall through to the selection fallback.
    }
  }
  return legacyCopy(value);
}

function legacyCopy(value: string): boolean {
  if (typeof document.execCommand !== 'function') return false;
  const area = document.createElement('textarea');
  area.value = value;
  area.setAttribute('readonly', '');
  // Keep it off screen but still selectable, and avoid scrolling the page.
  area.style.position = 'fixed';
  area.style.top = '0';
  area.style.left = '-9999px';
  document.body.appendChild(area);
  try {
    area.select();
    area.setSelectionRange(0, value.length);
    return document.execCommand('copy');
  } catch {
    return false;
  } finally {
    area.remove();
  }
}
