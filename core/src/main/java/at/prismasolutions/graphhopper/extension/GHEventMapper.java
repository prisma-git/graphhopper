package at.prismasolutions.graphhopper.extension;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class GHEventMapper {
	private GraphHopperWithId hopper;
	private GHEvent[] events;
	private Integer[] mapping;
	private List<GHEvent> allEvents;
	

	private GHEventReader reader = new GHEventReader();

	private ReadWriteLock lock = new ReentrantReadWriteLock();
	private Lock writeLock = lock.writeLock();
	private Lock readLock = lock.readLock();

	private final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GHEventMapper.class);
	
	public GHEventMapper(GraphHopperWithId hopper) {
		this.hopper = hopper;
		this.mapping = new Integer[this.hopper.getGraphHopperStorage().getEdges()];
	}
	
	
	
	public GHEvent[] getEvents() {
		return events;
	}

	public Integer[] getMapping() {
		return mapping;
	}



	public List<GHEvent> getAllEvents() {
		return allEvents;
	}



	public List<GHEvent> getGHEvents(int edgeId) {
		readLock.lock();
		try {
			Integer index = mapping[edgeId];
			if (index == null) {
				return null;
			}
			List<GHEvent> ret = new ArrayList<GHEvent>();
			for (int i = index.intValue(); events[i] != null; i++) {
				ret.add(events[i]);
			}
			return ret;
		} catch (Exception e) {
			logger.error("Error getting GHEvents for edge:" + edgeId, e);
			return null;
		} finally {
			readLock.unlock();
		}
	}

	public void initilize(File[] files) {
		List<GHEvent> newEvents = new ArrayList<GHEvent>();
		for (File file : files) {
			newEvents.addAll(this.reader.read(file));
		}
		
		this.createMapping(newEvents);
	}

	private void createMapping(List<GHEvent> newEvents) {
		Map<String, List<GHEvent>> map = new HashMap<String, List<GHEvent>>();
		for (GHEvent ev : newEvents) {
			List<GHEvent> mapped = map.getOrDefault(ev.getExtEdgeId(), new ArrayList<GHEvent>());
			mapped.add(ev);
			map.put(ev.getExtEdgeId(), mapped);
		}

		List<GHEvent> eventArr = new ArrayList<GHEvent>();
		Integer[] mappingArr = new Integer[this.mapping.length];
		int currentIndex = 0;

		for (int i = 0; i < this.mapping.length; i++) {
			Long wayId = hopper.getWay(i);
			if (map.containsKey(wayId.toString())) {
				mappingArr[i] = currentIndex;
				for (GHEvent ev : map.get(wayId.toString())) {
					eventArr.add(ev);
					this.mapping[i] = currentIndex;
					currentIndex++;
				}
				// skip one index to keep null terminator so we know when to stop looking for
				// events
				eventArr.add(null);
				currentIndex++;

			}
		}
		this.writeLock.lock();
		try {
			this.mapping = mappingArr;
			GHEvent[] arr = new GHEvent[eventArr.size()];
			this.events = eventArr.toArray(arr);
			this.allEvents = newEvents;
		} finally {
			this.writeLock.unlock();
		}
	}
}
