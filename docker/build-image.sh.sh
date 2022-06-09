#!/bin/sh

if [ -z "$1" ]
then
  echo "host not set";
  docker image build -t prismasolutions/graphhopper  .
else
  echo "host is " $1;
  sudo -u work docker -H $1 image build -t prismasolutions/graphhopper .
fi