#!/bin/bash

java -Ddw.graphhopper.datareader.file=/opt/graphhopper/data/source/dach-latest.osm2.pbf -jar graphhopper-web-5.0-SNAPSHOT.jar server /opt/graphhopper/data/config/config.yml