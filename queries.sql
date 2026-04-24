
SELECT version, count(*) as count
FROM module_requires
WHERE module = 'java.base'
GROUP BY version
ORDER BY count desc;

SELECT module, count(*) as count
FROM module_requires
WHERE NOT static
GROUP BY module
ORDER BY count desc;

SELECT module, version, count(*) as count
FROM module_requires
GROUP BY module, version
ORDER BY count desc;

SELECT COUNT(DISTINCT name)
FROM module;


SELECT module, COUNT(

               )
FROM module_requires mr
order by module;

SELECT count(DISTINCT(module)) as unfulfilled_requires
FROM module_requires
WHERE module_requires.module NOT IN (
    SELECT name
    FROM module
)
ORDER BY module;

SELECT sum(length(data))
FROM artifact;
SELECT COUNT (DISTINCT package)
FROM module_exports;

SELECT name, version, target_platform, length(artifact.data) as size
FROM module
         JOIN artifact ON artifact.sha256 = module.sha256
ORDER BY size desc;


SELECT module as missing_module, module.name as required_by, module_requires.module_id
FROM module_requires
         JOIN module ON module_requires.module_id = module.id
WHERE module_requires.module NOT IN (
    SELECT name
    FROM module
);

-- MISSING and not static
SELECT module as missing_module, module.name as required_by, module_requires.module_id
FROM module_requires
         JOIN module ON module_requires.module_id = module.id
WHERE module_requires.module NOT IN (
    SELECT name
    FROM module
) AND NOT module_requires.static
ORDER BY missing_module;


SELECT COUNT(*)
FROM module_requires
WHERE module_requires.module NOT IN (
    SELECT name
    FROM module
) AND NOT module_requires.static;

SELECT module as missing_module, COUNT(*) as required_by
FROM module_requires
WHERE module_requires.module NOT IN (
    SELECT name
    FROM module
)
GROUP BY module
ORDER BY required_by;

SELECT * FROM module WHERE name like 'dev.mccue.%';
