FROM ubuntu:22.04

# Prevent interactive prompts during apt installations
ENV DEBIAN_FRONTEND=noninteractive

# Install prerequisites: Java 17, curl, wget, gnupg, spin
RUN apt-get update && \
    apt-get install -y openjdk-17-jdk curl wget gnupg spin unzip git && \
    rm -rf /var/lib/apt/lists/*

# Install sbt
RUN echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | tee /etc/apt/sources.list.d/sbt.list && \
    echo "deb https://repo.scala-sbt.org/scalasbt/debian /" | tee /etc/apt/sources.list.d/sbt_old.list && \
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | gpg --no-default-keyring --keyring gnupg-ring:/etc/apt/trusted.gpg.d/scalasbt-release.gpg --import && \
    chmod 644 /etc/apt/trusted.gpg.d/scalasbt-release.gpg && \
    apt-get update && \
    apt-get install -y sbt && \
    rm -rf /var/lib/apt/lists/*

# Setup tools directory
RUN mkdir -p /opt/tools

# Download ANTLR v4
ENV ANTLR_VERSION=4.13.2
RUN curl -o /opt/tools/antlr.jar https://www.antlr.org/download/antlr-${ANTLR_VERSION}-complete.jar

# Download TLA+ tools
ENV TLA_VERSION=v1.8.0
RUN curl -L -o /opt/tools/tla2tools.jar https://github.com/tlaplus/tlaplus/releases/download/${TLA_VERSION}/tla2tools.jar

# Setup pcal and tlc commands
RUN echo '#!/bin/bash\njava -cp /opt/tools/tla2tools.jar tlc2.TLC "$@"' > /usr/local/bin/tlc && \
    echo '#!/bin/bash\njava -cp /opt/tools/tla2tools.jar pcal.trans "$@"' > /usr/local/bin/pcal && \
    chmod +x /usr/local/bin/tlc /usr/local/bin/pcal

# Create a local user whose UID/GID match the host (passed via build args)
ARG UID=1000
ARG GID=1000
RUN groupadd --gid ${GID} dev && \
    useradd --create-home --uid ${UID} --gid dev --shell /bin/bash dev

# Set working directory
WORKDIR /app
RUN chown dev:dev /app

# The environment variables for antlr (to use from scripts if needed)
ENV ANTLR_JAR=/opt/tools/antlr.jar

CMD ["/bin/bash"]
