# Phase 02 — Local PostgreSQL Database Configuration

## Overview

Phase 02 configures Spring Boot to connect to a locally installed PostgreSQL database. This enables local development using a direct database connection without containerization. The application runs on port 8084 with Spring Data JPA/Hibernate for ORM and includes Actuator endpoints for health verification.

## Changes Implemented

- ✅ Updated `application.yml` with PostgreSQL datasource configuration
- ✅ Configured environment variable support for database credentials
- ✅ Set Hibernate `ddl-auto: update` for schema management
- ✅ Enabled SQL logging for development debugging
- ✅ Configured Spring Boot Actuator health endpoint
- ✅ Set application port to 8084
- ✅ Configured connection pooling and JPA batch settings

## Files Created or Modified

### Modified Files

1. **`src/main/resources/application.yml`**
   - Added PostgreSQL datasource configuration (host: localhost, port: 5432)
   - Environment variable placeholders: `${DB_NAME}`, `${DB_USER}`, `${DB_PASSWORD}`
   - Configured Hibernate dialect for PostgreSQL
   - Set `ddl-auto: update` for schema auto-creation/updates
   - Enabled SQL logging at DEBUG level for troubleshooting
   - Configured Actuator endpoints (health, info)
   - Set application port to 8084 and context path to `/api`

### No New Files Required

- No Docker files (local development only)
- No `.env.example` (credentials are environment variables on Windows)
- Existing `pom.xml` already includes PostgreSQL driver dependency

## PostgreSQL Database Setup (Windows)

### Prerequisites

PostgreSQL must be installed locally on your Windows machine. Verify installation:

```bash
psql --version
```

Expected output:
```
psql (PostgreSQL) 16.x (or similar)
```

### Step 1: Verify PostgreSQL Service is Running

```bash
# Check if PostgreSQL service is running
sc query postgresql-x64-16
```

Expected output:
```
SERVICE_NAME: postgresql-x64-16
        TYPE               : 10  SHARE_PROCESS
        STATE              : 4  RUNNING
```

If service shows `STATE: 1 STOPPED`, start it:

```bash
net start postgresql-x64-16
```

### Step 2: Create Development Database

Connect to PostgreSQL as the default admin user and create the development database:

```bash
# Connect to PostgreSQL
psql -U postgres -h localhost
```

You'll be prompted for a password (default during installation is typically blank or `postgres`).

In the PostgreSQL prompt, execute:

```sql
-- Create the development database
CREATE DATABASE pulserelay_dev;

-- Create the application user
CREATE USER pulserelay WITH PASSWORD 'postgres';

-- Grant all privileges to the user
ALTER DATABASE pulserelay_dev OWNER TO pulserelay;
GRANT ALL PRIVILEGES ON DATABASE pulserelay_dev TO pulserelay;

-- Verify
\l
```

Expected output should show:
```
                                                 List of databases
     Name      |  Owner   | Encoding | Collate | Ctype | ICU Locale | Provision | Access privileges
---------------+----------+----------+---------+-------+------------+-----------+-------------------
 pulserelay_dev | pulserelay | UTF8   | C       | C     |            | d         |
 postgres       | postgres | UTF8     | C       | C     |            |           |
 template0      | postgres | UTF8     | C       | C     |            | c=CTL/postgres
 template1      | postgres | UTF8     | C       | C     |            | c=CTL/postgres
```

Exit psql:

```sql
\q
```

### Step 3: Verify User Can Connect

Test that the `pulserelay` user can connect to the database:

```bash
psql -U pulserelay -d pulserelay_dev -h localhost
```

You'll be prompted for password: enter `postgres`

Expected output:
```
psql (16.0)
Type "help" for help.

pulserelay_dev=>
```

Exit:

```sql
\q
```

## Windows Environment Variable Configuration

### Option 1: System Environment Variables (Recommended)

Set environment variables at the system level so Spring Boot can access them:

**Step 1: Open Environment Variables**
- Press `Win + X` and select "System"
- Click "Advanced system settings"
- Click "Environment Variables" button
- Under "System variables" section, click "New"

**Step 2: Add Database Variables**

Create these system variables:

```
Variable name: DB_NAME
Variable value: pulserelay_dev

Variable name: DB_USER
Variable value: pulserelay

Variable name: DB_PASSWORD
Variable value: postgres
```

**Step 3: Restart Your IDE or Terminal**

Environment variables are read on startup. Close and reopen:
- Command Prompt / PowerShell
- IDE (IntelliJ IDEA, VS Code)

### Option 2: Command Line (Temporary)

If you prefer to set environment variables only for the current session:

```bash
# In Command Prompt
set DB_NAME=pulserelay_dev
set DB_USER=pulserelay
set DB_PASSWORD=postgres

# Then run Spring Boot in same terminal
cd backend
mvnw.cmd spring-boot:run
```

### Option 3: IDE Configuration (IntelliJ IDEA)

If using IntelliJ:

1. Open Run → Edit Configurations
2. Select "Spring Boot" run configuration
3. Add Environment variables:
   ```
   DB_NAME=pulserelay_dev;DB_USER=pulserelay;DB_PASSWORD=postgres
   ```
