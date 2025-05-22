#!/bin/bash
mkdir -p /opt/graphhopper/data/graph-cache
chgrp -R work /opt/graphhopper/data/graph-cache
chmod -R 664 /opt/graphhopper/data/graph-cache
java -Xmx7g -Xms7g -jar /opt/graphhopper/graphhopper-web-5.0-SNAPSHOT.jar server /opt/graphhopper/data/config/config.yml