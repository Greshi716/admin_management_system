#!/bin/sh
set -e

# Pass Render PORT to Spring Boot
if [ -n "$PORT" ]; then
    export SERVER_PORT="$PORT"
fi

# Convert Render/Neon DATABASE_URL to Spring Boot JDBC format
if [ -n "$DATABASE_URL" ]; then
    # Strip protocol prefix (postgres:// or postgresql://)
    PROTO="$(echo "$DATABASE_URL" | grep :// | sed -e 's,^\(.*://\).*,\1,g')"
    URL_WITHOUT_PROTO="${DATABASE_URL#$PROTO}"

    # Extract user:password
    USERPASS="$(echo "$URL_WITHOUT_PROTO" | grep @ | cut -d@ -f1 || true)"
    HOSTPORTDB="${URL_WITHOUT_PROTO#$USERPASS@}"

    if [ -n "$USERPASS" ]; then
        DB_USER="$(echo "$USERPASS" | cut -d: -f1)"
        DB_PASS="$(echo "$USERPASS" | cut -d: -f2-)"
        export SPRING_DATASOURCE_USERNAME="$DB_USER"
        export SPRING_DATASOURCE_PASSWORD="$DB_PASS"
    fi

    HOSTPORT="$(echo "$HOSTPORTDB" | cut -d/ -f1)"
    DBNAME="$(echo "$HOSTPORTDB" | cut -d/ -f2 | cut -d? -f1)"

    # Construct clean JDBC URL with sslmode=require
    export SPRING_DATASOURCE_URL="jdbc:postgresql://${HOSTPORT}/${DBNAME}?sslmode=require"
fi

# In the background, monitor for credentials-first-run.txt and print to container logs
(
    DATA_DIR="${CAMPUSFLOW_DATA_DIR:-./data}"
    CRED_FILE="$DATA_DIR/credentials-first-run.txt"
    for i in $(seq 1 30); do
        if [ -f "$CRED_FILE" ]; then
            echo ""
            echo "=================================================================="
            echo " [CampusFlow] FIRST RUN ADMIN CREDENTIALS DETECTED:"
            cat "$CRED_FILE"
            echo "=================================================================="
            echo ""
            break
        fi
        sleep 2
    done
) &

exec "$@"
