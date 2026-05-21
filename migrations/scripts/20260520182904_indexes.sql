-- // indexes
CREATE INDEX module_exports_module_id_idx
    ON repository.module_exports (module_id);
CREATE INDEX module_requires_module_id_idx
    ON repository.module_requires (module_id);
CREATE INDEX module_provides_module_id_idx
    ON repository.module_provides (module_id);
CREATE INDEX module_uses_module_id_idx
    ON repository.module_uses (module_id);
CREATE INDEX module_package_module_id_idx
    ON repository.module_package (module_id);
CREATE INDEX module_hash_module_id_idx
    ON repository.module_hash (module_id);


-- //@UNDO
-- SQL to undo the change goes here.

DROP INDEX module_exports_module_id_idx;
DROP INDEX module_requires_module_id_idx;
DROP INDEX module_provides_module_id_idx;
DROP INDEX module_uses_module_id_idx;
DROP INDEX module_package_module_id_idx;
DROP INDEX module_package_module_id_idx;
