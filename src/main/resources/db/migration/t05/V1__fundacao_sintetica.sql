CREATE TABLE t05_app.probe (
    id bigint PRIMARY KEY,
    descricao varchar(80) NOT NULL
);

GRANT SELECT ON t05_app.probe TO t05_application;
