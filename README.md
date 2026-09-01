# Maps Backend

Spring Boot REST API for a maps application. It calculates routes between two points from
OpenStreetMap data and serves them as JSON.

Frontend (separate repository): https://github.com/antoni-kuczynski/MapsAppFrontend

## Status

Early stage. `GET /api/directions` is implemented and covered by tests. The marker and route
endpoints are placeholders that accept a request and do nothing yet.

Routing currently runs on [GraphHopper](https://github.com/graphhopper/graphhopper) embedded in the
application. The long-term plan is to replace it with a routing engine written for this project.

## Tech stack

| | |
| --- | --- |
| Language | Java 25 |
| Framework | Spring Boot 4.1 (Spring Framework 7) |
| Build | Maven (wrapper included) |
| Routing engine | GraphHopper 11.0, embedded |
| Map data | OpenStreetMap extract (`.osm.pbf`) |
| Database | PostgreSQL *(Work in progress)* |

## Prerequisites

- JDK 25
- An OpenStreetMap extract for the area you want to route in
- Roughly 2 GB of free RAM and 1 GB of disk for a region the size of a Polish voivodeship

If `java -version` on your machine reports an older JDK, point `JAVA_HOME` at a JDK 25 install
for every Maven command, for example:

```bash
export JAVA_HOME=~/.jdks/openjdk-25.0.2
