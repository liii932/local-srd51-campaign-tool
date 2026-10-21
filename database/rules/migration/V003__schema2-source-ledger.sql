-- Offline RULES chain; review and execute separately in the exact dnd_tool_rules default schema.
-- Adds transport storage and explicit acceptance only; no runtime materialization or release approval.
-- Stop on any error. DDL may commit; do not rerun a partially applied script.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE rules_v003_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO rules_v003_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_rules'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0
        OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM rule_schema_meta) = 2
    AND (SELECT COUNT(*) FROM rule_schema_meta WHERE schema_version = 1
        AND script_name = _binary'V001__rule-source-schema.sql'
        AND script_sha256 = _binary'003916bc758315351e46f177fec10eb70f2b1275a524d270c6eb89a602cd130e') = 1
    AND (SELECT COUNT(*) FROM rule_schema_meta WHERE schema_version = 2
        AND script_name = _binary'V002__tool-catalog.sql'
        AND script_sha256 = _binary'2a5a8a67ebef60754ff9ca572d651878558ccc56ed2bfd311ce811ef42082c40') = 1
    AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()
        AND table_name IN ('rule_schema2_artifact', 'rule_schema2_acceptance', 'rule_schema2_control')) = 0
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE rules_v003_preflight;

CREATE TABLE rule_schema2_artifact (
    module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    installation_sequence BIGINT NOT NULL,
    source_operation_id VARBINARY(16) NOT NULL,
    operation_fingerprint_version INT NOT NULL,
    operation_digest_sha256 VARBINARY(64) NOT NULL,
    author_schema_version INT NOT NULL,
    artifact_protocol_version INT NOT NULL,
    canonical_profile VARBINARY(64) NOT NULL,
    package_role VARBINARY(13) NOT NULL,
    raw_zip_sha256 VARBINARY(64) NOT NULL,
    logical_sha256 VARBINARY(64) NOT NULL,
    raw_zip_byte_length BIGINT NOT NULL,
    expanded_byte_length BIGINT NOT NULL,
    original_zip MEDIUMBLOB NOT NULL,
    installed_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (module_key, release_version),
    UNIQUE KEY uq_s2_artifact_sequence (installation_sequence),
    UNIQUE KEY uq_s2_artifact_operation (source_operation_id),
    UNIQUE KEY uq_s2_artifact_binding (module_key, release_version, source_operation_id),
    CONSTRAINT ck_s2_artifact_key CHECK (OCTET_LENGTH(module_key) BETWEEN 1 AND 64
        AND REGEXP_LIKE(CONVERT(module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c')
        AND NOT REGEXP_LIKE(CONVERT(module_key USING latin1), '[^a-z0-9_]', 'c')),
    CONSTRAINT ck_s2_artifact_version CHECK (OCTET_LENGTH(release_version) BETWEEN 1 AND 64
        AND REGEXP_LIKE(CONVERT(release_version USING latin1), '^[A-Za-z0-9]', 'c')
        AND NOT REGEXP_LIKE(CONVERT(release_version USING latin1), '[^A-Za-z0-9._-]', 'c')),
    CONSTRAINT ck_s2_artifact_protocol CHECK (author_schema_version = 2 AND artifact_protocol_version = 1
        AND operation_fingerprint_version = 1 AND canonical_profile = _binary'schema2-production-json-1'),
    CONSTRAINT ck_s2_artifact_role CHECK (package_role IN (_binary'BASE', _binary'EXTENSION', _binary'COMPATIBILITY')),
    CONSTRAINT ck_s2_artifact_sequence CHECK (installation_sequence BETWEEN 1 AND 16),
    CONSTRAINT ck_s2_artifact_bytes CHECK (raw_zip_byte_length BETWEEN 1 AND 2097152
        AND OCTET_LENGTH(original_zip) = raw_zip_byte_length),
    CONSTRAINT ck_s2_artifact_expanded_bytes CHECK (expanded_byte_length BETWEEN 1 AND 1146880),
    CONSTRAINT ck_s2_artifact_source_operation_id CHECK (OCTET_LENGTH(source_operation_id) = 16
        AND (ORD(SUBSTRING(source_operation_id, 7, 1)) & 240) = 64
        AND (ORD(SUBSTRING(source_operation_id, 9, 1)) & 192) = 128),
    CONSTRAINT ck_s2_artifact_operation_digest_sha256 CHECK (OCTET_LENGTH(operation_digest_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(operation_digest_sha256 USING latin1), '[^0-9a-f]', 'c')),
    CONSTRAINT ck_s2_artifact_raw_zip_sha256 CHECK (OCTET_LENGTH(raw_zip_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(raw_zip_sha256 USING latin1), '[^0-9a-f]', 'c')),
    CONSTRAINT ck_s2_artifact_logical_sha256 CHECK (OCTET_LENGTH(logical_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(logical_sha256 USING latin1), '[^0-9a-f]', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_schema2_acceptance (
    module_key VARBINARY(64) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    acceptance_sequence BIGINT NOT NULL,
    acceptance_operation_id VARBINARY(16) NOT NULL,
    operation_fingerprint_version INT NOT NULL,
    operation_digest_sha256 VARBINARY(64) NOT NULL,
    installed_operation_id VARBINARY(16) NOT NULL,
    installed_operation_digest_sha256 VARBINARY(64) NOT NULL,
    installation_sequence BIGINT NOT NULL,
    author_schema_version INT NOT NULL,
    artifact_protocol_version INT NOT NULL,
    canonical_profile VARBINARY(64) NOT NULL,
    package_role VARBINARY(13) NOT NULL,
    raw_zip_sha256 VARBINARY(64) NOT NULL,
    logical_sha256 VARBINARY(64) NOT NULL,
    review_sha256 VARBINARY(64) NOT NULL,
    accepted_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (module_key, release_version),
    UNIQUE KEY uq_s2_acceptance_sequence (acceptance_sequence),
    UNIQUE KEY uq_s2_acceptance_operation (acceptance_operation_id),
    CONSTRAINT fk_s2_acceptance_artifact FOREIGN KEY (module_key, release_version, installed_operation_id)
        REFERENCES rule_schema2_artifact (module_key, release_version, source_operation_id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_s2_acceptance_key CHECK (OCTET_LENGTH(module_key) BETWEEN 1 AND 64
        AND REGEXP_LIKE(CONVERT(module_key USING latin1), '^[a-z][a-z0-9_]{0,63}$', 'c')
        AND NOT REGEXP_LIKE(CONVERT(module_key USING latin1), '[^a-z0-9_]', 'c')),
    CONSTRAINT ck_s2_acceptance_version CHECK (OCTET_LENGTH(release_version) BETWEEN 1 AND 64
        AND REGEXP_LIKE(CONVERT(release_version USING latin1), '^[A-Za-z0-9]', 'c')
        AND NOT REGEXP_LIKE(CONVERT(release_version USING latin1), '[^A-Za-z0-9._-]', 'c')),
    CONSTRAINT ck_s2_acceptance_protocol CHECK (author_schema_version = 2 AND artifact_protocol_version = 1
        AND operation_fingerprint_version = 1 AND canonical_profile = _binary'schema2-production-json-1'),
    CONSTRAINT ck_s2_acceptance_role CHECK (package_role IN (_binary'BASE', _binary'EXTENSION', _binary'COMPATIBILITY')),
    CONSTRAINT ck_s2_acceptance_sequence CHECK (acceptance_sequence BETWEEN 1 AND 16
        AND installation_sequence BETWEEN 1 AND 16),
    CONSTRAINT ck_s2_acceptance_acceptance_operation_id CHECK (OCTET_LENGTH(acceptance_operation_id) = 16
        AND (ORD(SUBSTRING(acceptance_operation_id, 7, 1)) & 240) = 64
        AND (ORD(SUBSTRING(acceptance_operation_id, 9, 1)) & 192) = 128),
    CONSTRAINT ck_s2_acceptance_installed_operation_id CHECK (OCTET_LENGTH(installed_operation_id) = 16
        AND (ORD(SUBSTRING(installed_operation_id, 7, 1)) & 240) = 64
        AND (ORD(SUBSTRING(installed_operation_id, 9, 1)) & 192) = 128),
    CONSTRAINT ck_s2_acceptance_operation_digest_sha256 CHECK (OCTET_LENGTH(operation_digest_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(operation_digest_sha256 USING latin1), '[^0-9a-f]', 'c')),
    CONSTRAINT ck_s2_acceptance_installed_operation_digest_sha256 CHECK (OCTET_LENGTH(installed_operation_digest_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(installed_operation_digest_sha256 USING latin1), '[^0-9a-f]', 'c')),
    CONSTRAINT ck_s2_acceptance_raw_zip_sha256 CHECK (OCTET_LENGTH(raw_zip_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(raw_zip_sha256 USING latin1), '[^0-9a-f]', 'c')),
    CONSTRAINT ck_s2_acceptance_logical_sha256 CHECK (OCTET_LENGTH(logical_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(logical_sha256 USING latin1), '[^0-9a-f]', 'c')),
    CONSTRAINT ck_s2_acceptance_review_sha256 CHECK (OCTET_LENGTH(review_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(review_sha256 USING latin1), '[^0-9a-f]', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

-- Separate schema 2 counters never alter the original schema 1 accounting domain.
CREATE TABLE rule_schema2_control (
    control_id TINYINT NOT NULL PRIMARY KEY,
    protocol_version INT NOT NULL,
    metadata_row_count BIGINT NOT NULL,
    installation_count BIGINT NOT NULL,
    acceptance_count BIGINT NOT NULL,
    raw_zip_byte_count BIGINT NOT NULL,
    row_version BIGINT NOT NULL,
    CONSTRAINT ck_s2_control_identity CHECK (control_id = 1 AND protocol_version = 1),
    CONSTRAINT ck_s2_control_counts CHECK (installation_count BETWEEN 0 AND 16
        AND acceptance_count BETWEEN 0 AND installation_count
        AND metadata_row_count = 1 + installation_count + acceptance_count
        AND row_version = installation_count + acceptance_count),
    CONSTRAINT ck_s2_control_bytes CHECK (raw_zip_byte_count BETWEEN 0 AND 8388608
        AND ((installation_count = 0 AND raw_zip_byte_count = 0)
            OR (installation_count > 0 AND raw_zip_byte_count > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DELIMITER $$
CREATE TRIGGER rule_s2_artifact_insert BEFORE INSERT ON rule_schema2_artifact FOR EACH ROW
BEGIN
    DECLARE shared_protocol INT DEFAULT NULL;
    DECLARE installed_count BIGINT DEFAULT NULL;
    DECLARE byte_count BIGINT DEFAULT NULL;
    SELECT protocol_version INTO shared_protocol FROM rule_installation_control WHERE control_id = 1 FOR UPDATE;
    SELECT installation_count, raw_zip_byte_count INTO installed_count, byte_count
        FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF shared_protocol IS NULL OR shared_protocol <> 1 OR installed_count IS NULL
        OR NEW.installation_sequence <> installed_count + 1 OR installed_count >= 16
        OR byte_count + NEW.raw_zip_byte_length > 8388608
        OR (SELECT COALESCE(SUM(expanded_byte_length), 0) FROM rule_schema2_artifact) + NEW.expanded_byte_length > 8388608
        OR CAST(SHA2(NEW.original_zip, 256) AS BINARY) <> NEW.raw_zip_sha256
        OR EXISTS (SELECT 1 FROM rule_schema2_acceptance WHERE acceptance_operation_id = NEW.source_operation_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid schema2 installation';
    END IF;
    SET NEW.installed_at = UTC_TIMESTAMP(6);
END$$
CREATE TRIGGER rule_s2_artifact_update BEFORE UPDATE ON rule_schema2_artifact FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 installation';
END$$
CREATE TRIGGER rule_s2_artifact_delete BEFORE DELETE ON rule_schema2_artifact FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 installation';
END$$
CREATE TRIGGER rule_s2_acceptance_insert BEFORE INSERT ON rule_schema2_acceptance FOR EACH ROW
BEGIN
    DECLARE shared_protocol INT DEFAULT NULL;
    DECLARE accepted_count BIGINT DEFAULT NULL;
    SELECT protocol_version INTO shared_protocol FROM rule_installation_control WHERE control_id = 1 FOR UPDATE;
    SELECT acceptance_count INTO accepted_count FROM rule_schema2_control WHERE control_id = 1 FOR UPDATE;
    IF shared_protocol IS NULL OR shared_protocol <> 1 OR accepted_count IS NULL
        OR NEW.acceptance_sequence <> accepted_count + 1
        OR EXISTS (SELECT 1 FROM rule_schema2_artifact WHERE source_operation_id = NEW.acceptance_operation_id)
        OR NOT EXISTS (SELECT 1 FROM rule_schema2_artifact
            WHERE module_key = NEW.module_key AND release_version = NEW.release_version
            AND source_operation_id = NEW.installed_operation_id
            AND operation_digest_sha256 = NEW.installed_operation_digest_sha256
            AND installation_sequence = NEW.installation_sequence
            AND author_schema_version = NEW.author_schema_version
            AND artifact_protocol_version = NEW.artifact_protocol_version
            AND canonical_profile = NEW.canonical_profile AND package_role = NEW.package_role
            AND raw_zip_sha256 = NEW.raw_zip_sha256 AND logical_sha256 = NEW.logical_sha256) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Acceptance requires exact installed artifact';
    END IF;
    SET NEW.accepted_at = UTC_TIMESTAMP(6);
END$$
CREATE TRIGGER rule_s2_acceptance_update BEFORE UPDATE ON rule_schema2_acceptance FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable schema2 acceptance';
END$$
CREATE TRIGGER rule_s2_acceptance_delete BEFORE DELETE ON rule_schema2_acceptance FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 acceptance';
END$$
CREATE TRIGGER rule_s2_control_insert BEFORE INSERT ON rule_schema2_control FOR EACH ROW
BEGIN
    IF NEW.control_id <> 1 OR NEW.protocol_version <> 1 OR NEW.metadata_row_count <> 1
        OR NEW.installation_count <> 0 OR NEW.acceptance_count <> 0
        OR NEW.raw_zip_byte_count <> 0 OR NEW.row_version <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid initial schema2 control';
    END IF;
END$$
CREATE TRIGGER rule_s2_control_update BEFORE UPDATE ON rule_schema2_control FOR EACH ROW
BEGIN
    DECLARE shared_protocol INT DEFAULT NULL;
    SELECT protocol_version INTO shared_protocol FROM rule_installation_control WHERE control_id = 1 FOR UPDATE;
    IF shared_protocol IS NULL OR shared_protocol <> 1
        OR NEW.control_id <> OLD.control_id OR NEW.protocol_version <> OLD.protocol_version
        OR NEW.metadata_row_count <> OLD.metadata_row_count + 1 OR NEW.row_version <> OLD.row_version + 1
        OR NOT ((NEW.installation_count = OLD.installation_count + 1 AND NEW.acceptance_count = OLD.acceptance_count)
            OR (NEW.installation_count = OLD.installation_count AND NEW.acceptance_count = OLD.acceptance_count + 1))
        OR NEW.installation_count <> (SELECT COUNT(*) FROM rule_schema2_artifact)
        OR NEW.acceptance_count <> (SELECT COUNT(*) FROM rule_schema2_acceptance)
        OR NEW.raw_zip_byte_count <> (SELECT COALESCE(SUM(raw_zip_byte_length), 0) FROM rule_schema2_artifact) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid schema2 control transition';
    END IF;
END$$
CREATE TRIGGER rule_s2_control_delete BEFORE DELETE ON rule_schema2_control FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent schema2 control';
END$$
DELIMITER ;
INSERT INTO rule_schema2_control (control_id, protocol_version, metadata_row_count,
    installation_count, acceptance_count, raw_zip_byte_count, row_version)
VALUES (1, 1, 1, 0, 0, 0, 0);
-- CHECKSUM-SCOPE-END

-- Record only after all objects and the independent definition audit pass.
INSERT INTO rule_schema_meta (schema_version, script_name, script_sha256)
VALUES (3, 'V003__schema2-source-ledger.sql', '4166a7f4b4d8670a8300c4129ce566e177b824dda24c58ee3d208f9880048cdf');

