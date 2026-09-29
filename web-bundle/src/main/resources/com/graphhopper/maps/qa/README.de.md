# GHEvents-QA-Endpunkte (`/events`, `/events/edges`)

*English version: [README.md](README.md)*

Schreibgeschützte HTTP-Endpunkte, die das **In-Memory-GHEvent-Overlay** und die **gematchten
Graph-Kanten** für eine Bounding-Box bereitstellen, damit ein Kartenclient DATEX-Meldungen
über dem Straßennetz darstellen und prüfen kann, wie jede Meldung net-gematcht wurde. Sie
treiben die Net-Matching-QA-Karte unter `/maps/qa` an und werden von `EventsResource`
bereitgestellt (`at.prismasolutions.graphhopper.web.EventsResource`, eine
prisma-solutions-Ergänzung).

Die Daten kommen direkt aus dem laufenden `GHEventMapper` — denselben Events, die auch das
Routing nutzt — über den gemeinsamen `LocationIndex`, genau wie beim eingebauten
`/mvt`-Netz-Layer. Pro Anfrage wird nichts von der Platte gelesen, das Overlay spiegelt also
immer wider, was GraphHopper aktuell geladen hat.

> **Kein Protobuf-MVT.** Obwohl sie wie ein gekachelter Vektor-Layer konsumiert werden,
> liefern diese Endpunkte **auf eine Bbox begrenztes JSON**, keine Mapbox Vector Tiles. Der
> *Netz*-Layer ist GraphHoppers eigenes `/mvt` (Protobuf); das *Events*-Overlay ist diese
> JSON-API. Beide sollten nur im herangezoomten Zustand abgefragt werden.

## Endpunkte

### `GET /events?bbox=minLon,minLat,maxLon,maxLat`

Liefert jede GHEvent, deren **gematchte Graph-Kanten** die Bbox schneiden, als JSON-Array. Pro
gematchtem OSM-Way wird eine GHEvent ausgegeben (so schreibt sie der Generator), eine über
mehrere Ways verlaufende Sperrung erscheint also als mehrere Events mit derselben `ext_id`.
Innerhalb der Antwort nach Objektidentität dedupliziert.

Jede Event ist die GHEvent, wie GraphHopper sie serialisiert (**snake_case**-Feldnamen):

| Feld | Bedeutung |
|---|---|
| `ext_id` | Quell-`situationRecord`-id (von allen Kanten eines DATEX-Records geteilt) |
| `ext_edge_id` | die OSM-Way-id, auf die diese Event bezogen ist |
| `ext_type` | DATEX-Record-Typ (z. B. `RoadOrCarriagewayOrLaneManagement`) |
| `type` | GHEventType (`ALL`/`DESCRIPTION`/`EQUAL`/`LESSERTHAN`/`GREATERTHAN`) |
| `factor` | `<0` sperrt die Kante · `>1` verteuert · `<1` günstiger |
| `direction` | `0` vorwärts · `1` rückwärts · `2` beide |
| `caption` | menschenlesbarer Text (Inhalt hängt vom `--caption-mode` des Generators ab) |
| `shape` | Quell-DATEX-Geometrie als **WKT** (`LINESTRING(lon lat, …)` / `POINT(lon lat)`), lon/lat WGS84 — nur vorhanden, wenn der Generator mit `--include-shape` lief |
| `start_date` / `end_date` | ISO-8601-Gültigkeitsfenster (weggelassen, wenn offen) |
| `parameter_name` / `parameter_value` | Parameter einer bedingten Einschränkung (optional) |

### `GET /events/edges?bbox=minLon,minLat,maxLon,maxLat&way=<osmWayId>[,<osmWayId>…]`

Liefert die **Graph-Kanten** der angegebenen OSM-Way-id(s), die in die Bbox fallen — genutzt,
um die von einer Event gematchten Kanten aufzulisten und hervorzuheben. `way` ist eine
kommagetrennte Liste von OSM-Way-ids (die `ext_edge_id`-Werte). Jede Kante ist ein
JSON-Objekt:

| Feld | Bedeutung |
|---|---|
| `osm_way_id` | der OSM-Way, zu dem diese Kante gehört (zur Gruppierung) |
| `edge_id`, `edge_key`, `base_node`, `adj_node` | GraphHopper-Graph-Bezeichner |
| `distance` | Kantenlänge in Metern |
| *(Encoded Values)* | jeder Encoded Value der Kante (z. B. `road_class`, `max_speed`, `car_access`); richtungsabhängige Werte werden als `vorwärts \| rückwärts` dargestellt |
| `geometry` | die Kantengeometrie als **WKT** `LINESTRING(lon lat, …)`, lon/lat WGS84 |

