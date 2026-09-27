-- Offline RULES chain. Connect explicitly to dnd_tool_rules; never run from Web startup.
-- MySQL 8.0.16+; strict SQL mode and utf8mb4 client input are deployment prerequisites.
-- No CREATE DATABASE, USE, account changes, runtime references or rule-content seeds.
-- CHECKSUM-SCOPE-BEGIN
CREATE TABLE rule_schema_meta (
    schema_version INT NOT NULL PRIMARY KEY,
    script_name VARBINARY(255) NOT NULL UNIQUE,
    script_sha256 VARBINARY(64) NOT NULL,
    applied_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    CONSTRAINT ck_rule_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_rule_schema_name CHECK (
        REGEXP_LIKE(CONVERT(script_name USING latin1), '^V[0-9]{3,}__[a-z][a-z0-9]*(-[a-z0-9]+)*[.]sql$', 'c')
        AND NOT REGEXP_LIKE(CONVERT(script_name USING latin1), '[^A-Za-z0-9_.-]', 'c')
        AND SUBSTRING_INDEX(CONVERT(script_name USING latin1), '__', 1) =
            CONCAT('V', LPAD(CAST(schema_version AS CHAR), GREATEST(3, CHAR_LENGTH(CAST(schema_version AS CHAR))), '0'))),
    CONSTRAINT ck_rule_schema_digest CHECK (OCTET_LENGTH(script_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(script_sha256 USING latin1), '[^0-9a-f]', 'c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_release (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    module_key VARBINARY(128) NOT NULL,
    release_version VARBINARY(64) NOT NULL,
    canonical_format_version INT NOT NULL,
    archive_format_version INT NOT NULL,
    hash_algorithm VARBINARY(7) NOT NULL,
    content_sha256 VARBINARY(64) NULL,
    release_status VARBINARY(8) NOT NULL DEFAULT 'DRAFT',
    installation_revision BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    released_at DATETIME(6) NULL,
    UNIQUE KEY uq_rule_release_identity (module_key, release_version),
    CONSTRAINT ck_rule_release_key CHECK (OCTET_LENGTH(module_key) BETWEEN 1 AND 128
        AND REGEXP_LIKE(CONVERT(module_key USING latin1), '^[a-z][a-z0-9_]*([.][a-z][a-z0-9_]*)*$', 'c')
        AND NOT REGEXP_LIKE(CONVERT(module_key USING latin1), '[^a-z0-9_.]', 'c')),
    CONSTRAINT ck_rule_release_version CHECK (OCTET_LENGTH(release_version) BETWEEN 1 AND 64
        AND REGEXP_LIKE(CONVERT(release_version USING latin1), '^[A-Za-z0-9]', 'c')
        AND NOT REGEXP_LIKE(CONVERT(release_version USING latin1), '[^A-Za-z0-9._-]', 'c')),
    CONSTRAINT ck_rule_release_formats CHECK (canonical_format_version > 0 AND archive_format_version > 0),
    CONSTRAINT ck_rule_release_algorithm CHECK (hash_algorithm = _binary'SHA-256'),
    CONSTRAINT ck_rule_release_digest CHECK (content_sha256 IS NULL OR
        (OCTET_LENGTH(content_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(content_sha256 USING latin1), '[^0-9a-f]', 'c'))),
    CONSTRAINT ck_rule_release_state CHECK (
        (release_status = _binary'DRAFT' AND released_at IS NULL)
        OR (release_status = _binary'RELEASED' AND released_at IS NOT NULL AND content_sha256 IS NOT NULL)),
    CONSTRAINT ck_rule_release_revision CHECK (installation_revision >= 0
        AND (installation_revision > 0 OR (release_status = _binary'DRAFT' AND content_sha256 IS NULL)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_language (
    release_id BIGINT NOT NULL,
    language_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    category VARBINARY(8) NOT NULL,
    source_page SMALLINT NOT NULL,
    sort_order SMALLINT NOT NULL,
    PRIMARY KEY (release_id, language_key),
    UNIQUE KEY uq_rule_language_sort (release_id, sort_order),
    CONSTRAINT fk_rule_language_release FOREIGN KEY (release_id) REFERENCES rule_release (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_rule_language_category CHECK (
        (category = _binary'STANDARD' AND language_key IN (_binary'language.common', _binary'language.dwarvish',
            _binary'language.elvish', _binary'language.giant', _binary'language.gnomish', _binary'language.goblin',
            _binary'language.halfling', _binary'language.orc'))
        OR (category = _binary'EXOTIC' AND language_key IN (_binary'language.abyssal', _binary'language.celestial',
            _binary'language.deep_speech', _binary'language.draconic', _binary'language.infernal',
            _binary'language.primordial', _binary'language.sylvan', _binary'language.undercommon'))
        OR (category = _binary'SECRET' AND language_key IN (_binary'language.druidic', _binary'language.thieves_cant'))),
    CONSTRAINT ck_rule_language_page CHECK (source_page BETWEEN 3 AND 74),
    CONSTRAINT ck_rule_language_sort CHECK (sort_order BETWEEN 1 AND 18),
    CONSTRAINT ck_rule_language_name CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120
        AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]')
        AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_rule_language_description CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 1000
        AND NOT REGEXP_LIKE(description, '[[:cntrl:]]')
        AND REGEXP_LIKE(description, '[^[:space:]]'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_package_installation (
    release_id BIGINT NOT NULL,
    installation_revision BIGINT NOT NULL,
    source_operation_id VARBINARY(16) NOT NULL,
    operation_fingerprint_version INT NOT NULL,
    operation_digest_sha256 VARBINARY(64) NOT NULL,
    author_schema_version INT NOT NULL,
    installation_manifest_version INT NOT NULL,
    installation_manifest_sha256 VARBINARY(64) NOT NULL,
    package_display_name VARCHAR(120) NOT NULL,
    verification_scope VARBINARY(9) NOT NULL,
    observed_content_sha256 VARBINARY(64) NULL,
    installed_at DATETIME(6) NOT NULL DEFAULT (UTC_TIMESTAMP(6)),
    PRIMARY KEY (release_id, installation_revision),
    UNIQUE KEY uq_rule_installation_operation (source_operation_id),
    CONSTRAINT fk_rule_installation_release FOREIGN KEY (release_id) REFERENCES rule_release (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_rule_installation_uuid CHECK (OCTET_LENGTH(source_operation_id) = 16
        AND (ORD(SUBSTRING(source_operation_id, 7, 1)) & 240) = 64
        AND (ORD(SUBSTRING(source_operation_id, 9, 1)) & 192) = 128),
    CONSTRAINT ck_rule_installation_formats CHECK (operation_fingerprint_version = 1
        AND author_schema_version > 0 AND installation_manifest_version > 0),
    CONSTRAINT ck_rule_installation_operation_digest CHECK (OCTET_LENGTH(operation_digest_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(operation_digest_sha256 USING latin1), '[^0-9a-f]', 'c')),
    CONSTRAINT ck_rule_installation_manifest_digest CHECK (OCTET_LENGTH(installation_manifest_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(installation_manifest_sha256 USING latin1), '[^0-9a-f]', 'c')),
    CONSTRAINT ck_rule_installation_observed_digest CHECK (observed_content_sha256 IS NULL OR
        (OCTET_LENGTH(observed_content_sha256) = 64
        AND NOT REGEXP_LIKE(CONVERT(observed_content_sha256 USING latin1), '[^0-9a-f]', 'c'))),
    CONSTRAINT ck_rule_installation_scope CHECK (
        (verification_scope = _binary'PARTITION' AND observed_content_sha256 IS NULL)
        OR (verification_scope = _binary'COMPLETE' AND observed_content_sha256 IS NOT NULL)),
    CONSTRAINT ck_rule_installation_name CHECK (CHAR_LENGTH(package_display_name) BETWEEN 1 AND 120
        AND NOT REGEXP_LIKE(package_display_name, '[[:cntrl:]]')
        AND REGEXP_LIKE(package_display_name, '[^[:space:]]'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_package_installation_partition (
    release_id BIGINT NOT NULL,
    installation_revision BIGINT NOT NULL,
    partition_key VARBINARY(128) NOT NULL,
    PRIMARY KEY (release_id, installation_revision, partition_key),
    CONSTRAINT fk_rule_partition_installation FOREIGN KEY (release_id, installation_revision)
        REFERENCES rule_package_installation (release_id, installation_revision)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_rule_partition_key CHECK (partition_key = _binary'character.language')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE rule_installation_control (
    control_id TINYINT NOT NULL PRIMARY KEY,
    protocol_version INT NOT NULL,
    metadata_row_count BIGINT NOT NULL,
    row_version BIGINT NOT NULL,
    CONSTRAINT ck_rule_control_id CHECK (control_id = 1),
    CONSTRAINT ck_rule_control_protocol CHECK (protocol_version = 1),
    CONSTRAINT ck_rule_control_count CHECK (metadata_row_count BETWEEN 1 AND 16384),
    CONSTRAINT ck_rule_control_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DELIMITER $$
CREATE TRIGGER rule_release_insert BEFORE INSERT ON rule_release FOR EACH ROW
BEGIN
    IF NEW.release_status <> _binary'DRAFT' OR NEW.installation_revision <> 0
        OR NEW.content_sha256 IS NOT NULL OR NEW.released_at IS NOT NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid initial rule release';
    END IF;
    SET NEW.created_at = UTC_TIMESTAMP(6);
END$$
CREATE TRIGGER rule_release_id AFTER INSERT ON rule_release FOR EACH ROW
BEGIN
    IF NEW.id <= 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid rule release id';
    END IF;
END$$
CREATE TRIGGER rule_release_update BEFORE UPDATE ON rule_release FOR EACH ROW
BEGIN
    -- UPDATE already exclusively locks this root; do not select its mutating table.
    IF OLD.release_status = _binary'RELEASED' OR NEW.id <> OLD.id
        OR NEW.module_key <> OLD.module_key OR NEW.release_version <> OLD.release_version
        OR NEW.created_at <> OLD.created_at THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable rule release';
    END IF;
    IF NEW.release_status = _binary'RELEASED' THEN
        SET NEW.released_at = UTC_TIMESTAMP(6);
    END IF;
END$$
CREATE TRIGGER rule_release_delete BEFORE DELETE ON rule_release FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent rule release';
END$$
CREATE TRIGGER rule_language_insert BEFORE INSERT ON rule_language FOR EACH ROW
BEGIN
    DECLARE root_status VARBINARY(8) DEFAULT NULL;
    SELECT release_status INTO root_status FROM rule_release WHERE id = NEW.release_id FOR UPDATE;
    IF NEW.release_id <= 0 OR root_status IS NULL OR root_status <> _binary'DRAFT' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Rule language requires draft root';
    END IF;
END$$
CREATE TRIGGER rule_language_update BEFORE UPDATE ON rule_language FOR EACH ROW
BEGIN
    DECLARE root_status VARBINARY(8) DEFAULT NULL;
    IF NEW.release_id <> OLD.release_id OR NEW.language_key <> OLD.language_key THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable rule language identity';
    END IF;
    SELECT release_status INTO root_status FROM rule_release WHERE id = OLD.release_id FOR UPDATE;
    IF root_status IS NULL OR root_status <> _binary'DRAFT' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Rule language requires draft root';
    END IF;
END$$
CREATE TRIGGER rule_language_delete BEFORE DELETE ON rule_language FOR EACH ROW
BEGIN
    DECLARE root_status VARBINARY(8) DEFAULT NULL;
    SELECT release_status INTO root_status FROM rule_release WHERE id = OLD.release_id FOR UPDATE;
    IF root_status IS NULL OR root_status <> _binary'DRAFT' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Rule language requires draft root';
    END IF;
END$$
CREATE TRIGGER rule_installation_insert BEFORE INSERT ON rule_package_installation FOR EACH ROW
BEGIN
    DECLARE root_status VARBINARY(8) DEFAULT NULL;
    SELECT release_status INTO root_status FROM rule_release WHERE id = NEW.release_id FOR UPDATE;
    IF NEW.release_id <= 0 OR NEW.installation_revision <= 0
        OR root_status IS NULL OR root_status <> _binary'DRAFT' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Installation requires draft root and positive revision';
    END IF;
    SET NEW.installed_at = UTC_TIMESTAMP(6);
END$$
CREATE TRIGGER rule_installation_update BEFORE UPDATE ON rule_package_installation FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent installation fact';
END$$
CREATE TRIGGER rule_installation_delete BEFORE DELETE ON rule_package_installation FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent installation fact';
END$$
CREATE TRIGGER rule_partition_insert BEFORE INSERT ON rule_package_installation_partition FOR EACH ROW
BEGIN
    DECLARE root_status VARBINARY(8) DEFAULT NULL;
    SELECT release_status INTO root_status FROM rule_release WHERE id = NEW.release_id FOR UPDATE;
    IF NEW.release_id <= 0 OR NEW.installation_revision <= 0
        OR root_status IS NULL OR root_status <> _binary'DRAFT' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Partition requires draft root and positive revision';
    END IF;
END$$
CREATE TRIGGER rule_partition_update BEFORE UPDATE ON rule_package_installation_partition FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent installation partition';
END$$
CREATE TRIGGER rule_partition_delete BEFORE DELETE ON rule_package_installation_partition FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent installation partition';
END$$
CREATE TRIGGER rule_control_update BEFORE UPDATE ON rule_installation_control FOR EACH ROW
BEGIN
    IF NEW.control_id <> OLD.control_id OR NEW.protocol_version <> OLD.protocol_version THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable source control identity';
    END IF;
END$$
CREATE TRIGGER rule_control_delete BEFORE DELETE ON rule_installation_control FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent source control';
END$$
CREATE TRIGGER rule_schema_insert BEFORE INSERT ON rule_schema_meta FOR EACH ROW
BEGIN
    SET NEW.applied_at = UTC_TIMESTAMP(6);
END$$
CREATE TRIGGER rule_schema_update BEFORE UPDATE ON rule_schema_meta FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent rules migration history';
END$$
CREATE TRIGGER rule_schema_delete BEFORE DELETE ON rule_schema_meta FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Permanent rules migration history';
END$$
DELIMITER ;

INSERT INTO rule_installation_control (control_id, protocol_version, metadata_row_count, row_version)
VALUES (1, 1, 1, 0);
-- CHECKSUM-SCOPE-END

-- Execute only after all preceding statements and the offline postconditions succeed.
-- DDL implicitly commits: failure means maintenance stop, never blind replay or a forged ledger row.
INSERT INTO rule_schema_meta (schema_version, script_name, script_sha256)
SELECT 1, 'V001__rule-source-schema.sql',
    CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_rules'
        AND (SELECT COUNT(*) FROM information_schema.tables
            WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE') = 6
        AND (SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema = DATABASE()) = 18
        AND (SELECT COUNT(*) FROM rule_schema_meta) = 0
        AND (SELECT COUNT(*) FROM rule_release) = 0
        AND (SELECT COUNT(*) FROM rule_language) = 0
        AND (SELECT COUNT(*) FROM rule_package_installation) = 0
        AND (SELECT COUNT(*) FROM rule_package_installation_partition) = 0
        AND (SELECT COUNT(*) FROM rule_installation_control) = 1
        AND (SELECT COUNT(*) FROM rule_installation_control
            WHERE control_id = 1 AND protocol_version = 1 AND metadata_row_count = 1 AND row_version = 0) = 1
    THEN '003916bc758315351e46f177fec10eb70f2b1275a524d270c6eb89a602cd130e' ELSE NULL END;
