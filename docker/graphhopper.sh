#!/bin/bash
java -Xmx10g -Xms10g -jar /opt/graphhopper/graphhopper-web-5.0-SNAPSHOT.jar import /opt/graphhopper/data/config/config.yml
java -Xmx10g -Xms10g -jar /opt/graphhopper/graphhopper-web-5.0-SNAPSHOT.jar server /opt/graphhopper/data/config/config.yml