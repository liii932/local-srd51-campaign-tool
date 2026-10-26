-- Read-only supplement to rule-source-schema.sql for the schema 2 ledger and typed author material.
-- Execute with maintenance visibility; do not infer absent trigger/privilege rows from a narrow reader.
SELECT control_id, protocol_version, metadata_row_count, installation_count, acceptance_count,
    raw_zip_byte_count, row_version FROM rule_schema2_control ORDER BY control_id LIMIT 2;
SELECT (SELECT COUNT(*) FROM rule_schema2_artifact) AS installed_count,
    (SELECT COUNT(*) FROM rule_schema2_acceptance) AS accepted_count,
    (SELECT COALESCE(SUM(raw_zip_byte_length), 0) FROM rule_schema2_artifact) AS total_zip_bytes,
    (SELECT COALESCE(SUM(expanded_byte_length), 0) FROM rule_schema2_artifact) AS total_expanded_bytes;
-- Counts expose missing/extra typed materials for offline exact-ZIP verification, not semantic acceptance.
SELECT CONVERT(a.module_key USING latin1) AS module_key, CONVERT(a.release_version USING latin1) AS release_version,
    (SELECT COUNT(*) FROM rule_schema2_character_source s WHERE s.artifact_module_key = a.module_key AND s.release_version = a.release_version) AS character_source_count,
    (SELECT COUNT(*) FROM rule_schema2_proficiency_grant g WHERE g.artifact_module_key = a.module_key AND g.release_version = a.release_version) AS proficiency_grant_count,
    (SELECT COUNT(*) FROM rule_schema2_character_change c WHERE c.artifact_module_key = a.module_key AND c.release_version = a.release_version) AS character_change_count,
    (SELECT COUNT(*) FROM rule_schema2_class_progression p WHERE p.artifact_module_key = a.module_key AND p.release_version = a.release_version) AS class_progression_count,
    (SELECT COUNT(*) FROM rule_schema2_resource r WHERE r.artifact_module_key = a.module_key AND r.release_version = a.release_version) AS resource_count,
    (SELECT COUNT(*) FROM rule_schema2_advancement_change c WHERE c.artifact_module_key = a.module_key AND c.release_version = a.release_version) AS advancement_change_count,
    (SELECT COUNT(*) FROM rule_schema2_subclass m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS subclass_count,
    (SELECT COUNT(*) FROM rule_schema2_feature m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS feature_count,
    (SELECT COUNT(*) FROM rule_schema2_feat m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS feat_count,
    (SELECT COUNT(*) FROM rule_schema2_proficiency m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS proficiency_count,
    (SELECT COUNT(*) FROM rule_schema2_proficiency_set m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS proficiency_set_count,
    (SELECT COUNT(*) FROM rule_schema2_class_level m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS class_level_count,
    (SELECT COUNT(*) FROM rule_schema2_proficiency_member m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS proficiency_member_count,
    (SELECT COUNT(*) FROM rule_schema2_growth_grant m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS growth_grant_count,
    (SELECT COUNT(*) FROM rule_schema2_ability_requirement m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS ability_requirement_count,
    (SELECT COUNT(*) FROM rule_schema2_rest m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS rest_count,
    (SELECT COUNT(*) FROM rule_schema2_check m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS check_count,
    (SELECT COUNT(*) FROM rule_schema2_equipment_item m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS equipment_item_count,
    (SELECT COUNT(*) FROM rule_schema2_health m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS health_count,
    (SELECT COUNT(*) FROM rule_schema2_vital_effect m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS vital_effect_count,
    (SELECT COUNT(*) FROM rule_schema2_execution_change m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS execution_change_count,
    (SELECT COUNT(*) FROM rule_schema2_combat m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS combat_count,
    (SELECT COUNT(*) FROM rule_schema2_weapon_attack m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS weapon_attack_count,
    (SELECT COUNT(*) FROM rule_schema2_movement m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS movement_count,
    (SELECT COUNT(*) FROM rule_schema2_combat_change m WHERE m.artifact_module_key = a.module_key AND m.release_version = a.release_version) AS combat_change_count
FROM rule_schema2_artifact a ORDER BY a.installation_sequence LIMIT 17;
SELECT CONVERT(module_key USING latin1) AS module_key, CONVERT(release_version USING latin1) AS release_version,
    installation_sequence, HEX(source_operation_id) AS source_operation_id,
    operation_fingerprint_version, CONVERT(operation_digest_sha256 USING latin1) AS operation_digest_sha256,
    author_schema_version, artifact_protocol_version, CONVERT(canonical_profile USING latin1) AS canonical_profile,
    CONVERT(package_role USING latin1) AS package_role, CONVERT(raw_zip_sha256 USING latin1) AS raw_zip_sha256,
    CONVERT(logical_sha256 USING latin1) AS logical_sha256, raw_zip_byte_length, expanded_byte_length,
    OCTET_LENGTH(original_zip) AS actual_zip_bytes,
    CAST(SHA2(original_zip, 256) AS BINARY) = raw_zip_sha256 AS raw_zip_digest_matches, installed_at
FROM rule_schema2_artifact ORDER BY installation_sequence LIMIT 17;
SELECT CONVERT(a.module_key USING latin1) AS module_key, CONVERT(a.release_version USING latin1) AS release_version,
    a.acceptance_sequence, HEX(a.acceptance_operation_id) AS acceptance_operation_id,
    CONVERT(a.operation_digest_sha256 USING latin1) AS operation_digest_sha256,
    HEX(a.installed_operation_id) AS installed_operation_id,
    CONVERT(a.review_sha256 USING latin1) AS review_sha256, a.accepted_at,
    i.source_operation_id IS NOT NULL
        AND a.installed_operation_digest_sha256 = i.operation_digest_sha256
        AND a.installation_sequence = i.installation_sequence
        AND a.author_schema_version = i.author_schema_version
        AND a.artifact_protocol_version = i.artifact_protocol_version
        AND a.canonical_profile = i.canonical_profile AND a.package_role = i.package_role
        AND a.raw_zip_sha256 = i.raw_zip_sha256 AND a.logical_sha256 = i.logical_sha256 AS exact_binding
FROM rule_schema2_acceptance a
LEFT JOIN rule_schema2_artifact i ON i.module_key = a.module_key AND i.release_version = a.release_version
    AND i.source_operation_id = a.installed_operation_id
ORDER BY a.acceptance_sequence LIMIT 17;
-- Include foreign incoming references from every visible schema, not just source-owned constraints.
SELECT table_schema, table_name, constraint_name, column_name, referenced_table_schema,
    referenced_table_name, referenced_column_name
FROM information_schema.key_column_usage WHERE referenced_table_schema = DATABASE()
ORDER BY table_schema, table_name, constraint_name, ordinal_position;

