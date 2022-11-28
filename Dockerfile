ARG JVM=openjdk-11.0.16
ARG SBT_VERSION=1.8.0
ARG SCALA_VERSION=2.12.17

FROM hub.hamdocker.ir/sbtscala/scala-sbt:${JVM}_${SBT_VERSION}_${SCALA_VERSION} as builder

ARG NEXUS_KEY

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
