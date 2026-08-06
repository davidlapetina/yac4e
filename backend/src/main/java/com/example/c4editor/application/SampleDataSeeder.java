package com.example.c4editor.application;

import com.example.c4editor.domain.ArchitectureElementType;
import com.example.c4editor.domain.DiagramViewType;
import com.example.c4editor.domain.LayoutDirection;
import com.example.c4editor.domain.LinkProvider;
import com.example.c4editor.domain.LinkType;
import com.example.c4editor.domain.MetadataValueType;
import com.example.c4editor.domain.RelationshipType;
import com.example.c4editor.persistence.ArchitectureElementEntity;
import com.example.c4editor.persistence.ArchitectureRelationshipEntity;
import com.example.c4editor.persistence.DiagramViewElementEntity;
import com.example.c4editor.persistence.DiagramViewEntity;
import com.example.c4editor.persistence.DiagramViewRelationshipEntity;
import com.example.c4editor.persistence.ExternalLinkEntity;
import com.example.c4editor.persistence.MetadataDefinitionEntity;
import com.example.c4editor.persistence.WorkspaceEntity;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class SampleDataSeeder {
    @Transactional
    void seed(@Observes StartupEvent event) {
        if (WorkspaceEntity.count() > 0) {
            return;
        }

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.name = "Anonymous Reference Architecture";
        workspace.description = "Anonymous sample workspace demonstrating a metadata-rich C4 architecture repository.";
        workspace.persist();

        ArchitectureElementEntity operationsAnalyst = element(workspace, ArchitectureElementType.PERSON, "Operations Analyst",
                "Reviews operational work queues, exceptions and audit trails.", null, null, "Operations Enablement",
                "PRODUCTION", "Operations", "Case Management", "tier-2", "operations", "workflow");
        ArchitectureElementEntity serviceOwner = element(workspace, ArchitectureElementType.PERSON, "Service Owner",
                "Owns service quality, support readiness and lifecycle reviews.", null, null, "Service Ownership",
                "PRODUCTION", "Operations", "Service Governance", "tier-1", "ownership", "governance");
        ArchitectureElementEntity partnerUser = element(workspace, ArchitectureElementType.PERSON, "Partner User",
                "Submits external requests and checks processing status.", null, null, "Partner Operations",
                "PRODUCTION", "Partner Experience", "Request Intake", "tier-3", "partner", "external");

        ArchitectureElementEntity platform = element(workspace, ArchitectureElementType.SOFTWARE_SYSTEM, "Reference Operations Platform",
                "Anonymous platform for intake, policy checks, workflow execution, reporting and audit.", null,
                "React, Quarkus, PostgreSQL, NATS", "Platform Team", "PRODUCTION", "Operations",
                "Operational Automation", "tier-1", "reference", "operations", "governance");
        ArchitectureElementEntity identityProvider = element(workspace, ArchitectureElementType.EXTERNAL_SYSTEM, "Corporate Identity Provider",
                "Enterprise identity provider for workforce authentication and token issuance.", null, "OIDC",
                "Security Team", "PRODUCTION", "Security", "Identity", "tier-1", "security", "identity");
        ArchitectureElementEntity notificationGateway = element(workspace, ArchitectureElementType.EXTERNAL_SYSTEM, "Notification Gateway",
                "Managed service used to send email, chat and webhook notifications.", null, "HTTPS/Webhooks",
                "Messaging Team", "PRODUCTION", "Communications", "Notifications", "tier-2", "notifications");
        ArchitectureElementEntity partnerPortal = element(workspace, ArchitectureElementType.EXTERNAL_SYSTEM, "Partner Portal",
                "External portal that submits partner requests to the platform.", null, "HTTPS",
                "Partner Team", "PRODUCTION", "Partner Experience", "Request Intake", "tier-2", "partner", "api");

        ArchitectureElementEntity webConsole = element(workspace, ArchitectureElementType.CONTAINER, "Web Console",
                "Desktop web application for operators and service owners.", platform.id, "React 19 / TypeScript",
                "Experience Team", "PRODUCTION", "Operations", "Case Management", "tier-1", "frontend", "operations");
        ArchitectureElementEntity apiGateway = element(workspace, ArchitectureElementType.CONTAINER, "Public API Gateway",
                "Ingress API for web and partner clients with request validation and routing.", platform.id,
                "Quarkus Java 21 / REST", "Platform Team", "PRODUCTION", "Platform", "API Management",
                "tier-1", "api", "edge");
        ArchitectureElementEntity workflowService = element(workspace, ArchitectureElementType.CONTAINER, "Workflow Service",
                "Coordinates operational cases, approvals, state transitions and audit publishing.", platform.id,
                "Quarkus Java 21", "Workflow Team", "PRODUCTION", "Operations", "Workflow Automation",
                "tier-1", "workflow", "audit");
        ArchitectureElementEntity policyService = element(workspace, ArchitectureElementType.CONTAINER, "Policy Service",
                "Evaluates policy rules, entitlements and authorization decisions.", platform.id, "Quarkus Java 21",
                "Policy Team", "PRODUCTION", "Governance", "Policy Evaluation", "tier-1", "security", "policy");
        ArchitectureElementEntity reportingService = element(workspace, ArchitectureElementType.CONTAINER, "Reporting Service",
                "Builds operational metrics and dashboard-ready summaries.", platform.id, "Quarkus Java 21",
                "Insights Team", "PRODUCTION", "Reporting", "Operational Insights", "tier-2", "reporting", "metrics");
        ArchitectureElementEntity eventBroker = element(workspace, ArchitectureElementType.CONTAINER, "Event Broker",
                "Internal event bus for audit, workflow and notification events.", platform.id, "NATS JetStream",
                "Platform Team", "PRODUCTION", "Platform", "Event Streaming", "tier-1", "events", "async");
        ArchitectureElementEntity operationalStore = element(workspace, ArchitectureElementType.DATA_STORE, "Operational Data Store",
                "Stores cases, policies, decisions, audit records and view state.", platform.id, "PostgreSQL",
                "Data Platform Team", "PRODUCTION", "Operations", "Transactional Data", "tier-1", "database", "confidential");
        ArchitectureElementEntity analyticsStore = element(workspace, ArchitectureElementType.DATA_STORE, "Analytics Warehouse",
                "Stores curated historical metrics for trend analysis.", platform.id, "PostgreSQL / Columnar Tables",
                "Insights Team", "PRODUCTION", "Reporting", "Analytics", "tier-2", "analytics", "reporting");
        ArchitectureElementEntity objectArchive = element(workspace, ArchitectureElementType.DATA_STORE, "Object Archive",
                "Stores uploaded attachments, exported reports and evidence bundles.", platform.id, "S3-compatible object storage",
                "Data Platform Team", "PRODUCTION", "Operations", "Document Retention", "tier-2", "archive", "evidence");

        ArchitectureElementEntity intakeController = element(workspace, ArchitectureElementType.COMPONENT, "Intake Controller",
                "Normalizes incoming requests and validates required metadata.", workflowService.id, "JAX-RS",
                "Workflow Team", "PRODUCTION", "Operations", "Request Intake", "tier-1", "workflow", "validation");
        ArchitectureElementEntity taskOrchestrator = element(workspace, ArchitectureElementType.COMPONENT, "Task Orchestrator",
                "Moves cases through review, approval and completion states.", workflowService.id, "Java",
                "Workflow Team", "PRODUCTION", "Operations", "Workflow Automation", "tier-1", "workflow");
        ArchitectureElementEntity slaEvaluator = element(workspace, ArchitectureElementType.COMPONENT, "SLA Evaluator",
                "Calculates deadlines and escalates overdue work.", workflowService.id, "Java",
                "Workflow Team", "PRODUCTION", "Operations", "Service Levels", "tier-2", "sla", "workflow");
        ArchitectureElementEntity auditPublisher = element(workspace, ArchitectureElementType.COMPONENT, "Audit Publisher",
                "Publishes immutable audit events for downstream reporting and notification.", workflowService.id, "Java",
                "Workflow Team", "PRODUCTION", "Operations", "Audit", "tier-1", "audit", "events");
        ArchitectureElementEntity ruleEvaluator = element(workspace, ArchitectureElementType.COMPONENT, "Rule Evaluator",
                "Evaluates policy rules for requests and operational actions.", policyService.id, "Java",
                "Policy Team", "PRODUCTION", "Governance", "Policy Evaluation", "tier-1", "security", "policy");
        ArchitectureElementEntity entitlementAdapter = element(workspace, ArchitectureElementType.COMPONENT, "Entitlement Adapter",
                "Retrieves group and role claims from identity tokens.", policyService.id, "OIDC Client",
                "Policy Team", "PRODUCTION", "Security", "Entitlements", "tier-1", "identity", "security");
        ArchitectureElementEntity policyCache = element(workspace, ArchitectureElementType.COMPONENT, "Policy Cache",
                "Caches compiled policy rules for low-latency authorization decisions.", policyService.id, "Caffeine",
                "Policy Team", "PRODUCTION", "Governance", "Policy Evaluation", "tier-1", "cache", "policy");
        ArchitectureElementEntity metricsAggregator = element(workspace, ArchitectureElementType.COMPONENT, "Metrics Aggregator",
                "Aggregates workflow, SLA and exception metrics.", reportingService.id, "Java",
                "Insights Team", "PRODUCTION", "Reporting", "Operational Insights", "tier-2", "metrics");
        ArchitectureElementEntity dashboardApi = element(workspace, ArchitectureElementType.COMPONENT, "Dashboard API",
                "Serves dashboard summaries and drill-down data.", reportingService.id, "JAX-RS",
                "Insights Team", "PRODUCTION", "Reporting", "Dashboarding", "tier-2", "api", "reporting");

        link(workflowService, LinkProvider.RUNBOOK, LinkType.RUNBOOK, "Workflow service runbook", "https://example.invalid/runbooks/workflow-service", "RUN-WORKFLOW");
        link(policyService, LinkProvider.JIRA, LinkType.EPIC, "Policy hardening epic", "https://tracker.example.invalid/browse/REF-100", "REF-100");
        link(policyService, LinkProvider.CONFLUENCE, LinkType.DOCUMENTATION, "Policy service handbook", "https://docs.example.invalid/policy-service", null);
        link(webConsole, LinkProvider.GITHUB, LinkType.REPOSITORY, "Web console repository", "https://github.com/example/reference-web-console", null);
        link(apiGateway, LinkProvider.OPENAPI, LinkType.API_SPECIFICATION, "Public API specification", "https://api.example.invalid/openapi.yaml", "public-api");
        link(reportingService, LinkProvider.GRAFANA, LinkType.DASHBOARD, "Operational dashboard", "https://grafana.example.invalid/d/reference-operations", null);
        link(objectArchive, LinkProvider.WIKI, LinkType.DOCUMENTATION, "Retention policy", "https://wiki.example.invalid/retention-policy", null);

        ArchitectureRelationshipEntity analystUsesUi = relationship(workspace, operationsAnalyst, webConsole, RelationshipType.USES,
                "Reviews queues, exceptions and case history", "HTTPS");
        ArchitectureRelationshipEntity ownerUsesUi = relationship(workspace, serviceOwner, webConsole, RelationshipType.USES,
                "Reviews service health and governance reports", "HTTPS");
        ArchitectureRelationshipEntity partnerSubmits = relationship(workspace, partnerUser, partnerPortal, RelationshipType.USES,
                "Submits partner requests", "HTTPS");
        ArchitectureRelationshipEntity portalCallsGateway = relationship(workspace, partnerPortal, apiGateway, RelationshipType.CALLS,
                "Submits validated requests", "HTTPS/JSON");
        ArchitectureRelationshipEntity uiCallsGateway = relationship(workspace, webConsole, apiGateway, RelationshipType.CALLS,
                "Calls platform APIs", "HTTPS/JSON");
        ArchitectureRelationshipEntity uiAuthenticates = relationship(workspace, webConsole, identityProvider, RelationshipType.AUTHENTICATES_WITH,
                "Authenticates workforce users", "OIDC");
        ArchitectureRelationshipEntity gatewayCallsWorkflow = relationship(workspace, apiGateway, workflowService, RelationshipType.CALLS,
                "Routes case commands and queries", "HTTP/JSON");
        ArchitectureRelationshipEntity gatewayCallsPolicy = relationship(workspace, apiGateway, policyService, RelationshipType.CALLS,
                "Requests authorization decisions", "HTTP/JSON");
        ArchitectureRelationshipEntity gatewayCallsReporting = relationship(workspace, apiGateway, reportingService, RelationshipType.CALLS,
                "Retrieves dashboard data", "HTTP/JSON");
        ArchitectureRelationshipEntity workflowReadsStore = relationship(workspace, workflowService, operationalStore, RelationshipType.READS_FROM,
                "Loads case and policy state", "JDBC");
        ArchitectureRelationshipEntity workflowWritesStore = relationship(workspace, workflowService, operationalStore, RelationshipType.WRITES_TO,
                "Persists cases, transitions and audit records", "JDBC");
        ArchitectureRelationshipEntity workflowWritesArchive = relationship(workspace, workflowService, objectArchive, RelationshipType.WRITES_TO,
                "Stores evidence bundles and exported attachments", "S3 API");
        ArchitectureRelationshipEntity workflowPublishes = relationship(workspace, workflowService, eventBroker, RelationshipType.PUBLISHES_TO,
                "Publishes case and audit events", "NATS");
        ArchitectureRelationshipEntity brokerNotifies = relationship(workspace, eventBroker, notificationGateway, RelationshipType.PUBLISHES_TO,
                "Publishes notification events", "Webhook");
        ArchitectureRelationshipEntity policyAuthenticates = relationship(workspace, policyService, identityProvider, RelationshipType.AUTHENTICATES_WITH,
                "Validates token issuer and claims", "OIDC");
        ArchitectureRelationshipEntity policyReadsStore = relationship(workspace, policyService, operationalStore, RelationshipType.READS_FROM,
                "Loads policy definitions", "JDBC");
        ArchitectureRelationshipEntity reportingReadsStore = relationship(workspace, reportingService, operationalStore, RelationshipType.READS_FROM,
                "Reads operational records", "JDBC");
        ArchitectureRelationshipEntity reportingWritesWarehouse = relationship(workspace, reportingService, analyticsStore, RelationshipType.WRITES_TO,
                "Publishes curated metrics", "JDBC");
        ArchitectureRelationshipEntity reportingReadsWarehouse = relationship(workspace, reportingService, analyticsStore, RelationshipType.READS_FROM,
                "Reads historical trends", "JDBC");
        ArchitectureRelationshipEntity intakeOwns = relationship(workspace, workflowService, intakeController, RelationshipType.OWNS,
                "Contains request intake logic", null);
        ArchitectureRelationshipEntity orchestratorOwns = relationship(workspace, workflowService, taskOrchestrator, RelationshipType.OWNS,
                "Contains orchestration logic", null);
        ArchitectureRelationshipEntity slaOwns = relationship(workspace, workflowService, slaEvaluator, RelationshipType.OWNS,
                "Contains SLA calculation logic", null);
        ArchitectureRelationshipEntity auditOwns = relationship(workspace, workflowService, auditPublisher, RelationshipType.OWNS,
                "Contains audit event logic", null);
        ArchitectureRelationshipEntity ruleOwns = relationship(workspace, policyService, ruleEvaluator, RelationshipType.OWNS,
                "Contains policy rule logic", null);
        ArchitectureRelationshipEntity entitlementOwns = relationship(workspace, policyService, entitlementAdapter, RelationshipType.OWNS,
                "Contains entitlement integration logic", null);
        ArchitectureRelationshipEntity cacheOwns = relationship(workspace, policyService, policyCache, RelationshipType.OWNS,
                "Contains policy cache logic", null);
        ArchitectureRelationshipEntity metricsOwns = relationship(workspace, reportingService, metricsAggregator, RelationshipType.OWNS,
                "Contains aggregation logic", null);
        ArchitectureRelationshipEntity dashboardOwns = relationship(workspace, reportingService, dashboardApi, RelationshipType.OWNS,
                "Contains dashboard API logic", null);
        ArchitectureRelationshipEntity intakeCallsPolicy = relationship(workspace, intakeController, ruleEvaluator, RelationshipType.CALLS,
                "Requests initial policy checks", "In-process");
        ArchitectureRelationshipEntity orchestratorCallsSla = relationship(workspace, taskOrchestrator, slaEvaluator, RelationshipType.CALLS,
                "Checks active deadlines", "In-process");
        ArchitectureRelationshipEntity orchestratorPublishesAudit = relationship(workspace, taskOrchestrator, auditPublisher, RelationshipType.CALLS,
                "Records state transitions", "In-process");
        ArchitectureRelationshipEntity auditPublishesBroker = relationship(workspace, auditPublisher, eventBroker, RelationshipType.PUBLISHES_TO,
                "Publishes audit events", "NATS");
        ArchitectureRelationshipEntity ruleUsesCache = relationship(workspace, ruleEvaluator, policyCache, RelationshipType.DEPENDS_ON,
                "Uses compiled policy rules", "In-memory");
        ArchitectureRelationshipEntity entitlementUsesIdp = relationship(workspace, entitlementAdapter, identityProvider, RelationshipType.CALLS,
                "Reads user and group claims", "OIDC");
        ArchitectureRelationshipEntity metricsReadsStore = relationship(workspace, metricsAggregator, operationalStore, RelationshipType.READS_FROM,
                "Reads workflow metrics", "JDBC");
        ArchitectureRelationshipEntity dashboardReadsMetrics = relationship(workspace, dashboardApi, metricsAggregator, RelationshipType.CALLS,
                "Reads aggregated dashboard summaries", "In-process");

        metadataDefinitions(workspace);

        DiagramViewEntity context = view(workspace, "System Context", DiagramViewType.SYSTEM_CONTEXT, null);
        member(context, operationsAnalyst, 40, 85, 230, 140);
        member(context, serviceOwner, 40, 275, 230, 140);
        member(context, partnerUser, 40, 465, 230, 140);
        member(context, platform, 410, 190, 340, 210);
        member(context, identityProvider, 890, 65, 300, 145);
        member(context, notificationGateway, 890, 255, 300, 145);
        member(context, partnerPortal, 890, 445, 300, 145);
        viewRelationship(context, analystUsesUi);
        viewRelationship(context, ownerUsesUi);
        viewRelationship(context, partnerSubmits);
        viewRelationship(context, portalCallsGateway);
        viewRelationship(context, uiAuthenticates);
        viewRelationship(context, brokerNotifies);

        DiagramViewEntity container = view(workspace, "Container View", DiagramViewType.CONTAINER, platform.id);
        member(container, webConsole, 60, 70, 260, 145);
        member(container, apiGateway, 395, 70, 275, 150);
        member(container, workflowService, 770, 45, 280, 155);
        member(container, policyService, 770, 255, 280, 155);
        member(container, reportingService, 770, 465, 280, 155);
        member(container, eventBroker, 1135, 255, 260, 145);
        member(container, operationalStore, 1135, 45, 285, 150);
        member(container, analyticsStore, 1135, 465, 285, 150);
        member(container, objectArchive, 1490, 45, 285, 150);
        member(container, identityProvider, 395, 305, 275, 145);
        member(container, notificationGateway, 1490, 255, 285, 145);
        member(container, partnerPortal, 60, 305, 260, 145);
        viewRelationship(container, portalCallsGateway);
        viewRelationship(container, uiCallsGateway);
        viewRelationship(container, uiAuthenticates);
        viewRelationship(container, gatewayCallsWorkflow);
        viewRelationship(container, gatewayCallsPolicy);
        viewRelationship(container, gatewayCallsReporting);
        viewRelationship(container, workflowReadsStore);
        viewRelationship(container, workflowWritesStore);
        viewRelationship(container, workflowWritesArchive);
        viewRelationship(container, workflowPublishes);
        viewRelationship(container, brokerNotifies);
        viewRelationship(container, policyAuthenticates);
        viewRelationship(container, policyReadsStore);
        viewRelationship(container, reportingReadsStore);
        viewRelationship(container, reportingWritesWarehouse);
        viewRelationship(container, reportingReadsWarehouse);

        DiagramViewEntity workflow = view(workspace, "Workflow Service Components", DiagramViewType.COMPONENT, workflowService.id);
        member(workflow, intakeController, 60, 90, 270, 145);
        member(workflow, taskOrchestrator, 420, 90, 285, 150);
        member(workflow, slaEvaluator, 785, 40, 255, 140);
        member(workflow, auditPublisher, 785, 230, 255, 140);
        member(workflow, ruleEvaluator, 1120, 40, 255, 140);
        member(workflow, eventBroker, 1120, 230, 255, 140);
        member(workflow, operationalStore, 1455, 40, 285, 145);
        member(workflow, objectArchive, 1455, 230, 285, 145);
        viewRelationship(workflow, intakeOwns);
        viewRelationship(workflow, orchestratorOwns);
        viewRelationship(workflow, slaOwns);
        viewRelationship(workflow, auditOwns);
        viewRelationship(workflow, intakeCallsPolicy);
        viewRelationship(workflow, orchestratorCallsSla);
        viewRelationship(workflow, orchestratorPublishesAudit);
        viewRelationship(workflow, auditPublishesBroker);
        viewRelationship(workflow, workflowReadsStore);
        viewRelationship(workflow, workflowWritesStore);
        viewRelationship(workflow, workflowWritesArchive);

        DiagramViewEntity policy = view(workspace, "Policy Service Components", DiagramViewType.COMPONENT, policyService.id);
        member(policy, ruleEvaluator, 70, 90, 270, 145);
        member(policy, entitlementAdapter, 430, 90, 280, 145);
        member(policy, policyCache, 430, 285, 280, 140);
        member(policy, identityProvider, 800, 90, 300, 145);
        member(policy, operationalStore, 800, 285, 300, 145);
        viewRelationship(policy, ruleOwns);
        viewRelationship(policy, entitlementOwns);
        viewRelationship(policy, cacheOwns);
        viewRelationship(policy, ruleUsesCache);
        viewRelationship(policy, entitlementUsesIdp);
        viewRelationship(policy, policyAuthenticates);
        viewRelationship(policy, policyReadsStore);

        DiagramViewEntity reporting = view(workspace, "Reporting Data Flow", DiagramViewType.CUSTOM, reportingService.id);
        member(reporting, reportingService, 80, 130, 285, 150);
        member(reporting, metricsAggregator, 455, 65, 275, 145);
        member(reporting, dashboardApi, 455, 265, 275, 145);
        member(reporting, operationalStore, 820, 65, 295, 145);
        member(reporting, analyticsStore, 820, 265, 295, 145);
        member(reporting, webConsole, 80, 360, 260, 145);
        viewRelationship(reporting, metricsOwns);
        viewRelationship(reporting, dashboardOwns);
        viewRelationship(reporting, metricsReadsStore);
        viewRelationship(reporting, dashboardReadsMetrics);
        viewRelationship(reporting, reportingReadsStore);
        viewRelationship(reporting, reportingWritesWarehouse);
        viewRelationship(reporting, reportingReadsWarehouse);
        viewRelationship(reporting, gatewayCallsReporting);
    }

    private ArchitectureElementEntity element(WorkspaceEntity workspace, ArchitectureElementType type, String name, String description,
            java.util.UUID parentId, String technology, String team, String status, String domain, String capability,
            String serviceTier, String... tags) {
        ArchitectureElementEntity entity = new ArchitectureElementEntity();
        entity.workspaceId = workspace.id;
        entity.type = type;
        entity.name = name;
        entity.description = description;
        entity.parentElementId = parentId;
        entity.technology = technology;
        entity.metadata = metadata(team, status, domain, capability, serviceTier, tags);
        entity.persist();
        return entity;
    }

    private Map<String, Object> metadata(String team, String status, String domain, String capability, String serviceTier, String... tags) {
        List<String> tagList = tags.length == 0 ? List.of("reference") : List.of(tags);
        String reportingScope = "Reporting".equals(domain) ? "operational reporting" : domain.toLowerCase() + " reporting";
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("ownership", Map.of("team", team, "technicalOwner", team + " Lead", "businessOwner", "Anonymous Business Sponsor"));
        root.put("classification", Map.of("domain", domain, "capability", capability, "criticality", "HIGH",
                "dataClassification", "CONFIDENTIAL", "tags", tagList));
        root.put("lifecycle", Map.of("status", status, "version", "1.0.0", "reviewedAt", "2026-08-01", "reviewDueAt", "2026-11-01"));
        root.put("operations", Map.of("availabilityTarget", 99.9, "supportTeam", "Reference Operations",
                "runbookUrl", "https://example.invalid/runbooks/" + serviceTier, "dashboardUrl", "https://grafana.example.invalid/d/reference"));
        root.put("security", Map.of("authentication", "OAuth2", "authorization", "Policy Service", "internetExposed", false, "storesPersonalData", false));
        root.put("delivery", Map.of("jiraProject", "REF", "repositoryUrl", "https://github.com/example/reference-architecture",
                "deploymentPipelineUrl", "https://ci.example.invalid/pipelines/reference"));
        root.put("custom", Map.of("serviceTier", serviceTier, "dataRetentionClass", "standard", "complianceScope", List.of("internal-controls")));
        root.put("responsibilities", List.of("Maintain " + capability.toLowerCase(), "Support " + reportingScope));
        return root;
    }

    private void metadataDefinitions(WorkspaceEntity workspace) {
        metadataDefinition(workspace, "serviceTier", "Service tier", "Operational support tier.",
                MetadataValueType.SINGLE_SELECT, true, List.of("CONTAINER", "COMPONENT", "DATA_STORE"),
                List.of("tier-1", "tier-2", "tier-3"), 1);
        metadataDefinition(workspace, "dataRetentionClass", "Data retention class", "Retention profile for owned data.",
                MetadataValueType.SINGLE_SELECT, false, List.of("DATA_STORE", "CONTAINER"),
                List.of("short-lived", "standard", "regulated", "archive"), 2);
        metadataDefinition(workspace, "complianceScope", "Compliance scope", "Controls or review programs that apply.",
                MetadataValueType.MULTI_SELECT, false, List.of("SOFTWARE_SYSTEM", "CONTAINER", "COMPONENT", "DATA_STORE"),
                List.of("internal-controls", "privacy-review", "availability-review", "vendor-review"), 3);
    }

    private void metadataDefinition(WorkspaceEntity workspace, String key, String label, String description,
            MetadataValueType valueType, boolean required, List<String> appliesTo, List<String> allowedValues, int displayOrder) {
        MetadataDefinitionEntity entity = new MetadataDefinitionEntity();
        entity.workspaceId = workspace.id;
        entity.key = key;
        entity.label = label;
        entity.description = description;
        entity.valueType = valueType;
        entity.required = required;
        entity.appliesTo = appliesTo;
        entity.allowedValues = allowedValues;
        entity.displayOrder = displayOrder;
        entity.persist();
    }

    private ExternalLinkEntity link(ArchitectureElementEntity element, LinkProvider provider, LinkType type, String label, String url, String externalId) {
        ExternalLinkEntity entity = new ExternalLinkEntity();
        entity.elementId = element.id;
        entity.provider = provider;
        entity.type = type;
        entity.label = label;
        entity.url = url;
        entity.externalId = externalId;
        entity.metadata = new LinkedHashMap<>();
        entity.persist();
        return entity;
    }

    private ArchitectureRelationshipEntity relationship(WorkspaceEntity workspace, ArchitectureElementEntity source, ArchitectureElementEntity target,
            RelationshipType type, String description, String technology) {
        ArchitectureRelationshipEntity entity = new ArchitectureRelationshipEntity();
        entity.workspaceId = workspace.id;
        entity.sourceElementId = source.id;
        entity.targetElementId = target.id;
        entity.type = type;
        entity.description = description;
        entity.technology = technology;
        entity.protocol = technology;
        entity.metadata = new LinkedHashMap<>();
        entity.persist();
        return entity;
    }

    private DiagramViewEntity view(WorkspaceEntity workspace, String name, DiagramViewType type, java.util.UUID scopeElementId) {
        DiagramViewEntity entity = new DiagramViewEntity();
        entity.workspaceId = workspace.id;
        entity.name = name;
        entity.description = "Seeded " + name.toLowerCase() + " for the anonymous reference architecture.";
        entity.type = type;
        entity.scopeElementId = scopeElementId;
        entity.layoutDirection = LayoutDirection.LEFT_TO_RIGHT;
        entity.settings = Map.of("showLegend", true, "showMetadataBadges", true);
        entity.persist();
        return entity;
    }

    private void member(DiagramViewEntity view, ArchitectureElementEntity element, double x, double y, double width, double height) {
        DiagramViewElementEntity entity = new DiagramViewElementEntity();
        entity.viewId = view.id;
        entity.elementId = element.id;
        entity.x = x;
        entity.y = y;
        entity.width = width;
        entity.height = height;
        entity.visible = true;
        entity.displaySettings = new LinkedHashMap<>();
        entity.persist();
    }

    private void viewRelationship(DiagramViewEntity view, ArchitectureRelationshipEntity relationship) {
        DiagramViewRelationshipEntity entity = new DiagramViewRelationshipEntity();
        entity.viewId = view.id;
        entity.relationshipId = relationship.id;
        entity.visible = true;
        entity.displaySettings = new LinkedHashMap<>();
        entity.persist();
    }
}
