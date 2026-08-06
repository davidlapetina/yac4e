import { z } from 'zod';

export const metadataSchema = z
  .object({
    ownership: z.object({ team: z.string().optional(), technicalOwner: z.string().optional(), businessOwner: z.string().optional() }).optional(),
    classification: z.object({ domain: z.string().optional(), capability: z.string().optional(), criticality: z.string().optional(), dataClassification: z.string().optional(), tags: z.array(z.string()).optional() }).optional(),
    lifecycle: z.object({ status: z.string().optional(), version: z.string().optional(), reviewedAt: z.string().optional(), reviewDueAt: z.string().optional() }).optional(),
    operations: z.object({ availabilityTarget: z.coerce.number().optional(), supportTeam: z.string().optional(), runbookUrl: z.string().url().optional().or(z.literal('')), dashboardUrl: z.string().url().optional().or(z.literal('')) }).optional(),
    security: z.object({ authentication: z.string().optional(), authorization: z.string().optional(), internetExposed: z.boolean().optional(), storesPersonalData: z.boolean().optional() }).optional(),
    delivery: z.object({ jiraProject: z.string().optional(), repositoryUrl: z.string().url().optional().or(z.literal('')), deploymentPipelineUrl: z.string().url().optional().or(z.literal('')) }).optional(),
    custom: z.record(z.unknown()).optional(),
    responsibilities: z.array(z.string()).optional()
  })
  .passthrough();

export const elementFormSchema = z.object({
  name: z.string().min(1),
  type: z.enum(['PERSON', 'SOFTWARE_SYSTEM', 'CONTAINER', 'COMPONENT', 'DATA_STORE', 'EXTERNAL_SYSTEM']),
  description: z.string(),
  technology: z.string().optional(),
  parentElementId: z.string().optional().nullable(),
  metadata: metadataSchema
});

export const linkFormSchema = z.object({
  provider: z.enum(['CONFLUENCE', 'NOTION', 'JIRA', 'GITHUB', 'GITLAB', 'AZURE_DEVOPS', 'SERVICENOW', 'LINEAR', 'SHAREPOINT', 'GRAFANA', 'DATADOG', 'OPENAPI', 'RUNBOOK', 'WIKI', 'OTHER']),
  type: z.enum(['DOCUMENTATION', 'ISSUE', 'EPIC', 'REPOSITORY', 'DASHBOARD', 'RUNBOOK', 'API_SPECIFICATION', 'DEPLOYMENT_PIPELINE', 'INCIDENT', 'OTHER']),
  label: z.string().min(1),
  url: z.string().url(),
  externalId: z.string().optional(),
  metadata: z.record(z.unknown()).default({})
});

export type ElementFormValues = z.infer<typeof elementFormSchema>;
export type LinkFormValues = z.infer<typeof linkFormSchema>;
