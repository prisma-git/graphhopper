# GHEvents QA endpoints (`/events`, `/events/edges`)

Read-only HTTP endpoints that expose the **in-memory GHEvent overlay** and the **matched
graph edges** for a bounding box, so a map client can draw DATEX events on top of the road
network and inspect how each event net-matched. They power the net-matching QA map at
`/maps/qa` and are served by `EventsResource`
(`at.prismasolutions.graphhopper.web.EventsResource`, a prisma-solutions addition).

The data comes straight from the live `GHEventMapper` — the same events routing uses — via
the shared `LocationIndex`, exactly like the built-in `/mvt` net layer. Nothing is read from
disk per request, so the overlay always reflects what GraphHopper currently has loaded.

> **Not protobuf MVT.** Despite being consumed like a tiled vector layer, these endpoints
> return **JSON scoped to a bbox**, not Mapbox Vector Tiles. The *network* layer is
> GraphHopper's own `/mvt` (protobuf); the *events* overlay is this JSON API. Both are meant
> to be queried only when zoomed in.

## Endpoints

### `GET /events?bbox=minLon,minLat,maxLon,maxLat`

Returns every GHEvent whose **matched graph edges** intersect the bbox, as a JSON array. One
GHEvent is emitted per matched OSM way (that is how the generator writes them), so a closure
spanning several ways appears as several events sharing the same `ext_id`. Deduplicated by
object identity within the response.

Each event is the GHEvent as GraphHopper serializes it (**snake_case** field names):

