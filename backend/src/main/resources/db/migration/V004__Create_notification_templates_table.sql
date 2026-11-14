-- Phase 08: Create notification_templates table for template management

CREATE TABLE notification_templates (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    subject VARCHAR(500),
    body TEXT NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    
    CONSTRAINT fk_template_tenant 
        FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT uq_template_tenant_event_channel 
        UNIQUE (tenant_id, event_type, channel)
);

-- Create indexes for common query patterns
CREATE INDEX idx_template_tenant_id ON notification_templates(tenant_id);
CREATE INDEX idx_template_tenant_event ON notification_templates(tenant_id, event_type);
CREATE INDEX idx_template_tenant_channel ON notification_templates(tenant_id, channel);