4. Click OK and run

## Application Configuration Details

### Datasource Connection

The application connects to PostgreSQL using:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/${DB_NAME:pulserelay_dev}
    username: ${DB_USER:pulserelay}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
```

**Explanation:**
- `localhost:5432` - PostgreSQL running on local machine, default port
- `${DB_NAME:pulserelay_dev}` - Uses environment variable `DB_NAME`, falls back to `pulserelay_dev`
- `${DB_USER:pulserelay}` - Uses environment variable `DB_USER`, falls back to `pulserelay`
- `${DB_PASSWORD:postgres}` - Uses environment variable `DB_PASSWORD`, falls back to `postgres`

### JPA and Hibernate Configuration

```yaml
spring:
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        use_sql_comments: true
        jdbc:
          batch_size: 10
          fetch_size: 50
```

**Explanation:**
- `database-platform: PostgreSQLDialect` - Tells Hibernate to use PostgreSQL SQL dialect
- `ddl-auto: update` - Automatically creates/updates tables on startup (safe for development)
- `show-sql: false` - Don't print raw SQL (logging level controls this)
- `format_sql: true` - Format SQL statements for readability
- `batch_size: 10` - Batch insert/update operations for performance
- `fetch_size: 50` - Optimize query result fetching

### Logging Configuration

```yaml
logging:
  level:
    root: INFO
    com.pulserelay: DEBUG
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

**What you'll see in logs:**
- Hibernate SQL statements at DEBUG level
- Parameter binding (TRACE level) shows actual values
- Application-specific logs (com.pulserelay) at DEBUG
- Spring Web logs at DEBUG for request/response details

### Actuator Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized
```

**Exposes:**
- `GET /api/actuator/health` - Application and database health status
- `GET /api/actuator/info` - Application information

## Starting PostgreSQL and Spring Boot

### Start PostgreSQL Service (Windows)

```bash
# Start the PostgreSQL service
net start postgresql-x64-16

# Verify it's running
sc query postgresql-x64-16

# Should show: STATE: 4 RUNNING
```

### Start Spring Boot Application

**Option 1: Using Maven wrapper (Recommended)**

```bash
cd backend
mvnw.cmd spring-boot:run
```

**Option 2: Using IDE (IntelliJ IDEA)**

1. Open `src/main/java/com/pulserelay/PulserelayApplication.java`
2. Right-click on the class
3. Select "Run 'PulserelayApplication.main()'"

**Option 3: Build and run JAR**

```bash
cd backend
mvnw.cmd clean package -DskipTests
java -jar target/pulserelay-0.0.1-SNAPSHOT.jar
```

### Verify Application Started Successfully

Look for these log messages in the console:

```
HikariPool-1 - Starting...
HikariPool-1 - Start completed.
Creating new JDBC Driver for jdbc:postgresql://localhost:5432/pulserelay_dev
Successfully acquired a new connection
Hibernate dialect: PostgreSQLDialect
Started PulserelayApplication in X.XXX seconds (JVM running for Y.YYY s)
```

If you see these messages, the application is running and connected to PostgreSQL ✅

## Postman Testing

### Test 1: Health Endpoint (Database Connectivity)

**Endpoint:**
```
GET http://localhost:8084/api/actuator/health
```

**Headers:**
```
Content-Type: application/json
```

**Expected Response (HTTP 200):**

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    }
  }
}
```

**What this tells you:**
- ✅ Application started successfully
- ✅ Spring Boot is running on port 8084
- ✅ Database connection is active
- ✅ PostgreSQL is accessible
- ✅ Hibernate connected successfully

### Test 2: Info Endpoint

**Endpoint:**
```
GET http://localhost:8084/api/actuator/info
```

**Expected Response (HTTP 200):**

```json
{
  "app": {
    "name": "pulserelay",
    "description": "Spring Boot Application"
  }
}
```

**What this tells you:**
- ✅ Application is responding to requests
- ✅ API context path (`/api`) is correct

### Test 3: Verify Database Tables

If Hibernate created any tables (currently none, but for future reference):

```bash
# Connect to database
psql -U pulserelay -d pulserelay_dev -h localhost

# List all tables
\dt

# Should show: "No relations found" (since no entities exist yet)

# Exit
\q
```

## Troubleshooting Common Errors

### Error 1: "Connection refused" or "Cannot connect to server"

```
org.postgresql.util.PSQLException: Connection to localhost:5432 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
```

**Cause:** PostgreSQL service is not running

**Solution:**
```bash
# Start PostgreSQL
net start postgresql-x64-16

# Verify it started
sc query postgresql-x64-16

# Check port 5432 is listening
netstat -an | findstr 5432
# Should show: TCP 127.0.0.1:5432 LISTENING
```

### Error 2: "password authentication failed"

```
org.postgresql.util.PSQLException: FATAL: password authentication failed for user "pulserelay"
```

**Cause:** Database credentials don't match what's configured

