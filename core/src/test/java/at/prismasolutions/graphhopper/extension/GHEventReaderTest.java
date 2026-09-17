package at.prismasolutions.graphhopper.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.routing.ev.OSMWayID;
import com.graphhopper.util.Helper;

public class GHEventReaderTest {
	private static final String ghLoc = "./target/tmp/ghosm";
	private static final String testOsm = "./src/test/resources/com/graphhopper/reader/osm/test-osm.xml";
	private static final String testEventsPath = "./src/test/resources/com/graphhopper/reader/GHEvent/";
	private static final String testEvents = testEventsPath + "ghevents.json";
	private static final String testEventsRobust = testEventsPath + "ghevents-robust.json";
	private GraphHopper instance;

	@BeforeEach
	public void setUp() {
		Helper.removeDir(new File(ghLoc));
	}

	@AfterEach
	public void tearDown() {
		if (instance != null)
			instance.close();
		Helper.removeDir(new File(ghLoc));
	}

	@Test
	public void testRead() {
		GHEventReader reader = new GHEventReader();
		File file = new File(testEvents);
		List<GHEvent> events = reader.read(file);
		assertEquals(2, events.size());

		GHEvent event1 = events.get(0);
		GHEvent event2 = events.get(1);

		assertEquals("10", event1.getExtEdgeId());
		assertEquals("11", event2.getExtEdgeId());

		assertEquals(1.5, event1.getFactor());
		assertEquals(-1, event2.getFactor());

		// ISO-8601 dates are parsed into Instants ...
		assertEquals(Instant.from(OffsetDateTime.parse("2022-02-02T14:25:34+02:00")), event1.getStartDate());
		assertEquals(Instant.from(OffsetDateTime.parse("2022-02-14T14:25:34+02:00")), event1.getEndDate());
		// ... and a JSON null leaves the date unset.
		assertNull(event2.getStartDate());
		assertNull(event2.getEndDate());
	}

	@Test
	public void testReadRobust() {
		// A numeric extEdgeId, an object-valued "shape" and unknown object/array fields
		// must not break parsing: the numeric id is normalised to its string key and the
		// structured values are skipped whole so every event is still read.
		GHEventReader reader = new GHEventReader();
		List<GHEvent> events = reader.read(new File(testEventsRobust));
		assertEquals(2, events.size());

		GHEvent event1 = events.get(0);
		GHEvent event2 = events.get(1);

		// numeric extEdgeId is accepted and matches the Long.toString key used for mapping
		assertEquals("12620747", event1.getExtEdgeId());
		assertEquals(-1, event1.getFactor());
		// object-valued shape is skipped, not stored as a broken string
		assertNull(event1.getShape());

		// the event after the structured fields is still parsed intact
		assertEquals("11", event2.getExtEdgeId());
		assertEquals(1.5, event2.getFactor());
	}

	@Test
	public void testManager() {
		String profile = "car_profile";
		GraphHopperWithId hopper = new GraphHopperWithId();
		hopper.setEncodedValuesString(OSMWayID.KEY);
		this.instance = hopper;
		hopper.setStoreOnFlush(true).setProfiles(TestProfiles.constantSpeed(profile))
				.setGraphHopperLocation(ghLoc).setOSMFile(testOsm);
		hopper.getCHPreparationHandler().setCHProfiles(new CHProfile(profile));
		hopper.importOrLoad();
		GHEventManager manager = new GHEventManager(hopper, testEventsPath);
		GHEvent event1 = manager.getMapper().getGHEvents(0).get(0);
		GHEvent event2 = manager.getMapper().getGHEvents(2).get(0);

		assertEquals("10", event1.getExtEdgeId());
		assertEquals("11", event2.getExtEdgeId());

		assertEquals(1.5, event1.getFactor());
		assertEquals(-1, event2.getFactor());
	}
}
