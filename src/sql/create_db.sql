DROP DATABASE IF EXISTS pyregence;
DROP ROLE IF EXISTS pyregence;
CREATE ROLE pyregence WITH LOGIN CREATEDB PASSWORD 'pyregence';
GRANT pyregence TO postgres;
SET ROLE pyregence;
CREATE DATABASE pyregence WITH OWNER pyregence;
\c pyregence
CREATE EXTENSION pgcrypto;
