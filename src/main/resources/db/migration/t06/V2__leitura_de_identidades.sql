-- A autenticação usa papel distinto do bootstrap e do proprietário das migrações.
GRANT USAGE ON SCHEMA t06_identity TO t06_identity_reader;
GRANT SELECT ON t06_identity.identity, t06_identity.capability TO t06_identity_reader;
