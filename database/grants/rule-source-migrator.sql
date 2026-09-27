-- Separate administrator checkpoint; the dedicated account must already exist.
-- Schema-scoped CREATE also permits approved creation of this exact empty schema.
-- No DROP, global privileges, account management or GRANT OPTION.
-- TRIGGER definer must retain SELECT/UPDATE/INSERT needed by its guarded table operations.
-- Check SELECT @@GLOBAL.partial_revokes before applying this template.
-- With partial_revokes=OFF, schema-level underscores must be escaped as below.
-- With partial_revokes=ON, use the literal identifier `dnd_tool_rules` instead;
-- do not change the server setting or apply both forms.
GRANT CREATE, ALTER, INDEX, REFERENCES, TRIGGER, SELECT, INSERT, UPDATE, DELETE
    ON `dnd\_tool\_rules`.* TO 'dnd_tool_rules_migrator'@'127.0.0.1';