| field | meaning |
|---|---|
| `ext_id` | source `situationRecord` id (shared by all edges of one DATEX record) |
| `ext_edge_id` | the OSM way id this event is scoped to |
| `ext_type` | DATEX record type (e.g. `RoadOrCarriagewayOrLaneManagement`) |
| `type` | GHEventType (`ALL`/`DESCRIPTION`/`EQUAL`/`LESSERTHAN`/`GREATERTHAN`) |
| `factor` | `<0` blocks the edge · `>1` penalises · `<1` cheaper |
| `direction` | `0` forward · `1` reverse · `2` both |
| `caption` | human-readable text (content depends on the generator's `--caption-mode`) |
| `shape` | source DATEX geometry as **WKT** (`LINESTRING(lon lat, …)` / `POINT(lon lat)`), lon/lat WGS84 — present only when the generator ran with `--include-shape` |
| `start_date` / `end_date` | ISO-8601 validity window (omitted if open-ended) |
| `parameter_name` / `parameter_value` | conditional-restriction parameter (optional) |

### `GET /events/edges?bbox=minLon,minLat,maxLon,maxLat&way=<osmWayId>[,<osmWayId>…]`

Returns the **graph edges** of the given OSM way id(s) that fall inside the bbox — used to
list and highlight the edges an event matched to. `way` is a comma-separated list of OSM way
ids (the `ext_edge_id` values). Each edge is a JSON object:

| field | meaning |
|---|---|
| `osm_way_id` | the OSM way this edge belongs to (for grouping) |
| `edge_id`, `edge_key`, `base_node`, `adj_node` | GraphHopper graph identifiers |
| `distance` | edge length in metres |
| *(encoded values)* | every encoded value on the edge (e.g. `road_class`, `max_speed`, `car_access`); two-directional values are rendered `forward \| reverse` |
| `geometry` | the edge geometry as **WKT** `LINESTRING(lon lat, …)`, lon/lat WGS84 |

## Request rules & responses

- **`bbox` is required** and is `minLon,minLat,maxLon,maxLat` (WGS84 / EPSG:4326).
- **Area guard:** a bbox larger than **1.5 deg²** returns an empty array with header
  `X-GH-Events: zoomed-out`. Only query when zoomed in (the QA client uses `minZoom` 13).
- **Cap:** `/events` returns at most **25 000** events; if more match, the response is
  truncated and the header reads `X-GH-Events: truncated:<n>`. Otherwise `X-GH-Events: <n>`.
- If the running GraphHopper is not the id-aware build, or no events are loaded, the
  endpoints return `[]` (never an error), so the map degrades gracefully.

## OpenLayers example

A minimal overlay: fetch `/events` per viewport with a bbox loading strategy, parse the WKT
`shape` into geometry, colour by `factor`, and on click pull the matched edges from
`/events/edges`. ES-module (`ol` ≥ 7) form; for the CDN global build, swap the imports for
`ol.Map`, `ol.source.Vector`, `ol.format.WKT`, `ol.loadingstrategy.bbox`,
`ol.proj.transformExtent`, `ol.style.*`.

```js
import Map from 'ol/Map.js';
import View from 'ol/View.js';
import TileLayer from 'ol/layer/Tile.js';
import OSM from 'ol/source/OSM.js';
import VectorLayer from 'ol/layer/Vector.js';
import VectorSource from 'ol/source/Vector.js';
import {bbox as bboxStrategy} from 'ol/loadingstrategy.js';
import WKT from 'ol/format/WKT.js';
import {Style, Stroke, Circle, Fill} from 'ol/style.js';
import {transformExtent} from 'ol/proj.js';

const API = '';                 // same origin as GraphHopper; e.g. 'https://gh.example.com'
const wkt = new WKT();

const eventSource = new VectorSource({
  strategy: bboxStrategy,       // re-fetch whenever the viewport moves
  loader(extent, resolution, projection, success, failure) {
    // OpenLayers passes the extent in the view projection (usually 3857) -> to lon/lat
    const [minLon, minLat, maxLon, maxLat] = transformExtent(extent, projection, 'EPSG:4326');
    fetch(`${API}/events?bbox=${minLon},${minLat},${maxLon},${maxLat}`)
      .then((r) => r.json())
      .then((events) => {
        const features = [];
        for (const ev of events) {
          if (!ev.shape) continue;                 // only events carrying WKT geometry
          const f = wkt.readFeature(ev.shape, {
            dataProjection: 'EPSG:4326',           // shape is lon/lat WGS84
            featureProjection: projection,
          });
          f.setProperties(ev, true);               // ext_id, ext_edge_id, caption, factor, …
          features.push(f);
        }
        eventSource.addFeatures(features);
        success(features);
      })
      .catch(() => { eventSource.removeLoadedExtent(extent); failure(); });
  },
});

// factor drives the colour: <0 block (red), >1 penalty (orange), else info (blue)
function styleFor(feature) {
  const factor = feature.get('factor');
  const color = factor < 0 ? '#e11' : factor > 1 ? '#f90' : '#39f';
  return new Style({
    stroke: new Stroke({color, width: 4}),
    image: new Circle({radius: 6, fill: new Fill({color}), stroke: new Stroke({color: '#fff', width: 1.5})}),
  });
}

const eventLayer = new VectorLayer({
  source: eventSource,
  style: styleFor,
  minZoom: 13,                  // server rejects bboxes > 1.5 deg^2 — only fetch zoomed in
});

const map = new Map({
  target: 'map',
  layers: [new TileLayer({source: new OSM()}), eventLayer],
  view: new View({center: [0, 0], zoom: 14}),
});

// click an event -> fetch and log the graph edges it matched to
map.on('singleclick', async (e) => {
  const feature = map.forEachFeatureAtPixel(e.pixel, (f) => f, {layerFilter: (l) => l === eventLayer});
  if (!feature) return;

  // events arrive one-per-OSM-way; group by ext_id to collect all ways of one closure.
  const extId = feature.get('ext_id');
  const ways = eventSource.getFeatures()
    .filter((f) => f.get('ext_id') === extId)
    .map((f) => f.get('ext_edge_id'));

  const view = map.getView();
  const [minLon, minLat, maxLon, maxLat] =
    transformExtent(view.calculateExtent(map.getSize()), view.getProjection(), 'EPSG:4326');

  const edges = await fetch(
    `${API}/events/edges?bbox=${minLon},${minLat},${maxLon},${maxLat}&way=${ways.join(',')}`
  ).then((r) => r.json());

  console.log(feature.get('caption'));
  console.table(edges.map((ed) => ({way: ed.osm_way_id, edge: ed.edge_id, m: Math.round(ed.distance)})));
  // edges[i].geometry is WKT — parse with the same `wkt.readFeature(...)` to draw a highlight layer.
});
```

### Notes

- **Grouping:** because `/events` is one event per matched OSM way, group by `ext_id` to
  reconstruct a whole closure (as the click handler above does). The reference QA client also
  detects Berlin `_dir2` opposite-direction companions by id pattern — see the QA map README.
- **Highlighting matched edges:** feed the WKT from `/events/edges` `geometry` through the
  same `WKT` format into a second `VectorLayer` to outline exactly which graph edges an event
  covers.
- **Performance:** both endpoints are `LocationIndex` bbox scans over in-memory data and are
  cheap; the `minZoom` + area guard exist to keep result sets small, not because the server is
  slow.
