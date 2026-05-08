-- Users table
CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(32) PRIMARY KEY COMMENT 'ULID-style identifier',
    email VARCHAR(255) NOT NULL UNIQUE COMMENT 'User email address',
    password_hash VARCHAR(255) COMMENT 'BCrypt hashed password',
    nickname VARCHAR(100) NOT NULL COMMENT 'User nickname',
    communication_style VARCHAR(50) COMMENT 'Communication style (wave, mountain, flame, leaf, moon, star)',
    onboarding_answers JSON COMMENT 'Onboarding survey responses (List<Integer>)',
    roles JSON NOT NULL DEFAULT '["USER"]' COMMENT 'User roles (List<String>)',
    deleted_at TIMESTAMP(3) COMMENT 'Soft delete timestamp',
    created_at TIMESTAMP(3) NOT NULL COMMENT 'Creation timestamp',
    updated_at TIMESTAMP(3) NOT NULL COMMENT 'Last update timestamp'
) COMMENT='User accounts and profile information';

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_created_at ON users(created_at);

-- Sessions table
CREATE TABLE IF NOT EXISTS sessions (
    id VARCHAR(32) PRIMARY KEY COMMENT 'ULID-style identifier',
    created_by_user_id VARCHAR(32) NOT NULL COMMENT 'Session creator (User A)',
    invitee_user_id VARCHAR(32) COMMENT 'Session invitee/acceptor (User B)',
    invitee_guest_name VARCHAR(100) COMMENT 'Guest name if invitee is guest',
    invite_token VARCHAR(64) UNIQUE COMMENT 'Unique invite token',
    invite_expires_at TIMESTAMP(3) COMMENT 'Invite token expiration',
    relationship_type VARCHAR(32) COMMENT 'RelationType enum',
    conflict_type VARCHAR(32) COMMENT 'ConflictType enum',
    category JSON COMMENT 'Conflict category (SessionCategory)',
    status VARCHAR(32) NOT NULL COMMENT 'SessionStatus enum',
    current_turn INT DEFAULT 0 COMMENT 'Current turn number (0-6)',
    current_role_value VARCHAR(8) COMMENT 'Current role (A, B, MEDIATOR)',
    solo_mode BOOLEAN DEFAULT false COMMENT 'Solo mode flag',
    report_id VARCHAR(32) COMMENT 'Associated report ID',
    content_expires_at TIMESTAMP(3) COMMENT '30-day TTL for sensitive content',
    crisis_flags JSON COMMENT 'Crisis detection flags (List<String>)',
    completed_at TIMESTAMP(3) COMMENT 'Session completion timestamp',
    created_at TIMESTAMP(3) NOT NULL COMMENT 'Creation timestamp',
    updated_at TIMESTAMP(3) NOT NULL COMMENT 'Last update timestamp'
) COMMENT='Conflict mediation sessions';

