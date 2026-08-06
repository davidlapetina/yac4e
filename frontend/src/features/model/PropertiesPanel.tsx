import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertCircle, AlertTriangle, Info, Link2, Plus, Save, Trash2 } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { useForm, type UseFormRegisterReturn } from 'react-hook-form';
import { api } from '../../api/client';
import { useEditorStore } from '../../stores/editorStore';
import type { ArchitectureElement, ArchitectureRelationship, MetadataDefinition, ValidationIssue } from '../../types/model';
import { elementFormSchema, linkFormSchema, type ElementFormValues, type LinkFormValues } from '../../validation/schemas';

interface Props {
  workspaceId: string;
  elements: ArchitectureElement[];
  relationships: ArchitectureRelationship[];
  metadataDefinitions: MetadataDefinition[];
  issues: ValidationIssue[];
}

export function PropertiesPanel({ workspaceId, elements, relationships, metadataDefinitions, issues }: Props) {
  const store = useEditorStore();
  const element = elements.find((item) => item.id === store.selectedElementId);
  const relationship = relationships.find((item) => item.id === store.selectedRelationshipId);
  const selectedIssues = issues.filter((issue) =>
    (element && issue.elementId === element.id) || (relationship && issue.relationshipId === relationship.id)
  );
  return (
    <aside className="right-panel">
      <div className="panel-title">Properties</div>
      {selectedIssues.length > 0 && <SelectedValidationIssues issues={selectedIssues} />}
      {element && <ElementProperties workspaceId={workspaceId} element={element} elements={elements} definitions={metadataDefinitions} />}
      {relationship && <RelationshipProperties workspaceId={workspaceId} relationship={relationship} elements={elements} />}
      {!element && !relationship && <div className="empty-state">Select an element or relationship.</div>}
    </aside>
  );
}

function SelectedValidationIssues({ issues }: { issues: ValidationIssue[] }) {
  return (
    <section className="selected-validation">
      {issues.map((issue) => {
        const Icon = issue.severity === 'ERROR' ? AlertCircle : issue.severity === 'WARNING' ? AlertTriangle : Info;
        return (
          <div key={`${issue.code}-${issue.message}`} className={`selected-validation-item ${issue.severity.toLowerCase()}`}>
            <Icon size={15} />
            <div>
              <strong>{issue.code}</strong>
              <span>{issue.message}</span>
              <small>{issue.recommendedAction}</small>
            </div>
          </div>
        );
      })}
    </section>
  );
}

