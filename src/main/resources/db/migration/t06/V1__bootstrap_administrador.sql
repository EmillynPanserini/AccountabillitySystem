CREATE SCHEMA t06_identity;

CREATE TABLE t06_identity.identity (
    id uuid PRIMARY KEY,
    login varchar(200) NOT NULL UNIQUE CHECK (login = lower(btrim(login)) AND login <> ''),
    password_hash varchar(255) NOT NULL CHECK (password_hash LIKE '$argon2id$v=19$m=19456,t=2,p=1$%'),
    initial_admin boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX one_initial_administrator ON t06_identity.identity (initial_admin) WHERE initial_admin;

CREATE TABLE t06_identity.capability (
    identity_id uuid NOT NULL REFERENCES t06_identity.identity(id),
    name varchar(40) NOT NULL CHECK (name IN ('ADMINISTRAR_IDENTIDADES', 'CRIAR_EMPRESA')),
    PRIMARY KEY (identity_id, name)
);

CREATE TABLE t06_identity.bootstrap (
    singleton boolean PRIMARY KEY CHECK (singleton),
    administrator_id uuid UNIQUE REFERENCES t06_identity.identity(id),
    closed_at timestamptz,
    CHECK ((administrator_id IS NULL) = (closed_at IS NULL))
);
INSERT INTO t06_identity.bootstrap (singleton) VALUES (true);

CREATE FUNCTION t06_identity.prevent_bootstrap_reopening() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.closed_at IS NOT NULL THEN
        RAISE EXCEPTION 'Bootstrap definitivamente encerrado';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER bootstrap_closed BEFORE UPDATE ON t06_identity.bootstrap
    FOR EACH ROW EXECUTE FUNCTION t06_identity.prevent_bootstrap_reopening();

CREATE TABLE t06_identity.audit (
    event varchar(40) PRIMARY KEY DEFAULT 'BOOTSTRAP_ADMIN' CHECK (event = 'BOOTSTRAP_ADMIN'),
    administrator_id uuid NOT NULL REFERENCES t06_identity.identity(id),
    operator varchar(200) NOT NULL CHECK (btrim(operator) <> ''),
    recipient varchar(200) NOT NULL CHECK (btrim(recipient) <> ''),
    verification_reference varchar(300) NOT NULL CHECK (btrim(verification_reference) <> ''),
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

GRANT USAGE ON SCHEMA t06_identity TO t06_bootstrap;
GRANT SELECT, INSERT ON t06_identity.identity, t06_identity.capability, t06_identity.audit TO t06_bootstrap;
GRANT SELECT ON t06_identity.bootstrap TO t06_bootstrap;
GRANT UPDATE (administrator_id, closed_at) ON t06_identity.bootstrap TO t06_bootstrap;