CREATE INDEX IF NOT EXISTS idx_sessions_created_by_user_id ON sessions(created_by_user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_invitee_user_id ON sessions(invitee_user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_invite_token ON sessions(invite_token);
CREATE INDEX IF NOT EXISTS idx_sessions_status ON sessions(status);
CREATE INDEX IF NOT EXISTS idx_sessions_content_expires_at ON sessions(content_expires_at);
CREATE INDEX IF NOT EXISTS idx_sessions_created_at ON sessions(created_at);

-- Turns table
CREATE TABLE IF NOT EXISTS turns (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Auto-increment ID',
    session_id VARCHAR(32) NOT NULL COMMENT 'Foreign key to sessions',
    turn_number INT NOT NULL COMMENT 'Turn sequence number (1-6)',
    role VARCHAR(32) COMMENT 'TurnRole enum (A, B, MEDIATOR)',
    user_id VARCHAR(32) COMMENT 'User who submitted the turn',
    content LONGTEXT COMMENT 'User input text (TTL purge after 30 days)',
    mediator_message LONGTEXT COMMENT 'LLM mediator response (TTL purge)',
    mediator_summary_for_opponent LONGTEXT COMMENT 'Neutral summary for opponent (TTL purge)',
    is_perspective_taking BOOLEAN DEFAULT false COMMENT 'Perspective-taking turn flag',
    skipped BOOLEAN DEFAULT false COMMENT 'Turn skipped flag',
    tokens_used INT DEFAULT 0 COMMENT 'LLM tokens used',
    llm_latency_ms BIGINT DEFAULT 0 COMMENT 'LLM latency in milliseconds',
    created_at TIMESTAMP(3) NOT NULL COMMENT 'Creation timestamp',
    CONSTRAINT fk_turns_session FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    UNIQUE KEY uk_turns_session_number (session_id, turn_number)
) COMMENT='Individual turns within a session';

CREATE INDEX IF NOT EXISTS idx_turns_session_id ON turns(session_id);
CREATE INDEX IF NOT EXISTS idx_turns_user_id ON turns(user_id);

-- Reports table
CREATE TABLE IF NOT EXISTS reports (
    id VARCHAR(32) PRIMARY KEY COMMENT 'ULID-style identifier',
    session_id VARCHAR(32) NOT NULL UNIQUE COMMENT 'Associated session ID',
    participant_a JSON COMMENT 'Participant A snapshot (Participant)',
    participant_b JSON COMMENT 'Participant B snapshot (Participant, nullable for solo)',
    conflict_type VARCHAR(32) COMMENT 'ConflictType enum',
    solo_mode BOOLEAN COMMENT 'Solo mode flag',
    contribution_ratio JSON COMMENT 'Contribution ratio analysis (ContributionRatio)',
    needs_map JSON COMMENT 'Needs map analysis (NeedsMap)',
    temperature DECIMAL(3, 1) COMMENT 'Relationship temperature (36.0-37.5)',
    four_horsemen JSON COMMENT 'Four Horsemen analysis (FourHorsemenAnalysis)',
    nvc_scripts JSON COMMENT 'NVC scripts (NVCScripts)',
    repair_suggestions JSON COMMENT 'Repair suggestions (List<String>)',
    llm_provider VARCHAR(50) COMMENT 'LLM provider name',
    llm_call_count INT COMMENT 'Number of LLM calls',
    generation_duration_ms BIGINT COMMENT 'Report generation duration',
    a_pattern_feedback LONGTEXT COMMENT 'Pattern feedback for solo mode',
    suggested_approach LONGTEXT COMMENT 'Suggested approach for solo',
    invite_again_cta LONGTEXT COMMENT 'Call-to-action for re-invitation',
    created_at TIMESTAMP(3) NOT NULL COMMENT 'Creation timestamp'
) COMMENT='Analysis reports generated from completed sessions';

CREATE INDEX IF NOT EXISTS idx_reports_session_id ON reports(session_id);
CREATE INDEX IF NOT EXISTS idx_reports_created_at ON reports(created_at);

-- User relationships table (Neo4j replacement)
CREATE TABLE IF NOT EXISTS user_relationships (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Auto-increment ID',
    user_a_id VARCHAR(32) NOT NULL COMMENT 'Primary user ID (canonical: smaller)',
    user_b_id VARCHAR(32) COMMENT 'Secondary user ID (canonical: larger, nullable for guests)',
    user_b_guest_name VARCHAR(100) COMMENT 'Guest name if user_b is guest',
    relationship_type VARCHAR(32) NOT NULL COMMENT 'RelationType enum',
    first_session_at TIMESTAMP(3) COMMENT 'First session date',
    last_session_at TIMESTAMP(3) COMMENT 'Last session date',
    session_count INT DEFAULT 0 COMMENT 'Total sessions',
    average_temperature DECIMAL(3, 1) COMMENT 'Average relationship temperature',
    CONSTRAINT uk_user_relationships_a_b_type UNIQUE (user_a_id, user_b_id, relationship_type)
) COMMENT='User relationships (replaces Neo4j PersonNode and relationships)';

CREATE INDEX IF NOT EXISTS idx_user_relationships_user_a_id ON user_relationships(user_a_id);
CREATE INDEX IF NOT EXISTS idx_user_relationships_user_b_id ON user_relationships(user_b_id);

-- Conflict history table
CREATE TABLE IF NOT EXISTS conflict_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Auto-increment ID',
    session_id VARCHAR(32) NOT NULL COMMENT 'Session identifier',
    user_a_id VARCHAR(32) NOT NULL COMMENT 'User A ID',
    user_b_id VARCHAR(32) COMMENT 'User B ID (nullable for guests)',
    relationship_type VARCHAR(32) COMMENT 'RelationType',
    conflict_type VARCHAR(32) COMMENT 'ConflictType',
    temperature DECIMAL(3, 1) COMMENT 'Conflict temperature reading',
    created_at TIMESTAMP(3) NOT NULL COMMENT 'Record creation timestamp'
) COMMENT='Historical conflict records for analytics';

CREATE INDEX IF NOT EXISTS idx_conflict_history_session_id ON conflict_history(session_id);
CREATE INDEX IF NOT EXISTS idx_conflict_history_user_pair ON conflict_history(user_a_id, user_b_id);

-- Temperature history table
CREATE TABLE IF NOT EXISTS temperature_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Auto-increment ID',
    user_id VARCHAR(32) NOT NULL COMMENT 'User ID',
    related_user_id VARCHAR(32) COMMENT 'Related user ID (nullable)',
    session_id VARCHAR(32) NOT NULL COMMENT 'Session ID',
    temperature DECIMAL(3, 1) COMMENT 'Temperature reading',
    recorded_at TIMESTAMP(3) NOT NULL COMMENT 'Recording timestamp'
) COMMENT='Temperature history per user and session';

CREATE INDEX IF NOT EXISTS idx_temperature_history_user_id ON temperature_history(user_id);
CREATE INDEX IF NOT EXISTS idx_temperature_history_session_id ON temperature_history(session_id);

-- LLM call logs table
CREATE TABLE IF NOT EXISTS llm_call_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Auto-increment ID',
    correlation_id VARCHAR(64) COMMENT 'Request correlation ID',
    provider VARCHAR(50) COMMENT 'LLM provider (e.g., claude)',
    session_id VARCHAR(32) COMMENT 'Associated session ID',
    turn_number INT COMMENT 'Associated turn number',
    tokens_used INT COMMENT 'Tokens consumed',
    latency_ms BIGINT COMMENT 'Response latency',
    input_length INT COMMENT 'Input token count',
    output_length INT COMMENT 'Output token count',
    outcome VARCHAR(32) COMMENT 'Call outcome (success, fallback, timeout, error)',
    error_code VARCHAR(64) COMMENT 'Error code if applicable',
    created_at TIMESTAMP(3) NOT NULL COMMENT 'Log timestamp'
) COMMENT='LLM call performance and diagnostics';

CREATE INDEX IF NOT EXISTS idx_llm_call_logs_correlation_id ON llm_call_logs(correlation_id);
CREATE INDEX IF NOT EXISTS idx_llm_call_logs_session_id ON llm_call_logs(session_id);
CREATE INDEX IF NOT EXISTS idx_llm_call_logs_created_at ON llm_call_logs(created_at);

COMMIT;
