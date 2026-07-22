CREATE TABLE appliance (
    id uuid NOT NULL,
    display_name varchar(100) NOT NULL,
    description varchar(500),
    vendor_key varchar(50) NOT NULL,
    external_reference varchar(128) NOT NULL,
    collection_state varchar(16) NOT NULL DEFAULT 'ACTIVE',
    collection_interval_seconds integer NOT NULL,
    next_collection_due_at timestamptz,
    consecutive_failure_count integer NOT NULL DEFAULT 0,
    last_collection_status varchar(20) NOT NULL DEFAULT 'NEVER_ATTEMPTED',
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT pk_appliance PRIMARY KEY (id),
    CONSTRAINT uk_appliance_vendor_key_external_reference
        UNIQUE (vendor_key, external_reference),
    CONSTRAINT ck_appliance_display_name_non_blank
        CHECK (btrim(display_name) <> ''),
    CONSTRAINT ck_appliance_vendor_key_format
        CHECK (vendor_key ~ '^[a-z0-9-]+$'),
    CONSTRAINT ck_appliance_external_reference_non_blank
        CHECK (btrim(external_reference) <> ''),
    CONSTRAINT ck_appliance_collection_interval_seconds
        CHECK (collection_interval_seconds BETWEEN 5 AND 86400),
    CONSTRAINT ck_appliance_collection_state
        CHECK (collection_state IN ('ACTIVE', 'PAUSED')),
    CONSTRAINT ck_appliance_last_collection_status
        CHECK (last_collection_status IN (
            'NEVER_ATTEMPTED',
            'SUCCESS',
            'PARTIAL_SUCCESS',
            'FAILED'
        )),
    CONSTRAINT ck_appliance_consecutive_failure_count
        CHECK (consecutive_failure_count >= 0),
    CONSTRAINT ck_appliance_version
        CHECK (version >= 0),
    CONSTRAINT ck_appliance_updated_at_not_before_created_at
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_appliance_collection_state_due
        CHECK (
            (
                collection_state = 'ACTIVE'
                AND next_collection_due_at IS NOT NULL
            )
            OR
            (
                collection_state = 'PAUSED'
                AND next_collection_due_at IS NULL
            )
        )
);

CREATE INDEX idx_appliance_created_at_id
    ON appliance (created_at ASC, id ASC);

CREATE INDEX idx_appliance_collection_state_created_at_id
    ON appliance (collection_state, created_at ASC, id ASC);

CREATE INDEX idx_appliance_active_due_at_id
    ON appliance (next_collection_due_at ASC, id ASC)
    WHERE collection_state = 'ACTIVE';
