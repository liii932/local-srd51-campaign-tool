-- Tools extend the DRAFT source catalog. Apply once; stop on any error.
-- Authorize isolated replay and maintenance separately. DDL is not atomic.
-- CHECKSUM-SCOPE-BEGIN
CREATE TEMPORARY TABLE rules_v002_preflight (ok TINYINT NOT NULL CHECK (ok = 1));
INSERT INTO rules_v002_preflight (ok)
SELECT CASE WHEN CAST(DATABASE() AS BINARY) = _binary'dnd_tool_rules'
    AND (FIND_IN_SET('STRICT_TRANS_TABLES', @@SESSION.sql_mode) > 0
        OR FIND_IN_SET('STRICT_ALL_TABLES', @@SESSION.sql_mode) > 0)
    AND (SELECT COUNT(*) FROM rule_schema_meta) = 1
    AND (SELECT COUNT(*) FROM rule_schema_meta WHERE schema_version = 1
        AND script_name = _binary'V001__rule-source-schema.sql'
        AND script_sha256 = _binary'003916bc758315351e46f177fec10eb70f2b1275a524d270c6eb89a602cd130e') = 1
    THEN 1 ELSE NULL END;
DROP TEMPORARY TABLE rules_v002_preflight;
ALTER TABLE rule_package_installation_partition DROP CHECK ck_rule_partition_key,
    ADD CONSTRAINT ck_rule_partition_key CHECK (partition_key IN (_binary'character.language', _binary'character.tool'));

CREATE TABLE rule_tool (
    release_id BIGINT NOT NULL,
    tool_key VARBINARY(128) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    category VARBINARY(18) NOT NULL,
    source_page SMALLINT NOT NULL,
    sort_order SMALLINT NOT NULL,
    PRIMARY KEY (release_id, tool_key),
    UNIQUE KEY uq_rule_tool_sort (release_id, sort_order),
    CONSTRAINT fk_rule_tool_release FOREIGN KEY (release_id) REFERENCES rule_release (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    CONSTRAINT ck_rule_tool_category CHECK (
        (category = _binary'ARTISAN' AND tool_key IN (_binary'tool.alchemist_supplies', _binary'tool.brewer_supplies', _binary'tool.calligrapher_supplies', _binary'tool.carpenter_tools', _binary'tool.cartographer_tools', _binary'tool.cobbler_tools', _binary'tool.cook_utensils', _binary'tool.glassblower_tools', _binary'tool.jeweler_tools', _binary'tool.leatherworker_tools', _binary'tool.mason_tools', _binary'tool.painter_supplies', _binary'tool.potter_tools', _binary'tool.smith_tools', _binary'tool.tinker_tools', _binary'tool.weaver_tools', _binary'tool.woodcarver_tools'))
        OR (category = _binary'GAMING_SET' AND tool_key IN (_binary'tool.dice_set', _binary'tool.playing_card_set'))
        OR (category = _binary'KIT' AND tool_key IN (_binary'tool.disguise_kit', _binary'tool.forgery_kit', _binary'tool.herbalism_kit', _binary'tool.poisoner_kit', _binary'tool.thieves_tools'))
        OR (category = _binary'MUSICAL_INSTRUMENT' AND tool_key IN (_binary'tool.bagpipes', _binary'tool.drum', _binary'tool.dulcimer', _binary'tool.flute', _binary'tool.horn', _binary'tool.lute', _binary'tool.lyre', _binary'tool.pan_flute', _binary'tool.shawm', _binary'tool.viol'))
        OR (category = _binary'NAVIGATION' AND tool_key IN (_binary'tool.navigator_tools'))
        OR (category = _binary'VEHICLE' AND tool_key IN (_binary'tool.vehicles_land', _binary'tool.vehicles_water'))),
    CONSTRAINT ck_rule_tool_page CHECK (source_page BETWEEN 3 AND 74),
    CONSTRAINT ck_rule_tool_sort CHECK (sort_order BETWEEN 1 AND 37),
    CONSTRAINT ck_rule_tool_name CHECK (CHAR_LENGTH(display_name) BETWEEN 1 AND 120
        AND NOT REGEXP_LIKE(display_name, '[[:cntrl:]]')
        AND REGEXP_LIKE(display_name, '[^[:space:]]')),
    CONSTRAINT ck_rule_tool_description CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 1000
        AND NOT REGEXP_LIKE(description, '[[:cntrl:]]')
        AND REGEXP_LIKE(description, '[^[:space:]]'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

DELIMITER $$
CREATE TRIGGER rule_tool_insert BEFORE INSERT ON rule_tool FOR EACH ROW
BEGIN
    DECLARE root_status VARBINARY(8) DEFAULT NULL;
    SELECT release_status INTO root_status FROM rule_release WHERE id = NEW.release_id FOR UPDATE;
    IF NEW.release_id <= 0 OR root_status IS NULL OR root_status <> _binary'DRAFT' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Rule tool requires draft root';
    END IF;
END$$
CREATE TRIGGER rule_tool_update BEFORE UPDATE ON rule_tool FOR EACH ROW
BEGIN
    DECLARE root_status VARBINARY(8) DEFAULT NULL;
    IF NEW.release_id <> OLD.release_id OR NEW.tool_key <> OLD.tool_key THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Immutable rule tool identity';
    END IF;
    SELECT release_status INTO root_status FROM rule_release WHERE id = OLD.release_id FOR UPDATE;
    IF root_status IS NULL OR root_status <> _binary'DRAFT' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Rule tool requires draft root';
    END IF;
END$$
CREATE TRIGGER rule_tool_delete BEFORE DELETE ON rule_tool FOR EACH ROW
BEGIN
    DECLARE root_status VARBINARY(8) DEFAULT NULL;
    SELECT release_status INTO root_status FROM rule_release WHERE id = OLD.release_id FOR UPDATE;
    IF root_status IS NULL OR root_status <> _binary'DRAFT' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Rule tool requires draft root';
    END IF;
END$$
DELIMITER ;
-- CHECKSUM-SCOPE-END

-- Record only after the complete script and separate object audit succeed.
INSERT INTO rule_schema_meta (schema_version, script_name, script_sha256)
VALUES (2, 'V002__tool-catalog.sql', '2a5a8a67ebef60754ff9ca572d651878558ccc56ed2bfd311ce811ef42082c40');
