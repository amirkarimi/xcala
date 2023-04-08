FROM registry.hamdocker.ir/ketab/sbt:main as builder

ARG NEXUS_KEY

# Cache sbt build definition, this layer rarely changes.
WORKDIR         /opt
ADD project     /opt/project
ADD build.sbt   /opt/
RUN sbt clean update updateClassifiers

# Prepare build
ADD app     /opt/app
ADD conf    /opt/conf
ADD test    /opt/test