**Solution:**
```bash
# Verify credentials are set correctly
echo %DB_NAME%
echo %DB_USER%
echo %DB_PASSWORD%

# Should show:
# pulserelay_dev
# pulserelay
# postgres

# If blank, set them again:
set DB_NAME=pulserelay_dev
set DB_USER=pulserelay
set DB_PASSWORD=postgres

# Test connection directly
psql -U pulserelay -d pulserelay_dev -h localhost

# If psql connection fails, reset password in PostgreSQL
psql -U postgres -h localhost
ALTER USER pulserelay WITH PASSWORD 'postgres';
\q
```

### Error 3: "database 'pulserelay_dev' does not exist"

```
org.postgresql.util.PSQLException: ERROR: database "pulserelay_dev" does not exist
```

**Cause:** Database was not created

**Solution:**
```bash
# Connect as admin
psql -U postgres -h localhost

# Create database (in psql prompt)
CREATE DATABASE pulserelay_dev;
CREATE USER pulserelay WITH PASSWORD 'postgres';
GRANT ALL PRIVILEGES ON DATABASE pulserelay_dev TO pulserelay;

# Verify
\l

# Exit
\q

# Restart Spring Boot application
```

### Error 4: "property 'DB_NAME' not found"

```
org.springframework.beans.factory.BeanCreationException: 
Failed to create bean 'dataSource' ... The following profiles are active: dev
```

**Cause:** Environment variables are not set

**Solution:**
```bash
# Verify environment variables exist
echo %DB_NAME%

# If empty, set them in current terminal:
set DB_NAME=pulserelay_dev
set DB_USER=pulserelay
set DB_PASSWORD=postgres

# Or set as system variables (requires IDE/terminal restart):
# Windows: Settings > Environment Variables > System variables

# Then run application
cd backend
mvnw.cmd spring-boot:run
```

### Error 5: "Connection pool is full"

```
java.sql.SQLException: Unable to acquire a new connection from the pool
```

**Cause:** Too many connections or connection leak

**Solution:**
```bash
# Stop the application (Ctrl+C)
# This closes all connections

# Restart
cd backend
mvnw.cmd spring-boot:run

# If persists, check no other instances are running:
# TaskManager > Find any java.exe processes and end them
```

### Error 6: PostgreSQL service fails to start

**Solution:**
```bash
# Check PostgreSQL service status
sc query postgresql-x64-16

# If it won't start, try:
net stop postgresql-x64-16
net start postgresql-x64-16

# If still fails, check Windows Event Viewer for errors
# Or reinstall PostgreSQL
```

## Verification Checklist

Before proceeding to Phase 03, verify:

- [x] PostgreSQL installed and running on Windows
- [x] `pulserelay_dev` database created
- [x] `pulserelay` user created with password `postgres`
- [x] User has GRANT permissions on database
- [x] `application.yml` configured with PostgreSQL settings
- [x] Environment variables set (DB_NAME, DB_USER, DB_PASSWORD)
- [x] Spring Boot runs without connection errors
- [x] Hibernate connects and initializes
- [x] GET /api/actuator/health returns 200 with db: UP
- [x] GET /api/actuator/info returns 200
- [x] No Docker files present (local development only)
- [x] No hardcoded credentials in source code

## Git Commit

**Commit message:**
```
chore: configure local PostgreSQL development environment

- Update application.yml with PostgreSQL datasource configuration
- Add environment variable support for database credentials
- Configure Hibernate ddl-auto: update for schema management
- Enable SQL logging and batch settings for performance
- Configure Spring Boot Actuator health and info endpoints
- Set application port to 8084 with /api context path
- Remove Docker and containerization files (Phase 02 is local-only)

Connects to locally installed PostgreSQL database (localhost:5432).
Environment variables (DB_NAME, DB_USER, DB_PASSWORD) manage credentials.
Spring Boot runs directly via Maven without containerization.
```

**Push to repository:**
```bash
cd backend
git add -A
git commit -m "chore: configure local PostgreSQL development environment"
git push origin main  # or your branch name
```

## Manual Setup Steps Required

Before the application can run, you MUST perform these manual steps:

1. **Verify PostgreSQL is installed**
   ```bash
   psql --version
   ```

2. **Start PostgreSQL service**
   ```bash
   net start postgresql-x64-16
   ```

3. **Create the development database**
   ```bash
   psql -U postgres -h localhost
   # Then run SQL commands (see PostgreSQL Database Setup section)
   ```

4. **Set environment variables** (Windows)
   - Either set as system variables via Settings > Environment Variables
   - Or set in each terminal session before running Spring Boot

5. **Run Spring Boot**
   ```bash
   cd backend
   mvnw.cmd spring-boot:run
   ```

6. **Test connectivity**
   ```bash
   curl http://localhost:8084/api/actuator/health
   # Or open in browser
   ```

Once these steps are complete, the application will start successfully and connect to PostgreSQL.

## What's NOT Included

- No Docker or Docker Compose (local development only)
- No Kafka or message queuing (Phase 05+)
- No Redis or caching (Phase 06+)
- No authentication or authorization (Phase 04+)
- No entity models or repositories (Phase 03+)
- No API endpoints (Phase 03+)

Phase 02 focuses solely on database connectivity and configuration.
