package at.prismasolutions.graphhopper.web;

import at.prismasolutions.graphhopper.extension.GHEvent;
import at.prismasolutions.graphhopper.extension.GHEventManager;
import at.prismasolutions.graphhopper.extension.GHEventMapper;
import at.prismasolutions.graphhopper.extension.GraphHopperWithId;
import com.graphhopper.GraphHopper;
import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.ev.DecimalEncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.IntEncodedValue;
import com.graphhopper.routing.ev.LongEncodedValue;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.search.KVStorage;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.BBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only GHEvent overlay + matched-edge inspection for the net-matching QA map
 * ({@code /maps/qa}). Served straight from the in-memory {@link GHEventMapper}, reusing
 * the same LocationIndex traversal as {@code MVTResource} so it stays cheap and
 * consistent with the /mvt net layer.
 *
 * <ul>
 *   <li>{@code GET /events?bbox=minLon,minLat,maxLon,maxLat} - the GHEvents whose matched
 *       graph edges fall in the bbox (each with DATEX geometry + QA-enriched caption).</li>
 *   <li>{@code GET /events/edges?bbox=...&way=<osmWayId>} - the graph edges of that OSM way
 *       in the bbox, with their encoded-value attributes (like the /maps edge popup) and
 *       geometry, for the click-to-inspect edge list + highlight.</li>
 * </ul>
 *
 * The frontend only calls these when zoomed in (like a tiled layer); the server also
 * refuses bboxes larger than {@link #MAX_AREA_DEG2}.
 *
 * prisma-solutions addition (kept in at.prismasolutions.* to stay distinct from upstream).
 */
@Path("events")
public class EventsResource {

    private static final Logger logger = LoggerFactory.getLogger(EventsResource.class);
    private static final double MAX_AREA_DEG2 = 1.5;
    private static final int MAX_EVENTS = 25_000;

    private final GraphHopper graphHopper;
    private final EncodingManager encodingManager;

    @Inject
    public EventsResource(GraphHopper graphHopper, EncodingManager encodingManager) {
        this.graphHopper = graphHopper;
        this.encodingManager = encodingManager;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@QueryParam("bbox") String bboxStr) {
        if (!(graphHopper instanceof GraphHopperWithId))
            return Response.ok(Collections.emptyList()).build();
        GHEventManager manager = ((GraphHopperWithId) graphHopper).getManager();
        if (manager == null || manager.getMapper() == null)
            return Response.ok(Collections.emptyList()).build();
        final GHEventMapper mapper = manager.getMapper();

        BBox bbox = requireBBox(bboxStr);
        if (tooBig(bbox))
            return Response.ok(Collections.emptyList()).header("X-GH-Events", "zoomed-out").build();

        final Set<GHEvent> found = Collections.newSetFromMap(new IdentityHashMap<>());
        LocationIndexTree locationIndex = (LocationIndexTree) graphHopper.getLocationIndex();
        locationIndex.query(bbox, edgeId -> {
            List<GHEvent> evs = mapper.getGHEvents(edgeId);
            if (evs != null && !evs.isEmpty())
                found.addAll(evs);
        });

        List<GHEvent> out = new ArrayList<>(found);
        boolean truncated = out.size() > MAX_EVENTS;
        if (truncated) {
            logger.info("events bbox {} matched {} events, truncating to {}", bboxStr, out.size(), MAX_EVENTS);
            out = out.subList(0, MAX_EVENTS);
        }
        return Response.ok(out).header("X-GH-Events", (truncated ? "truncated:" : "") + out.size()).build();
    }

    /** The graph edges of one or more OSM ways (comma-separated) inside the bbox, with
     *  attributes + geometry. Each edge carries its osm_way_id so the caller can group. */
    @GET
    @Path("edges")
    @Produces(MediaType.APPLICATION_JSON)
    public Response edges(@QueryParam("bbox") String bboxStr, @QueryParam("way") String way) {
        if (!(graphHopper instanceof GraphHopperWithId) || way == null || way.isEmpty())
            return Response.ok(Collections.emptyList()).build();
        final GraphHopperWithId idHopper = (GraphHopperWithId) graphHopper;
        final Set<String> ways = new java.util.HashSet<>(java.util.Arrays.asList(way.split(",")));

        BBox bbox = requireBBox(bboxStr);
        if (tooBig(bbox))
            return Response.ok(Collections.emptyList()).header("X-GH-Events", "zoomed-out").build();

        final List<Map<String, Object>> edges = new ArrayList<>();
        final Set<Integer> seen = new java.util.HashSet<>();
        LocationIndexTree locationIndex = (LocationIndexTree) graphHopper.getLocationIndex();
        locationIndex.query(bbox, edgeId -> {
            if (!seen.add(edgeId))
                return;
            Long wayId = idHopper.getWay(edgeId);
            if (wayId == null || !ways.contains(String.valueOf(wayId)))
                return;
            EdgeIteratorState edge = graphHopper.getBaseGraph().getEdgeIteratorStateForKey(edgeId * 2);
            Map<String, Object> m = describeEdge(edge);
            m.put("osm_way_id", String.valueOf(wayId));
            edges.add(m);
        });
        return Response.ok(edges).header("X-GH-Events", "edges:" + edges.size()).build();
    }

    /** Edge attributes (edge_id, osm way, distance, all encoded values) + geometry as WKT. */
    private Map<String, Object> describeEdge(EdgeIteratorState edge) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("edge_id", edge.getEdge());
        m.put("edge_key", edge.getEdgeKey());
        m.put("base_node", edge.getBaseNode());
        m.put("adj_node", edge.getAdjNode());
        m.put("distance", edge.getDistance());
        for (Map.Entry<String, KVStorage.KValue> e : edge.getKeyValues().entrySet())
            m.put(e.getKey(), e.getValue().toString());
        encodingManager.getEncodedValues().forEach(ev -> {
            String two = ev.isStoreTwoDirections() ? " | " : "";
            if (ev instanceof EnumEncodedValue)
                m.put(ev.getName(), edge.get((EnumEncodedValue) ev).toString()
                        + (two.isEmpty() ? "" : two + edge.getReverse((EnumEncodedValue) ev)));
            else if (ev instanceof DecimalEncodedValue)
                m.put(ev.getName(), edge.get((DecimalEncodedValue) ev)
                        + (two.isEmpty() ? "" : two + edge.getReverse((DecimalEncodedValue) ev)));
            else if (ev instanceof BooleanEncodedValue)
                m.put(ev.getName(), edge.get((BooleanEncodedValue) ev)
                        + (two.isEmpty() ? "" : two + edge.getReverse((BooleanEncodedValue) ev)));
            else if (ev instanceof IntEncodedValue)
                m.put(ev.getName(), edge.get((IntEncodedValue) ev)
                        + (two.isEmpty() ? "" : two + edge.getReverse((IntEncodedValue) ev)));
            else if (ev instanceof LongEncodedValue)
                m.put(ev.getName(), edge.get((LongEncodedValue) ev)
                        + (two.isEmpty() ? "" : two + edge.getReverse((LongEncodedValue) ev)));
        });
        m.put("geometry", toWkt(edge.fetchWayGeometry(FetchMode.ALL)));
        return m;
    }

    private static String toWkt(PointList pl) {
        if (pl.size() < 2)
            return null;
        StringBuilder sb = new StringBuilder("LINESTRING(");
        for (int i = 0; i < pl.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(pl.getLon(i)).append(' ').append(pl.getLat(i));
        }
        return sb.append(')').toString();
    }

    private static BBox requireBBox(String s) {
        if (s == null || s.isEmpty())
            throw new IllegalArgumentException("bbox required as 'minLon,minLat,maxLon,maxLat'");
        String[] p = s.split(",");
        if (p.length != 4)
            throw new IllegalArgumentException("bbox must be 'minLon,minLat,maxLon,maxLat'");
        try {
            BBox b = new BBox(Double.parseDouble(p[0].trim()), Double.parseDouble(p[2].trim()),
                    Double.parseDouble(p[1].trim()), Double.parseDouble(p[3].trim()));
            if (!b.isValid())
                throw new IllegalArgumentException("invalid bbox " + s);
            return b;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bbox must be four numbers");
        }
    }

    private static boolean tooBig(BBox b) {
        return (b.maxLon - b.minLon) * (b.maxLat - b.minLat) > MAX_AREA_DEG2;
    }
}
