-- Crea un usuario y una base de datos por microservicio (base por servicio, ADR-04).
-- Lo ejecuta psql: en Docker al crear el volumen (docker-entrypoint-initdb.d) y en el CI.
-- Es idempotente: puede correrse varias veces sin error.
\set ON_ERROR_STOP on

-- === Usuarios =======================================================
DO $$
DECLARE
  svc text;
BEGIN
  FOREACH svc IN ARRAY ARRAY['auth', 'partners', 'documents', 'alerts'] LOOP
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = svc) THEN
      EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', svc, svc);
    END IF;
  END LOOP;
END
$$;

-- === Bases (CREATE DATABASE no admite transaccion: se usa \gexec) ====
SELECT 'CREATE DATABASE auth OWNER auth'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auth') \gexec

SELECT 'CREATE DATABASE partners OWNER partners'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'partners') \gexec

SELECT 'CREATE DATABASE documents OWNER documents'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'documents') \gexec

SELECT 'CREATE DATABASE alerts OWNER alerts'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'alerts') \gexec

-- === Extensiones ====================================================
\connect auth
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\connect partners
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\connect documents
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\connect alerts
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