## Anfrageregeln & Antworten

- **`bbox` ist erforderlich** und lautet `minLon,minLat,maxLon,maxLat` (WGS84 / EPSG:4326).
- **Flächengrenze:** eine Bbox größer als **1,5 Grad²** liefert ein leeres Array mit dem
  Header `X-GH-Events: zoomed-out`. Nur herangezoomt abfragen (der QA-Client nutzt `minZoom`
  13).
- **Obergrenze:** `/events` liefert höchstens **25 000** Events; bei mehr Treffern wird die
  Antwort abgeschnitten und der Header lautet `X-GH-Events: truncated:<n>`, sonst
  `X-GH-Events: <n>`.
- Ist das laufende GraphHopper nicht der id-fähige Build oder sind keine Events geladen,
  liefern die Endpunkte `[]` (nie einen Fehler), sodass die Karte sauber degradiert.

## OpenLayers-Beispiel

Ein minimales Overlay: `/events` pro Viewport mit einer Bbox-Ladestrategie abrufen, die WKT-
`shape` in Geometrie parsen, nach `factor` einfärben und beim Klick die gematchten Kanten aus
`/events/edges` holen. ES-Modul-Form (`ol` ≥ 7); für den CDN-Global-Build die Importe durch
`ol.Map`, `ol.source.Vector`, `ol.format.WKT`, `ol.loadingstrategy.bbox`,
`ol.proj.transformExtent`, `ol.style.*` ersetzen.

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

const API = '';                 // gleiche Origin wie GraphHopper; z. B. 'https://gh.example.com'
const wkt = new WKT();

const eventSource = new VectorSource({
  strategy: bboxStrategy,       // bei jeder Viewport-Bewegung neu laden
  loader(extent, resolution, projection, success, failure) {
    // OpenLayers liefert den Extent in der View-Projektion (meist 3857) -> nach lon/lat
    const [minLon, minLat, maxLon, maxLat] = transformExtent(extent, projection, 'EPSG:4326');
    fetch(`${API}/events?bbox=${minLon},${minLat},${maxLon},${maxLat}`)
      .then((r) => r.json())
      .then((events) => {
        const features = [];
        for (const ev of events) {
          if (!ev.shape) continue;                 // nur Events mit WKT-Geometrie
          const f = wkt.readFeature(ev.shape, {
            dataProjection: 'EPSG:4326',           // shape ist lon/lat WGS84
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

// factor bestimmt die Farbe: <0 Sperrung (rot), >1 Malus (orange), sonst Info (blau)
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
  minZoom: 13,                  // Server lehnt Bboxen > 1,5 Grad^2 ab — nur herangezoomt laden
});

const map = new Map({
  target: 'map',
  layers: [new TileLayer({source: new OSM()}), eventLayer],
  view: new View({center: [0, 0], zoom: 14}),
});

// Klick auf eine Event -> die gematchten Graph-Kanten holen und ausgeben
map.on('singleclick', async (e) => {
  const feature = map.forEachFeatureAtPixel(e.pixel, (f) => f, {layerFilter: (l) => l === eventLayer});
  if (!feature) return;

  // Events kommen einzeln pro OSM-Way; nach ext_id gruppieren, um alle Ways einer Sperrung zu sammeln.
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
  // edges[i].geometry ist WKT — mit demselben `wkt.readFeature(...)` parsen, um ein Highlight zu zeichnen.
});
```

### Hinweise

- **Gruppierung:** Da `/events` eine Event pro gematchtem OSM-Way liefert, nach `ext_id`
  gruppieren, um eine ganze Sperrung zu rekonstruieren (wie der Klick-Handler oben). Der
  Referenz-QA-Client erkennt zudem die Berliner `_dir2`-Gegenrichtungs-Companions am
  id-Muster — siehe das QA-Karten-README.
- **Kanten hervorheben:** das WKT aus `/events/edges` `geometry` durch dasselbe `WKT`-Format
  in einen zweiten `VectorLayer` geben, um exakt die von einer Event abgedeckten Graph-Kanten
  zu umranden.
- **Performance:** beide Endpunkte sind `LocationIndex`-Bbox-Scans über In-Memory-Daten und
  günstig; `minZoom` + Flächengrenze halten die Ergebnismengen klein, nicht weil der Server
  langsam wäre.
