package at.prismasolutions.graphhopper.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.Helper;

public class GHEventReaderTest {
	private static final String ghLoc = "./target/tmp/ghosm";
	private static final String testOsm = "./src/test/resources/com/graphhopper/reader/osm/test-osm.xml";
	private static final String testEventsPath = "./src/test/resources/com/graphhopper/reader/GHEvent/";
	private static final String testEvents = testEventsPath+"ghevents.json";
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
	}

	@Test
	public void testManager() {
		String profile = "car_profile";
		String vehicle = "car";
		String weighting = "fastest";
		GraphHopperWithId hopper = new GraphHopperWithId();
		this.instance = hopper;
		hopper.setStoreOnFlush(true).setProfiles(new Profile(profile).setVehicle(vehicle).setWeighting(weighting))
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
