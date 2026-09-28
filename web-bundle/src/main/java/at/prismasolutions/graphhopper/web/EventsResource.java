package at.prismasolutions.graphhopper.web;

import at.prismasolutions.graphhopper.extension.GHEvent;
import at.prismasolutions.graphhopper.extension.GHEventManager;
import at.prismasolutions.graphhopper.extension.GHEventMapper;
import at.prismasolutions.graphhopper.extension.GraphHopperWithId;
import com.graphhopper.GraphHopper;
import com.graphhopper.storage.index.LocationIndexTree;
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
import java.util.List;
import java.util.Set;

/**
 * Read-only GHEvent overlay for the net-matching QA map, served straight from the
 * in-memory {@link GHEventMapper} (no file re-read). Returns the events whose matched
 * graph edges fall inside the requested bbox, reusing the same LocationIndex traversal
 * as {@code MVTResource} so it stays cheap and consistent with the /mvt net layer.
 *
 * The frontend is expected to only call this once zoomed in (like a tiled layer); the
 * server also refuses bboxes larger than {@link #MAX_AREA_DEG2} as a safety cap so a
 * zoomed-out request can't try to serialize the whole country's events.
 *
 * Each event carries its DATEX geometry (shape, WKT) and the QA-enriched caption when
 * the loaded *.json came from a datex2ghevents --include-shape run.
 *
 * This is a prisma-solutions addition (kept in the at.prismasolutions.* namespace so it
 * stays distinct from upstream GraphHopper code across merges).
 */
@Path("events")
public class EventsResource {

    private static final Logger logger = LoggerFactory.getLogger(EventsResource.class);
    /** Refuse bboxes bigger than this (deg^2) - matches "only when zoomed in" on the client. */
    private static final double MAX_AREA_DEG2 = 1.5;
    /** Hard cap so a dense viewport can't flood the browser; logged when hit. */
    private static final int MAX_EVENTS = 25_000;

    private final GraphHopper graphHopper;

    @Inject
    public EventsResource(GraphHopper graphHopper) {
        this.graphHopper = graphHopper;
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

        BBox bbox = parseBBox(bboxStr);
        if (bbox == null || !bbox.isValid())
            throw new IllegalArgumentException("bbox required as 'minLon,minLat,maxLon,maxLat'");
        double area = (bbox.maxLon - bbox.minLon) * (bbox.maxLat - bbox.minLat);
        if (area > MAX_AREA_DEG2)
            // too far zoomed out: nothing rather than a huge payload
            return Response.ok(Collections.emptyList()).header("X-GH-Events", "zoomed-out").build();

        // Same edges-in-bbox traversal as MVTResource; collect the distinct GHEvent
        // instances attached to those edges (one DATEX record spans many edges, so
        // dedupe by identity - getGHEvents returns the shared instances).
        final Set<GHEvent> found = Collections.newSetFromMap(new IdentityHashMap<>());
        LocationIndexTree locationIndex = (LocationIndexTree) graphHopper.getLocationIndex();
        locationIndex.query(bbox, edgeId -> {
            List<GHEvent> evs = mapper.getGHEvents(edgeId);
            if (evs != null && !evs.isEmpty())
                found.addAll(evs);
        });

        List<GHEvent> out = new ArrayList<>(found);
        boolean truncated = false;
        if (out.size() > MAX_EVENTS) {
            logger.info("events bbox {} matched {} events, truncating to {}", bboxStr, out.size(), MAX_EVENTS);
            out = out.subList(0, MAX_EVENTS);
            truncated = true;
        }
        return Response.ok(out).header("X-GH-Events", (truncated ? "truncated:" : "") + out.size()).build();
    }

    private static BBox parseBBox(String s) {
        if (s == null || s.isEmpty())
            return null;
        String[] p = s.split(",");
        if (p.length != 4)
            return null;
        try {
            double minLon = Double.parseDouble(p[0].trim());
            double minLat = Double.parseDouble(p[1].trim());
            double maxLon = Double.parseDouble(p[2].trim());
            double maxLat = Double.parseDouble(p[3].trim());
            return new BBox(minLon, maxLon, minLat, maxLat);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
