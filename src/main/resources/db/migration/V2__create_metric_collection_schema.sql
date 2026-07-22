CREATE TABLE collection_attempt (
    id uuid NOT NULL,
    appliance_id uuid NOT NULL,
    trigger varchar(16) NOT NULL,
    outcome varchar(20) NOT NULL,
    started_at timestamptz NOT NULL,
    completed_at timestamptz NOT NULL,
    sample_count integer NOT NULL,
    failure_category varchar(20),
    failure_message varchar(500),
    retry_after_seconds integer,
    next_collection_due_at timestamptz,
    CONSTRAINT pk_collection_attempt PRIMARY KEY (id),
    CONSTRAINT fk_collection_attempt_appliance
        FOREIGN KEY (appliance_id)
        REFERENCES appliance (id)
        ON DELETE RESTRICT,
    CONSTRAINT uk_collection_attempt_id_appliance_id
        UNIQUE (id, appliance_id),
    CONSTRAINT ck_collection_attempt_trigger
        CHECK (trigger IN ('MANUAL', 'SCHEDULED')),
    CONSTRAINT ck_collection_attempt_outcome
        CHECK (outcome IN ('SUCCESS', 'PARTIAL_SUCCESS', 'FAILED')),
    CONSTRAINT ck_collection_attempt_completed_at_not_before_started_at
        CHECK (completed_at >= started_at),
    CONSTRAINT ck_collection_attempt_sample_count_non_negative
        CHECK (sample_count >= 0),
    CONSTRAINT ck_collection_attempt_outcome_sample_count
        CHECK (
            (
                outcome IN ('SUCCESS', 'PARTIAL_SUCCESS')
                AND sample_count > 0
            )
            OR
            (
                outcome = 'FAILED'
                AND sample_count = 0
            )
        ),
    CONSTRAINT ck_collection_attempt_failure_category
        CHECK (
            failure_category IS NULL
            OR failure_category IN (
                'TIMEOUT',
                'RATE_LIMITED',
                'INVALID_DATA',
                'TRANSIENT',
                'UNEXPECTED'
            )
        ),
    CONSTRAINT ck_collection_attempt_failure_fields
        CHECK (
            (
                outcome IN ('SUCCESS', 'PARTIAL_SUCCESS')
                AND failure_category IS NULL
                AND failure_message IS NULL
                AND retry_after_seconds IS NULL
            )
            OR
            (
                outcome = 'FAILED'
                AND failure_category IS NOT NULL
            )
        ),
    CONSTRAINT ck_collection_attempt_retry_after_seconds
        CHECK (
            retry_after_seconds IS NULL
            OR
            (
                retry_after_seconds > 0
                AND failure_category = 'RATE_LIMITED'
            )
        ),
    CONSTRAINT ck_collection_attempt_next_due_not_before_completed_at
        CHECK (
            next_collection_due_at IS NULL
            OR next_collection_due_at >= completed_at
        )
);

CREATE INDEX idx_collection_attempt_appliance_started_at_id
    ON collection_attempt (appliance_id, started_at DESC, id ASC);

CREATE INDEX idx_collection_attempt_appliance_trigger_started_at_id
    ON collection_attempt (appliance_id, trigger, started_at DESC, id ASC);

CREATE INDEX idx_collection_attempt_appliance_outcome_started_at_id
    ON collection_attempt (appliance_id, outcome, started_at DESC, id ASC);

CREATE TABLE collection_warning (
    collection_attempt_id uuid NOT NULL,
    warning_index integer NOT NULL,
    code varchar(64) NOT NULL,
    message varchar(500) NOT NULL,
    CONSTRAINT pk_collection_warning
        PRIMARY KEY (collection_attempt_id, warning_index),
    CONSTRAINT fk_collection_warning_attempt
        FOREIGN KEY (collection_attempt_id)
        REFERENCES collection_attempt (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_collection_warning_index_non_negative
        CHECK (warning_index >= 0),
    CONSTRAINT ck_collection_warning_code_format
        CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_collection_warning_message_non_blank
        CHECK (btrim(message) <> '')
);

CREATE TABLE metric_sample (
    id uuid NOT NULL,
    appliance_id uuid NOT NULL,
    collection_attempt_id uuid NOT NULL,
    metric_name varchar(64) NOT NULL,
    unit varchar(32) NOT NULL,
    value numeric(20,6) NOT NULL,
    observed_at timestamptz NOT NULL,
    ingested_at timestamptz NOT NULL,
    CONSTRAINT pk_metric_sample PRIMARY KEY (id),
    CONSTRAINT fk_metric_sample_appliance
        FOREIGN KEY (appliance_id)
        REFERENCES appliance (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_metric_sample_attempt_appliance
        FOREIGN KEY (collection_attempt_id, appliance_id)
        REFERENCES collection_attempt (id, appliance_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_metric_sample_metric_name_format
        CHECK (metric_name ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_metric_sample_unit_format
        CHECK (unit ~ '^[A-Z][A-Z0-9_]*$'),
    CONSTRAINT ck_metric_sample_value_finite
        CHECK (value NOT IN ('NaN'::numeric, 'Infinity'::numeric, '-Infinity'::numeric))
);

CREATE INDEX idx_metric_sample_appliance_observed_at_id
    ON metric_sample (appliance_id, observed_at ASC, id ASC);

CREATE INDEX idx_metric_sample_observed_at_appliance_metric_unit
    ON metric_sample (observed_at ASC, appliance_id, metric_name, unit)
    INCLUDE (value);
