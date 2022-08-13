ARG JVM=11.0.14.1
ARG SBT_VERSION=1.6.2
ARG SCALA_VERSION=2.12.15

FROM hub.hamdocker.ir/hseeberger/scala-sbt:${JVM}_${SBT_VERSION}_${SCALA_VERSION} as builder

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
