package at.prismasolutions.graphhopper.extension;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.DefaultWeightingFactory;
import com.graphhopper.routing.WeightingFactory;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.util.BitUtil;

public class GraphHopperWithId extends GraphHopper {
	// mapping of internal edge ID to OSM way ID
	private DataAccess edgeMapping;
	final BitUtil bitUtil = BitUtil.LITTLE;
	private GHEventManager manager;

	@Override
	public boolean load() {
		boolean loaded = super.load();

		Directory dir = getGraphHopperStorage().getDirectory();
		edgeMapping = dir.findOrCreate("edge_mapping");

		if (loaded) {
			edgeMapping.loadExisting();
		}

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
		long pointer = 8L * internalEdgeId;
		return bitUtil.combineIntsToLong(edgeMapping.getInt(pointer), edgeMapping.getInt(pointer + 4L));
	}

	public GHEventManager getManager() {
		return manager;
	}

	public void setManager(GHEventManager manager) {
		this.manager = manager;
	}

}
