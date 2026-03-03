package at.prismasolutions.graphhopper.extension;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.DefaultWeightingFactory;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.routing.ev.OSMWayID;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.util.BitUtil;

public class GraphHopperWithId extends GraphHopper {
	// mapping of internal edge ID to OSM way ID
	final BitUtil bitUtil = BitUtil.LITTLE;
	private GHEventManager manager;

	@Override
	public boolean load() {
		boolean loaded = super.load();
		return loaded;
	}

	@Override
	protected WeightingFactory createWeightingFactory() {
		WeightingFactory fact = super.createWeightingFactory();
		if (fact instanceof DefaultWeightingFactory) {
			((DefaultWeightingFactory) fact).setManager(manager);
		}
		return fact;
	}

	public long getWay(int internalEdgeId) {
		return this.getEncodingManager().getLongEncodedValue(OSMWayID.KEY).getLong(false, internalEdgeId,
				this.getBaseGraph().getEdgeAccess());
	}

	public GHEventManager getManager() {
		return manager;
	}

	public void setManager(GHEventManager manager) {
		this.manager = manager;
	}

}
