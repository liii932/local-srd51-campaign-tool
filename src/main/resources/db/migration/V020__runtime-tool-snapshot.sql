-- Add immutable tools to fresh runtime PARTITION snapshots; never upgrade existing snapshots.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE v020_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO v020_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_se'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0
        OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM schema_meta) = 19
    AND (SELECT COUNT(*) FROM schema_meta WHERE
        (schema_version = 1 AND CAST(script_name AS BINARY) = _binary'V001__stage1_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'29ced895929c5a083a5f8703ecdb7946366d35d38523ed2aa4ed382ab2d9c644')
        OR         (schema_version = 2 AND CAST(script_name AS BINARY) = _binary'V002__stage2_module_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'55d346046c5544ab9a9e2800bcc6b9b8da7b0504c22788b750554e5ad664a7f8')
        OR         (schema_version = 3 AND CAST(script_name AS BINARY) = _binary'V003__builtin_module_draft.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'2ac1df0d7e7cdee42946c69debefb00ed4aceaa3b50423a14462865d8ca01225')
        OR         (schema_version = 4 AND CAST(script_name AS BINARY) = _binary'V004__release_builtin_module.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'5ff9773d8abef2e56ae46aee700196a42908915069253386a45252ba390a021f')
        OR         (schema_version = 5 AND CAST(script_name AS BINARY) = _binary'V005__stage2_character_event_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'd4741ddae56adbb574c0018c75195ed71bf248219118a0e4771e17b16d3838d6')
        OR         (schema_version = 6 AND CAST(script_name AS BINARY) = _binary'V006__stage2_character_field_value_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'f7eae4d3e5b1ea06cab941369e0081da3f10b7b8ddd608538a3acce7b32cfb7b')
        OR         (schema_version = 7 AND CAST(script_name AS BINARY) = _binary'V007__character_creation_idempotency.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'01f7bbc29a15e3708e48e8b9b1bac17096760a014a5829a8ae79fc27d87249ef')
        OR         (schema_version = 8 AND CAST(script_name AS BINARY) = _binary'V008__stage2_simple_item_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'369d189dad623c1e81312637fe97775356283378258903ccec4db735014c1709')
        OR         (schema_version = 9 AND CAST(script_name AS BINARY) = _binary'V009__stage3_check_execution_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'e1c0311b19706726b7accdd1016706c00f4191a7bce177ebd0e3e7d371630a6c')
        OR         (schema_version = 10 AND CAST(script_name AS BINARY) = _binary'V010__stage3_node_encounter_schema.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'b4fa1d7085cec782670b8b40f39bf3c7a9deb2316a4ac9e8ed1ac89610a31e87')
        OR         (schema_version = 11 AND CAST(script_name AS BINARY) = _binary'V011__complete_character_catalog_draft.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'0575e6c00e0cf4d4ce15ba2c2281f6cbf637d9602ccc4dce263cfd50a9189beb')
        OR         (schema_version = 12 AND CAST(script_name AS BINARY) = _binary'V012__level_one_character_creation.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'56f84bdd6763427d92b4cad30323707676aa4beb7a2bbdeffef8b128e6e3d0e2')
        OR         (schema_version = 13 AND CAST(script_name AS BINARY) = _binary'V013__level_advancement_hit_dice.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'c2824fae928b37cd3caf86ea98307de1ddd5e3e31d22e6d303c705745ffdb74d')
        OR         (schema_version = 14 AND CAST(script_name AS BINARY) = _binary'V014__class_feature_lifecycle.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'e60b947c2837f4f36dce4c05f32cd81a4209a6725785bf75b24906b6d3015361')
        OR         (schema_version = 15 AND CAST(script_name AS BINARY) = _binary'V015__multiclass_asi_feat_draft.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'550aa953ef21c49766f4f61385f22c7eea3f9a989f189efb11579e191784ffdd')
        OR         (schema_version = 16 AND CAST(script_name AS BINARY) = _binary'V016__starting_proficiency_baseline_draft.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'0d25ad4506ea68e99d47bb665a15e6e84a069ff6b7f11261652658b12c028dda')
        OR         (schema_version = 17 AND CAST(script_name AS BINARY) = _binary'V017__character_archive_v2_origin.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'6985a479484233a9dc478f09be4f64eea752f557c300d7d22842ff4ccc68c4a0')
        OR         (schema_version = 18 AND CAST(script_name AS BINARY) = _binary'V018__multiclass_spell_slot_foundation.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'8b685a942e2784584ea9106594a8d33e9fdcbd840020daf770c15f9ff38f586a')
        OR (schema_version = 19 AND CAST(script_name AS BINARY) = _binary'V019__runtime-language-snapshot.sql'
            AND CAST(script_sha256 AS BINARY) = _binary'6df15d978dc79d0298c7cf4fc470f75633b58199795b4a3fae4f1a1bf44f3173')) = 19
    AND NOT EXISTS (SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name IN
            ('runtime_rule_tool'))
    AND NOT EXISTS (SELECT 1 FROM information_schema.triggers
        WHERE trigger_schema = DATABASE() AND trigger_name IN
            ('runtime_tool_update'))
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE v020_preflight;

CREATE TABLE runtime_rule_tool (
    snapshot_id VARBINARY(16) NOT NULL,
    tool_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    category VARBINARY(18) NOT NULL,
    source_page SMALLINT NOT NULL,
    sort_order SMALLINT NOT NULL,
    PRIMARY KEY (snapshot_id, tool_key),
    UNIQUE KEY uq_runtime_tool_sort (snapshot_id, sort_order),
    CONSTRAINT fk_runtime_tool_snapshot FOREIGN KEY (snapshot_id)
        REFERENCES runtime_rule_snapshot (snapshot_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_runtime_tool_snapshot CHECK (OCTET_LENGTH(snapshot_id) = 16
        AND (ORD(SUBSTRING(snapshot_id, 7, 1)) & 240) = 64
        AND (ORD(SUBSTRING(snapshot_id, 9, 1)) & 192) = 128),
    CONSTRAINT ck_runtime_tool_category CHECK (
        (category = _binary'ARTISAN' AND tool_key IN (_binary'tool.alchemist_supplies', _binary'tool.brewer_supplies', _binary'tool.calligrapher_supplies', _binary'tool.carpenter_tools', _binary'tool.cartographer_tools', _binary'tool.cobbler_tools', _binary'tool.cook_utensils', _binary'tool.glassblower_tools', _binary'tool.jeweler_tools', _binary'tool.leatherworker_tools', _binary'tool.mason_tools', _binary'tool.painter_supplies', _binary'tool.potter_tools', _binary'tool.smith_tools', _binary'tool.tinker_tools', _binary'tool.weaver_tools', _binary'tool.woodcarver_tools'))
        OR (category = _binary'GAMING_SET' AND tool_key IN (_binary'tool.dice_set', _binary'tool.playing_card_set'))
        OR (category = _binary'KIT' AND tool_key IN (_binary'tool.disguise_kit', _binary'tool.forgery_kit', _binary'tool.herbalism_kit', _binary'tool.poisoner_kit', _binary'tool.thieves_tools'))
        OR (category = _binary'MUSICAL_INSTRUMENT' AND tool_key IN (_binary'tool.bagpipes', _binary'tool.drum', _binary'tool.dulcimer', _binary'tool.flute', _binary'tool.horn', _binary'tool.lute', _binary'tool.lyre', _binary'tool.pan_flute', _binary'tool.shawm', _binary'tool.viol'))
        OR (category = _binary'NAVIGATION' AND tool_key IN (_binary'tool.navigator_tools'))
        OR (category = _binary'VEHICLE' AND tool_key IN (_binary'tool.vehicles_land', _binary'tool.vehicles_water'))),
    CONSTRAINT ck_runtime_tool_page CHECK (source_page BETWEEN 3 AND 74),
    CONSTRAINT ck_runtime_tool_sort CHECK (sort_order BETWEEN 1 AND 37),
    CONSTRAINT ck_runtime_tool_name CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120
        AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]')
        AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_runtime_tool_description CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 1000
        AND NOT REGEXP_LIKE(description, '[[:cntrl:]]')
        AND REGEXP_LIKE(description, '[^[:space:]]'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DELIMITER $$
CREATE TRIGGER runtime_tool_update BEFORE UPDATE ON runtime_rule_tool FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable runtime tool';
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

-- Record only after the complete script and separate object audit succeed.
INSERT INTO schema_meta (schema_version, script_name, script_sha256, description)
VALUES (20, 'V020__runtime-tool-snapshot.sql', 'cc8cf59276982af13f25640abe64ec2cac359e5710d31d7bfc15d3c2fce0f5a1', 'Runtime DRAFT tool catalog partition');
