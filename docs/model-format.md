# Canonical Model Format

Schema version: `1.0`

```json
{
  "schemaVersion": "1.0",
  "workspace": {
    "name": "Anonymous Reference Architecture",
    "description": "Architecture workspace"
  },
  "elements": [],
  "relationships": [],
  "views": [],
  "links": [],
  "metadataDefinitions": []
}
```

Element metadata is stored as JSONB and follows this standard shape where present:

```json
{
  "ownership": {},
  "classification": {},
  "lifecycle": {},
  "operations": {},
  "security": {},
  "delivery": {},
  "custom": {}
}
```

Structurizr imports preserve source identity under `metadata.importSource` and in `import_source_mapping`.
