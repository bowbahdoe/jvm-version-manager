# Shows available commands
help:
    just --list

# Checks presence of cli tools needed for commands
check_cli_tools:
    java --version || echo "please install java"
    migrate info || echo "please install mybatis migrations"
    clojure --version || echo "please install the clojure cli tools"
    jresolve --version || echo "please install jresolve"

# Downloads Postgresql Driver Jars needed for migrations
download_postgres_drivers:
    jresolve --output-directory migrations/drivers pkg:maven/org.postgresql/postgresql@42.7.11 pkg:maven/org.slf4j/slf4j-simple@2.0.18

# Applies pending migrations
migrate_up:
    cd migrations && migrate up

# Reverts migrations
migrate_down:
    cd migrations && migrate down

# Compile Tailwind CSS
tailwind_watch:
    npx @tailwindcss/cli -i ./css/input.css -o ./res/tailwind.css --watch
