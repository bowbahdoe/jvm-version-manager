FROM node:lts-alpine3.23 as tailwind

COPY ./css ./css
COPY package.json package.json
COPY package-lock.json package-lock.json

RUN npx @tailwindcss/cli --optimize -i ./css/input.css -o ./res/tailwind.css

FROM eclipse-temurin:26-jdk

# Install curl
RUN apt-get update && apt-get install -y curl \
    && rm -rf /var/lib/apt/lists/*

# Install Clojure CLI
RUN curl -L -O https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
RUN chmod +x linux-install.sh
RUN ./linux-install.sh
RUN rm linux-install.sh

# Copy Project Files Over
COPY --from=tailwind ./res/tailwind.css ./res/tailwind.css
COPY ./src ./src
COPY ./res ./res
COPY ./jars ./jars
COPY ./deps.edn ./deps.edn

# Cache Dependencies
RUN clojure -P

# Run the app
CMD ["clojure", "-M", "-m", "dev.mccue.system"]