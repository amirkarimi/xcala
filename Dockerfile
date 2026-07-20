FROM registry.hamdocker.ir/ketab/sbt:main as builder

ARG NEXUS_KEY
ENV NEXUS_USER=ci

RUN update_credentials

# Cache sbt build definition, this layer rarely changes.
WORKDIR         /opt
ADD project     /opt/project
ADD build.sbt   /opt/
RUN sbt -Dci=true clean update

# Prepare build
ADD app           /opt/app
ADD conf          /opt/conf
ADD shared-assets /opt/shared-assets

# Test image adds the test sources on top of the build image.
FROM builder as test
ADD test    /opt/test

