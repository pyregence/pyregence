DROP DATABASE IF EXISTS pyregence;
DROP ROLE IF EXISTS pyregence;
CREATE ROLE pyregence WITH LOGIN CREATEDB PASSWORD 'pyregence';
CREATE DATABASE pyregence WITH OWNER pyregence;
\c pyregence
CREATE EXTENSION pgcrypto;
