SELECT 'CREATE DATABASE entrego_auth_test'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'entrego_auth_test')\gexec
