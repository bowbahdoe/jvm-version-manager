# Shows available commands
help:
    just --list

# Checks presence of cli tools needed for commands
check_cli_tools:
    java --version || echo "please install java"
    migrate info || echo "please install mybatis migrations"
    clojure --version || echo "please install the clojure cli tools"
    jresolve --version || echo "please install jresolve"
    npx --version || echo "npm is needed for tailwind"
    goat --version || echo "goat is needed to publish atproto schemas"

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

# Run tests
test:
    clojure -A:test -M -m kaocha.runner

# Start a REPL to connect to for development
nrepl:
    export $(cat .env | xargs) && clojure -A:dev -M -m nrepl.cmdline

# Publish ATProto Lexicon Definitions
publish_lexicons:
    # goat account login --username mccue.dev --password ...
    # (note to self: escape !s in password with \!)
    goat lex publish

# Build the CLI artifact
build_cli:
    clojure -A:build -M -m build

# Run tthe CLI
jvm *args:
    @clojure -J--enable-native-access=ALL-UNNAMED -A:cli -M -m dev.mccue.jvm.cli {{args}}

# Run the CLI (from a built uberjar)
jvm_uber *args:
    java -jar target/jvm.jar {{args}}
