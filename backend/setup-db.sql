-- PulseRelay Database Setup Script
-- Run this as PostgreSQL superuser (postgres)

-- Create database if it doesn't exist
CREATE DATABASE IF NOT EXISTS pulserelay;

-- Alternative syntax for some PostgreSQL versions:
-- CREATE DATABASE pulserelay;

-- Connect to the database
\c pulserelay

-- Verify connection
SELECT 'PulseRelay database created successfully!' AS status;
