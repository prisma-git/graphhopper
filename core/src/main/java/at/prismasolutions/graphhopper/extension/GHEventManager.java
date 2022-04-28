package at.prismasolutions.graphhopper.extension;

import java.util.Timer;

public class GHEventManager {
	private static boolean initialised = false;
	private GHEventChangeListener listener;
	private GHEventMapper mapper;
	private static Timer timer;

	public GHEventManager(GraphHopperWithId hopper) {
		if (!initialised) {
			String filepath = System.getenv("GH_EVENT_PATH");
			if(filepath == null || filepath.isEmpty()) {
				filepath = "./ghevents";
			}
			this.mapper = new GHEventMapper(hopper);
			this.listener = new GHEventChangeListener(filepath, this.mapper);
			this.listener.run();
			timer = new Timer();
		    timer.schedule(listener,0,300000);
			initialised = true;
		}
	}
	public GHEventManager(GraphHopperWithId hopper, String path) {
		if (!initialised) {
			String filepath = path;
			this.mapper = new GHEventMapper(hopper);
			this.listener = new GHEventChangeListener(filepath, this.mapper);
			this.listener.run();
			timer = new Timer();
		    timer.schedule(listener,0,300000);
			initialised = true;
		}
	}

	public  GHEventChangeListener getListener() {
		return listener;
	}

	public GHEventMapper getMapper() {
		return mapper;
	}
}
