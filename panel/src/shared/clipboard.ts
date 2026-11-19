// Panoya kopyalama. copyRich: HTML + duz metin birlikte (yapistirilan yer bicimi destekliyorsa HTML, yoksa duz metin kullanilir).
// Eski tarayicilarda (ClipboardItem yok) secim + execCommand ile.

export async function copyRich(html: string, text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.write && typeof ClipboardItem !== "undefined") {
      await navigator.clipboard.write([new ClipboardItem({
        "text/html": new Blob([html], { type: "text/html" }),
        "text/plain": new Blob([text], { type: "text/plain" }),
      })]);
      return true;
    }
  } catch {
    // asagidaki yontemi dene
  }
  const el = document.createElement("div");
  el.contentEditable = "true";
  el.innerHTML = html;
  Object.assign(el.style, { position: "fixed", left: "-9999px", top: "0" });
  document.body.appendChild(el);
  try {
    const range = document.createRange();
    range.selectNodeContents(el);
    const selection = window.getSelection();
    selection?.removeAllRanges();
    selection?.addRange(range);
    const ok = document.execCommand("copy");
    selection?.removeAllRanges();
    return ok;
  } finally {
    el.remove();
  }
}

export async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // asagidaki yontemi dene
  }
  const el = document.createElement("textarea");
  el.value = text;
  Object.assign(el.style, { position: "fixed", left: "-9999px", top: "0" });
  document.body.appendChild(el);
  try {
    el.select();
    return document.execCommand("copy");
  } finally {
    el.remove();
  }
}

/** Telefonda paylasim penceresi (Fotograflara kaydet), olmazsa indirme. */
export async function saveFile(blob: Blob, name: string): Promise<void> {
  const file = new File([blob], name, { type: blob.type });
  if (navigator.canShare?.({ files: [file] })) {
    try {
      await navigator.share({ files: [file] });
      return;
    } catch (e) {
      if (e instanceof DOMException && e.name === "AbortError") return;   // kullanici vazgecti
    }
  }
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