function ElementProperties({ workspaceId, element, elements, definitions }: { workspaceId: string; element: ArchitectureElement; elements: ArchitectureElement[]; definitions: MetadataDefinition[] }) {
  const [tab, setTab] = useState('General');
  const queryClient = useQueryClient();
  const elementForm = useForm<ElementFormValues>({
    resolver: zodResolver(elementFormSchema),
    defaultValues: toElementForm(element)
  });
  const links = useQuery({ queryKey: ['links', workspaceId, element.id], queryFn: () => api.links(workspaceId, element.id) });
  const linkForm = useForm<LinkFormValues>({
    resolver: zodResolver(linkFormSchema),
    defaultValues: { provider: 'OTHER', type: 'DOCUMENTATION', label: '', url: '', externalId: '', metadata: {} }
  });
  const [definitionForm, setDefinitionForm] = useState<MetadataDefinitionForm>(() => emptyDefinitionForm(definitions.length + 1, element.type));

  useEffect(() => {
    elementForm.reset(toElementForm(element));
  }, [element, elementForm]);

  const updateElement = useMutation({
    mutationFn: (values: ElementFormValues) =>
      api.updateElement(workspaceId, element.id, {
        ...values,
        metadata: normalizeMetadata(values.metadata),
        parentElementId: values.parentElementId || null,
        version: element.version
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['elements', workspaceId] }),
        queryClient.invalidateQueries({ queryKey: ['validation', workspaceId] })
      ]);
    }
  });

  const createLink = useMutation({
    mutationFn: (values: LinkFormValues) => api.createLink(workspaceId, element.id, values),
    onSuccess: async () => {
      linkForm.reset({ provider: 'OTHER', type: 'DOCUMENTATION', label: '', url: '', externalId: '', metadata: {} });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['links', workspaceId, element.id] }),
        queryClient.invalidateQueries({ queryKey: ['elements', workspaceId] }),
        queryClient.invalidateQueries({ queryKey: ['validation', workspaceId] })
      ]);
    }
  });

  const saveDefinition = useMutation({
    mutationFn: () => {
      const payload = metadataDefinitionPayload(definitionForm, definitions.length + 1);
      return definitionForm.id ? api.updateMetadataDefinition(workspaceId, definitionForm.id, payload) : api.createMetadataDefinition(workspaceId, payload);
    },
    onSuccess: async () => {
      setDefinitionForm(emptyDefinitionForm(definitions.length + 2, element.type));
      await queryClient.invalidateQueries({ queryKey: ['metadata-definitions', workspaceId] });
    }
  });

  const deleteDefinition = useMutation({
    mutationFn: (definitionId: string) => api.deleteMetadataDefinition(workspaceId, definitionId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['metadata-definitions', workspaceId] })
  });

  const visibleDefinitions = definitions.filter((definition) => definition.appliesTo.length === 0 || definition.appliesTo.includes(element.type));

  return (
    <form className="properties-form" onSubmit={elementForm.handleSubmit((values) => updateElement.mutate(values))}>
      <div className="tabs">
        {['General', 'Metadata', 'Ownership', 'Lifecycle', 'Security', 'Operations', 'Delivery', 'Links', 'Custom'].map((item) => (
          <button key={item} type="button" className={tab === item ? 'active' : ''} onClick={() => setTab(item)}>
            {item}
          </button>
        ))}
      </div>
      {elementForm.formState.isDirty && <div className="unsaved-warning">Unsaved property changes</div>}
      {tab === 'General' && (
        <div className="form-grid">
          <label>Name<input {...elementForm.register('name')} /></label>
          <label>Type<select {...elementForm.register('type')}>{['PERSON', 'SOFTWARE_SYSTEM', 'CONTAINER', 'COMPONENT', 'DATA_STORE', 'EXTERNAL_SYSTEM'].map((type) => <option key={type}>{type}</option>)}</select></label>
          <label>Description<textarea {...elementForm.register('description')} /></label>
          <label>Technology<input {...elementForm.register('technology')} /></label>
          <label>Parent<select {...elementForm.register('parentElementId')}><option value="">None</option>{elements.filter((candidate) => candidate.id !== element.id).map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.name}</option>)}</select></label>
          <label>Responsibilities<textarea {...elementForm.register('metadata.responsibilitiesText' as Parameters<typeof elementForm.register>[0])} placeholder="One responsibility per line" /></label>
        </div>
      )}
      {tab === 'Metadata' && (
        <div className="form-grid">
          <label>Domain<input {...elementForm.register('metadata.classification.domain' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Capability<input {...elementForm.register('metadata.classification.capability' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Criticality<input {...elementForm.register('metadata.classification.criticality' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Data classification<input {...elementForm.register('metadata.classification.dataClassification' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Tags<input {...elementForm.register('metadata.classification.tagsText' as Parameters<typeof elementForm.register>[0])} placeholder="security,governance" /></label>
        </div>
      )}
      {tab === 'Ownership' && (
        <div className="form-grid">
          <label>Team<input {...elementForm.register('metadata.ownership.team' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Technical owner<input {...elementForm.register('metadata.ownership.technicalOwner' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Business owner<input {...elementForm.register('metadata.ownership.businessOwner' as Parameters<typeof elementForm.register>[0])} /></label>
        </div>
      )}
      {tab === 'Lifecycle' && (
        <div className="form-grid">
          <label>Status<input {...elementForm.register('metadata.lifecycle.status' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Version<input {...elementForm.register('metadata.lifecycle.version' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Reviewed at<input type="date" {...elementForm.register('metadata.lifecycle.reviewedAt' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Review due at<input type="date" {...elementForm.register('metadata.lifecycle.reviewDueAt' as Parameters<typeof elementForm.register>[0])} /></label>
        </div>
      )}
      {tab === 'Security' && (
        <div className="form-grid">
          <label>Authentication<input {...elementForm.register('metadata.security.authentication' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Authorization<input {...elementForm.register('metadata.security.authorization' as Parameters<typeof elementForm.register>[0])} /></label>
          <label className="checkbox"><input type="checkbox" {...elementForm.register('metadata.security.internetExposed' as Parameters<typeof elementForm.register>[0])} /> Internet exposed</label>
          <label className="checkbox"><input type="checkbox" {...elementForm.register('metadata.security.storesPersonalData' as Parameters<typeof elementForm.register>[0])} /> Stores personal data</label>
        </div>
      )}
      {tab === 'Operations' && (
        <div className="form-grid">
          <label>Availability target<input type="number" step="0.01" {...elementForm.register('metadata.operations.availabilityTarget' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Support team<input {...elementForm.register('metadata.operations.supportTeam' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Runbook URL<input {...elementForm.register('metadata.operations.runbookUrl' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Dashboard URL<input {...elementForm.register('metadata.operations.dashboardUrl' as Parameters<typeof elementForm.register>[0])} /></label>
        </div>
      )}
      {tab === 'Delivery' && (
        <div className="form-grid">
          <label>Jira project<input {...elementForm.register('metadata.delivery.jiraProject' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Repository URL<input {...elementForm.register('metadata.delivery.repositoryUrl' as Parameters<typeof elementForm.register>[0])} /></label>
          <label>Pipeline URL<input {...elementForm.register('metadata.delivery.deploymentPipelineUrl' as Parameters<typeof elementForm.register>[0])} /></label>
        </div>
      )}
      {tab === 'Links' && (
        <div className="form-grid">
          <div className="links-list">
            {(links.data ?? []).map((link) => (
              <a key={link.id} href={link.url} target="_blank" rel="noreferrer">
                <Link2 size={14} /> {link.label} <span>{link.provider}</span>
              </a>
            ))}
          </div>
          <label>Provider<select {...linkForm.register('provider')}>{['CONFLUENCE', 'NOTION', 'JIRA', 'GITHUB', 'GITLAB', 'AZURE_DEVOPS', 'SERVICENOW', 'LINEAR', 'SHAREPOINT', 'GRAFANA', 'DATADOG', 'OPENAPI', 'RUNBOOK', 'WIKI', 'OTHER'].map((provider) => <option key={provider}>{provider}</option>)}</select></label>
          <label>Type<select {...linkForm.register('type')}>{['DOCUMENTATION', 'ISSUE', 'EPIC', 'REPOSITORY', 'DASHBOARD', 'RUNBOOK', 'API_SPECIFICATION', 'DEPLOYMENT_PIPELINE', 'INCIDENT', 'OTHER'].map((type) => <option key={type}>{type}</option>)}</select></label>
          <label>Label<input {...linkForm.register('label')} /></label>
          <label>URL<input {...linkForm.register('url')} /></label>
          <label>External ID<input {...linkForm.register('externalId')} /></label>
          <button type="button" className="secondary-action" onClick={linkForm.handleSubmit((values) => createLink.mutate(values))}><Plus size={14} /> Add link</button>
        </div>
      )}
      {tab === 'Custom' && (
        <div className="form-grid">
          {visibleDefinitions.map((definition) => (
            <label key={definition.id}>
              {definition.label}{definition.required ? ' *' : ''}
              {renderCustomField(definition, elementForm.register(`metadata.custom.${definition.key}` as Parameters<typeof elementForm.register>[0]))}
            </label>
          ))}
          <div className="metadata-definition-editor">
            <div className="subheading">Custom field definition</div>
            <label>Key<input value={definitionForm.key} onChange={(event) => setDefinitionForm({ ...definitionForm, key: slugKey(event.target.value) })} placeholder="service_tier" /></label>
            <label>Label<input value={definitionForm.label} onChange={(event) => setDefinitionForm({ ...definitionForm, label: event.target.value })} placeholder="Service tier" /></label>
            <label>Description<textarea value={definitionForm.description} onChange={(event) => setDefinitionForm({ ...definitionForm, description: event.target.value })} /></label>
            <label>Value type<select value={definitionForm.valueType} onChange={(event) => setDefinitionForm({ ...definitionForm, valueType: event.target.value as MetadataDefinition['valueType'] })}>{['STRING', 'TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'URL', 'SINGLE_SELECT', 'MULTI_SELECT', 'USER_REFERENCE', 'TEAM_REFERENCE'].map((type) => <option key={type}>{type}</option>)}</select></label>
            <label>Applies to<select multiple value={definitionForm.appliesTo} onChange={(event) => setDefinitionForm({ ...definitionForm, appliesTo: Array.from(event.target.selectedOptions).map((option) => option.value as ArchitectureElement['type']) })}>{['PERSON', 'SOFTWARE_SYSTEM', 'CONTAINER', 'COMPONENT', 'DATA_STORE', 'EXTERNAL_SYSTEM'].map((type) => <option key={type}>{type}</option>)}</select></label>
            <label>Allowed values<textarea value={definitionForm.allowedValuesText} onChange={(event) => setDefinitionForm({ ...definitionForm, allowedValuesText: event.target.value })} placeholder="One option per line or JSON array" /></label>
            <label>Default value<input value={definitionForm.defaultValueText} onChange={(event) => setDefinitionForm({ ...definitionForm, defaultValueText: event.target.value })} /></label>
            <label>Validation rules JSON<textarea value={definitionForm.validationRulesText} onChange={(event) => setDefinitionForm({ ...definitionForm, validationRulesText: event.target.value })} placeholder='{"pattern":"^[A-Z]+$"}' /></label>
            <label className="checkbox"><input type="checkbox" checked={definitionForm.required} onChange={(event) => setDefinitionForm({ ...definitionForm, required: event.target.checked })} /> Required</label>
            <button type="button" className="secondary-action inline-action" onClick={() => saveDefinition.mutate()} disabled={!definitionForm.key || !definitionForm.label}><Plus size={14} /> {definitionForm.id ? 'Update field' : 'Add custom field'}</button>
            {saveDefinition.error && <div className="form-error">{String(saveDefinition.error.message)}</div>}
          </div>
          <div className="metadata-definition-list">
            {definitions.map((definition) => (
              <button key={definition.id} type="button" className="definition-row" onClick={() => setDefinitionForm(fromMetadataDefinition(definition))}>
                <span>{definition.label}</span>
                <small>{definition.key} · {definition.valueType}</small>
                <Trash2
                  size={14}
                  onClick={(event) => {
                    event.stopPropagation();
                    if (globalThis.confirm(`Delete custom field "${definition.label}"?`)) deleteDefinition.mutate(definition.id);
                  }}
                />
              </button>
            ))}
          </div>
        </div>
      )}
      {updateElement.error && <div className="form-error">{String(updateElement.error.message)}</div>}
      <button className="primary-action" type="submit"><Save size={15} /> Save properties</button>
    </form>
  );
}

function RelationshipProperties({ workspaceId, relationship, elements }: { workspaceId: string; relationship: ArchitectureRelationship; elements: ArchitectureElement[] }) {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    sourceElementId: relationship.sourceElementId,
    targetElementId: relationship.targetElementId,
    type: relationship.type,
    description: relationship.description,
    technology: relationship.technology ?? '',
    protocol: relationship.protocol ?? '',
    metadata: relationship.metadata
  });
  const [metadataText, setMetadataText] = useState(JSON.stringify(relationship.metadata ?? {}, null, 2));
  useEffect(() => {
    setForm({
      sourceElementId: relationship.sourceElementId,
      targetElementId: relationship.targetElementId,
      type: relationship.type,
      description: relationship.description,
      technology: relationship.technology ?? '',
      protocol: relationship.protocol ?? '',
      metadata: relationship.metadata
    });
    setMetadataText(JSON.stringify(relationship.metadata ?? {}, null, 2));
  }, [relationship]);
  const update = useMutation({
    mutationFn: () => api.updateRelationship(workspaceId, relationship.id, { ...form, metadata: parseJsonObject(metadataText, 'Relationship metadata'), version: relationship.version }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['relationships', workspaceId] }),
        queryClient.invalidateQueries({ queryKey: ['validation', workspaceId] })
      ]);
    }
  });
  const elementOptions = useMemo(() => elements.map((element) => <option key={element.id} value={element.id}>{element.name}</option>), [elements]);
  return (
    <div className="properties-form">
      <div className="tabs"><button className="active">Relationship</button></div>
      <div className="form-grid">
        <label>Source<select value={form.sourceElementId} onChange={(event) => setForm({ ...form, sourceElementId: event.target.value })}>{elementOptions}</select></label>
        <label>Target<select value={form.targetElementId} onChange={(event) => setForm({ ...form, targetElementId: event.target.value })}>{elementOptions}</select></label>
        <label>Type<select value={form.type} onChange={(event) => setForm({ ...form, type: event.target.value as ArchitectureRelationship['type'] })}>{['USES', 'CALLS', 'READS_FROM', 'WRITES_TO', 'PUBLISHES_TO', 'SUBSCRIBES_TO', 'DEPENDS_ON', 'OWNS', 'AUTHENTICATES_WITH'].map((type) => <option key={type}>{type}</option>)}</select></label>
        <label>Description<textarea value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label>
        <label>Technology<input value={form.technology} onChange={(event) => setForm({ ...form, technology: event.target.value })} /></label>
        <label>Protocol<input value={form.protocol} onChange={(event) => setForm({ ...form, protocol: event.target.value })} /></label>
        <label>Direction<input value={`${elementName(elements, form.sourceElementId)} -> ${elementName(elements, form.targetElementId)}`} readOnly /></label>
        <label>Metadata JSON<textarea value={metadataText} onChange={(event) => setMetadataText(event.target.value)} /></label>
      </div>
      {update.error && <div className="form-error">{String(update.error.message)}</div>}
      <button type="button" className="primary-action" onClick={() => update.mutate()}><Save size={15} /> Save relationship</button>
    </div>
  );
}

interface MetadataDefinitionForm {
  id?: string;
  key: string;
  label: string;
  description: string;
  valueType: MetadataDefinition['valueType'];
  required: boolean;
  appliesTo: ArchitectureElement['type'][];
  allowedValuesText: string;
  defaultValueText: string;
  validationRulesText: string;
  displayOrder: number;
}

function toElementForm(element: ArchitectureElement): ElementFormValues {
  const metadata = structuredClone(element.metadata);
  const classification = asRecord(metadata.classification);
  if (Array.isArray(classification.tags)) {
    classification.tagsText = classification.tags.join(',');
  }
  if (Array.isArray(metadata.responsibilities)) {
    metadata.responsibilitiesText = metadata.responsibilities.join('\n');
  }
  return {
    name: element.name,
    type: element.type,
    description: element.description,
    technology: element.technology ?? '',
    parentElementId: element.parentElementId ?? '',
    metadata
  };
}

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as Record<string, unknown>) : {};
}

function normalizeMetadata(metadata: Record<string, unknown>) {
  const next = structuredClone(metadata);
  const classification = asRecord(next.classification);
  if (typeof classification.tagsText === 'string') {
    classification.tags = classification.tagsText.split(',').map((tag) => tag.trim()).filter(Boolean);
    delete classification.tagsText;
  }
  if (typeof next.responsibilitiesText === 'string') {
    next.responsibilities = next.responsibilitiesText.split('\n').map((item) => item.trim()).filter(Boolean);
    delete next.responsibilitiesText;
  }
  return next;
}

function emptyDefinitionForm(displayOrder: number, elementType: ArchitectureElement['type']): MetadataDefinitionForm {
  return {
    key: '',
    label: '',
    description: '',
    valueType: 'STRING',
    required: false,
    appliesTo: [elementType],
    allowedValuesText: '',
    defaultValueText: '',
    validationRulesText: '{}',
    displayOrder
  };
}

function fromMetadataDefinition(definition: MetadataDefinition): MetadataDefinitionForm {
  return {
    id: definition.id,
    key: definition.key,
    label: definition.label,
    description: definition.description ?? '',
    valueType: definition.valueType,
    required: definition.required,
    appliesTo: definition.appliesTo,
    allowedValuesText: formatDefinitionValue(definition.allowedValues),
    defaultValueText: formatDefinitionValue(definition.defaultValue),
    validationRulesText: JSON.stringify(definition.validationRules ?? {}, null, 2),
    displayOrder: definition.displayOrder
  };
}

function metadataDefinitionPayload(form: MetadataDefinitionForm, fallbackOrder: number): Omit<MetadataDefinition, 'id' | 'workspaceId'> {
  return {
    key: form.key,
    label: form.label,
    description: form.description,
    valueType: form.valueType,
    required: form.required,
    appliesTo: form.appliesTo,
    allowedValues: parseAllowedValues(form.allowedValuesText),
    defaultValue: parseScalar(form.defaultValueText),
    validationRules: parseJsonObject(form.validationRulesText || '{}', 'Validation rules'),
    displayOrder: form.displayOrder || fallbackOrder
  };
}

function renderCustomField(definition: MetadataDefinition, register: UseFormRegisterReturn) {
  const allowedValues = normalizeAllowedValues(definition.allowedValues);
  if (definition.valueType === 'BOOLEAN') return <input type="checkbox" {...register} />;
  if (definition.valueType === 'NUMBER') return <input type="number" {...register} />;
  if (definition.valueType === 'DATE') return <input type="date" {...register} />;
  if (definition.valueType === 'URL') return <input type="url" {...register} />;
  if (definition.valueType === 'TEXT') return <textarea {...register} />;
  if ((definition.valueType === 'SINGLE_SELECT' || definition.valueType === 'MULTI_SELECT') && allowedValues.length > 0) {
    return <select multiple={definition.valueType === 'MULTI_SELECT'} {...register}>{allowedValues.map((value) => <option key={value} value={value}>{value}</option>)}</select>;
  }
  return <input {...register} />;
}

function parseAllowedValues(value: string) {
  if (!value.trim()) return null;
  if (value.trim().startsWith('[')) return JSON.parse(value);
  return value.split('\n').map((item) => item.trim()).filter(Boolean);
}

function parseScalar(value: string) {
  if (!value.trim()) return null;
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

function parseJsonObject(value: string, label: string): Record<string, unknown> {
  const parsed = JSON.parse(value || '{}') as unknown;
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error(`${label} must be a JSON object`);
  }
  return parsed as Record<string, unknown>;
}

function normalizeAllowedValues(value: unknown) {
  if (Array.isArray(value)) return value.map(String);
  return [];
}

function formatDefinitionValue(value: unknown) {
  if (value === null || value === undefined) return '';
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
}

function slugKey(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_+|_+$/g, '');
}

function elementName(elements: ArchitectureElement[], elementId: string) {
  return elements.find((element) => element.id === elementId)?.name ?? elementId;
}
