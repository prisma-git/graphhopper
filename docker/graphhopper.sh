#!/bin/bash
mkdir -p /opt/graphhopper/data/graph-cache
chgrp -R work /opt/graphhopper/data/graph-cache
chmod -R 664 /opt/graphhopper/data/graph-cache
java -Xmx10g -Xms10g -jar /opt/graphhopper/graphhopper-web-11.0-SNAPSHOT.jar server /opt/graphhopper/data/config/config.yml