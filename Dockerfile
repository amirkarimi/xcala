ARG JVM=11.0.10
ARG SBT_VERSION=1.4.7
ARG SCALA_VERSION=2.12.13

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
ADD public /opt/public

# Actual building
RUN sbt publish
