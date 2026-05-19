FROM eclipse-temurin:26-jdk

# Install curl
RUN apt-get install -y curl

# Install Clojure CLI
RUN wget -O linux-install.sh https://github.com/clojure/brew-install/releases/latest/download/linux-install.sh
RUN chmod +x linux-install.sh
RUN sudo ./linux-install.sh
RUN rm linux-install.sh

# Copy Project Files Over
COPY ./src ./src
COPY ./res ./res
COPY ./deps.edn ./deps.edn

# Cache Dependencies
RUN clojure -P

# Run the app
CMD ["clojure", "-M", "-m", "dev.mccue.repository.system"]