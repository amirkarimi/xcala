ARG JVM=openjdk-11.0.16
ARG SBT_VERSION=1.8.1
ARG SCALA_VERSION=2.13.10

FROM hub.hamdocker.ir/sbtscala/scala-sbt:${JVM}_${SBT_VERSION}_${SCALA_VERSION} as builder

ARG NEXUS_KEY

# Install NodeJS for faster stage output from SBT
RUN apt update --allow-releaseinfo-change \
    && apt install -y nodejs npm \
    && apt install -y webp \
    && rm -rf /var/lib/apt/lists/*

# Cache sbt build definition, this layer rarely changes.
WORKDIR /opt
ADD project /opt/project
ADD build.sbt /opt/
RUN sbt clean update updateClassifiers

# Prepare build
ADD app /opt/app
ADD conf /opt/conf

# Actual building
RUN sbt test publish
