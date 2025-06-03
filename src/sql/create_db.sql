DROP DATABASE IF EXISTS :database;
DROP ROLE IF EXISTS :user;
CREATE ROLE :user WITH LOGIN CREATEDB PASSWORD :'password';
GRANT :user TO postgres;
SET ROLE :user;
CREATE DATABASE :database WITH OWNER :user;
\c :database
CREATE EXTENSION pgcrypto;
