SELECT (
    current_database() = :'database'
    AND session_user = 'postgres'
    AND current_user = 'postgres'
    AND current_setting('server_version_num')::integer / 10000 = 17
    AND current_setting('port') = '5432'
    AND NOT EXISTS (SELECT FROM pg_namespace WHERE nspname IN ('t05_control', 't05_app'))
) AS bootstrap_allowed \gset
\if :bootstrap_allowed
\else
SELECT 1 / 0;
\endif

SET LOCAL log_statement = 'none';
SET LOCAL log_min_error_statement = 'panic';

REVOKE ALL ON DATABASE :"database" FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;

CREATE ROLE t05_migrator LOGIN PASSWORD :'migrator_password';
CREATE ROLE t05_application LOGIN PASSWORD :'application_password';
GRANT CONNECT ON DATABASE :"database" TO t05_migrator, t05_application;

CREATE SCHEMA t05_app AUTHORIZATION t05_migrator;
GRANT USAGE ON SCHEMA t05_app TO t05_application;

CREATE SCHEMA t05_control;
CREATE TABLE t05_control.run_identity (marker text PRIMARY KEY);
INSERT INTO t05_control.run_identity VALUES (:'run_marker');
GRANT USAGE ON SCHEMA t05_control TO t05_migrator, t05_application;
GRANT SELECT ON t05_control.run_identity TO t05_migrator, t05_application;

