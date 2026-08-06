# Export Design

Frontend export is isolated behind:

```ts
interface DiagramExportService {
  exportSvg(options: SvgExportOptions): Promise<Blob>;
  exportPng(options: PngExportOptions): Promise<Blob>;
}
```

The service waits for fonts, calculates bounds across rendered React Flow nodes, adds margins, supports white or transparent backgrounds, and downloads complete-view SVG/PNG output. PNG uses `html-to-image` with a default pixel ratio of at least 2.

Server-side export can be added by implementing the same semantic options on the backend without changing editor callers.
