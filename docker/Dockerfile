FROM openjdk:11.0-jre

RUN mkdir /opt/graphhopper/

COPY graphhopper-web-*.jar /opt/graphhopper/
COPY graphhopper.sh /opt/graphhopper/
RUN ["chmod", "+x", "/opt/graphhopper/graphhopper.sh"]
