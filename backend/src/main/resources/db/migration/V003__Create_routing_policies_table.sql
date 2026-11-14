-- Phase 07: Configurable Notification Routing Policies
-- Create routing_policies table for tenant-specific notification routing policies

CREATE TABLE IF NOT EXISTS routing_policies (
    id UUID PRIMARY KEY NOT NULL,
    tenant_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    primary_channels VARCHAR(255) NOT NULL,
    fallback_channels VARCHAR(255),
    priority VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_routing_policy_tenant FOREIGN KEY (tenant_id) 
        REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT uq_tenant_event_type UNIQUE (tenant_id, event_type)
);

-- Indexes for efficient queries
CREATE INDEX idx_routing_policy_tenant_id ON routing_policies(tenant_id);
CREATE INDEX idx_routing_policy_tenant_event_type ON routing_policies(tenant_id, event_type);
CREATE INDEX idx_routing_policy_enabled ON routing_policies(enabled);
