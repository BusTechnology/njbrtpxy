FROM tomcat:7.0.79-jre8-alpine

MAINTAINER Philip Matuskiewicz <philip.matuskiewicz@nyct.com>

RUN apk add --update unzip python py-pip bash curl && \
    rm -rf /var/cache/apk/*
RUN pip install s3cmd awscli

EXPOSE 8080

RUN mkdir -p /oba/njb/bundle
RUN rm -Rf /usr/local/tomcat/webapps/*
COPY run.sh /run.sh
COPY build/libs/njb-rt-proxy.war /usr/local/tomcat/webapps/ROOT.war
COPY /tmp/njb_bundle/* /oba/njb/bundle/

CMD sh /run.sh
