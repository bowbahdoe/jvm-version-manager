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

# Imports a set of libraries, partially to excercise the system
import_maven_libraries:
    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.openjfx --artifactId javafx-controls --classifier mac-aarch64 --version 26.0.1 --attribute license=GPLv2+CE os=macos arch=aarch64

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.openjfx --artifactId javafx-controls --classifier win --version 26.0.1 --attribute license=GPLv2+CE os=windows arch=amd64

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.openjfx --artifactId javafx-controls --classifier linux --version 26.0.1 --attribute license=GPLv2+CE os=linux arch=amd64

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.openjfx --artifactId javafx-controls --classifier linux-aarch64 --version 26.0.1 --attribute license=GPLv2+CE os=linux arch=aarch64

    sleep 10
    ## ....

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.openjfx --artifactId javafx-graphics --classifier mac-aarch64 --version 26.0.1 --attribute license=GPLv2+CE os=macos arch=aarch64

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.openjfx --artifactId javafx-graphics --classifier win --version 26.0.1 --attribute license=GPLv2+CE os=windows arch=amd64

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.openjfx --artifactId javafx-graphics --classifier linux --version 26.0.1 --attribute license=GPLv2+CE os=linux arch=amd64

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.openjfx --artifactId javafx-graphics --classifier linux-aarch64 --version 26.0.1 --attribute license=GPLv2+CE os=linux arch=aarch64

    sleep 10
    ## ....

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.openjfx --artifactId javafx-base --classifier mac-aarch64 --version 26.0.1 --attribute license=GPLv2+CE os=macos arch=aarch64

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.openjfx --artifactId javafx-base --classifier win --version 26.0.1 --attribute license=GPLv2+CE os=windows arch=amd64

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.openjfx --artifactId javafx-base --classifier linux --version 26.0.1 --attribute license=GPLv2+CE os=linux arch=amd64

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.openjfx --artifactId javafx-base --classifier linux-aarch64 --version 26.0.1 --attribute license=GPLv2+CE os=linux arch=aarch64

    sleep 10
    ## ....

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.jspecify --artifactId jspecify --version 1.0.0 # --attribute "license=The Apache License, Version 2.0"

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId dev.mccue --artifactId magic-bean --version 2025.02.09 # --attribute "license=Apache License, Version 2.0"

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId dev.mccue --artifactId jdbc --version 2025.10.07 # --attribute "license=Apache License, Version 2.0"

    sleep 10
    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId dev.mccue --artifactId tools --version 2025.01.31
    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId dev.mccue --artifactId tools-javac --version 2025.01.31
    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId dev.mccue --artifactId tools-jar --version 2025.01.31

    sleep 10
    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId dev.mccue --artifactId tools-java --version 2025.01.31
    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId dev.mccue --artifactId tools-javadoc --version 2025.01.31
    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId dev.mccue --artifactId tools-javap --version 2025.01.31

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId dev.mccue --artifactId tools-jlink --version 2025.01.31

    sleep 10
    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId dev.mccue --artifactId tools-jmod --version 2025.01.31

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId dev.mccue --artifactId tools-jpackage --version 2025.01.31

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId dev.mccue --artifactId tools-jdk --version 2025.01.31

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId info.picocli --artifactId picocli --version 4.7.7

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId com.fasterxml.jackson.core --artifactId jackson-core --version 2.22.0

    sleep 10
    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId com.fasterxml.jackson.core --artifactId jackson-databind --version 2.22.0

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId com.fasterxml.jackson.core --artifactId jackson-annotations --version 2.22

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.apache.commons --artifactId commons-lang3 --version 3.20.0

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.apache.commons --artifactId commons-compress --version 1.28.0

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId commons-io --artifactId commons-io --version 2.20.0

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId com.google.guava --artifactId guava --version 33.6.0-jre

    sleep 10
    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId com.google.guava --artifactId failureaccess --version 1.0.3

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.slf4j --artifactId slf4j-api --version 2.0.18

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId org.slf4j --artifactId slf4j-simple --version 2.0.18

    export $(cat .env | xargs) && \
        just jvm maven-import --append --groupId com.github.ben-manes.caffeine --artifactId caffeine --version 3.2.4



