package at.prismasolutions.graphhopper.extension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;

public abstract class ExtendedWeighting implements Weighting {
	
    protected PMap hints;
    protected GHEventMapper mapper;
    private static final Logger logger = LoggerFactory.getLogger(ExtendedWeighting.class);
    @Override
	public void setHints(PMap hints) {
		this.hints = hints;
	}
    @Override
	public void setGHEventMapper(GHEventMapper mapper) {
		this.mapper = mapper;
    }
    protected double getFactor(EdgeIteratorState edgeState, boolean reverse) {
    	double factor = 1;
    	logger.info("trying to get factor");
    	if(this.mapper != null && this.hints != null) {
    		logger.info("looking for ghEvents");
    		List<GHEvent> events = this.mapper.getGHEvents(edgeState.getEdge());
    		if(events != null) {
    			for(GHEvent event : events) {
    				if(!this.isEventApplicable(event, this.hints, reverse)){
    					continue;
    				}
    				//if one event locks the edge the other events' factor does not matter
    				if(event.getFactor() <0) {
    					return -1;
    				}
    				factor = factor * event.getFactor();
    			}
    		}
    	}
    	return factor;
    }
    private boolean isEventApplicable(GHEvent event, PMap params, boolean reverse) {
    	if(event.getType() == GHEventType.DESCRIPTION) {
    		return false;
    	}
    	short direction = event.getDirection();
		if((direction == 0 && reverse) || (direction == 1 && !reverse) ) {
			return false;
		}
		String startDate = params.getString("start_date", null);
		String endDate = params.getString("end_date", null);
		boolean startsOrEndsInEvent = false;
		if(startDate == null && endDate == null) {
			startsOrEndsInEvent = true;
		}
		if(startDate != null) {
			DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
			OffsetDateTime offsetDateTime = OffsetDateTime.parse(startDate, timeFormatter);
			Instant instant = Instant.from(offsetDateTime);
			if((event.getStartDate() == null || instant.isAfter(event.getStartDate()))
					&&(event.getEndDate() == null || instant.isBefore(event.getEndDate()))) {
				startsOrEndsInEvent = true;
			}
		}
		if(endDate != null) {
			DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
			OffsetDateTime offsetDateTime = OffsetDateTime.parse(endDate, timeFormatter);
			Instant instant = Instant.from(offsetDateTime);
			if((event.getStartDate() == null || instant.isAfter(event.getStartDate()))
					&&(event.getEndDate() == null || instant.isBefore(event.getEndDate()))) {
				startsOrEndsInEvent = true;
			}
		}
		if(!startsOrEndsInEvent) {
			return false;
		}
		
		if (event.getParameterName() != null) {
			String param = params.getString(event.getParameterName(), null);
			if (param != null) {
				switch (event.getType()) {
				case DESCRIPTION:
					return false;
				case EQUAL:
					if (!event.getParameterValue().equals(Double.parseDouble(param))) {
						return false;
					}
					break;
				case GREATERTHAN:
					if (!(event.getParameterValue().compareTo(Double.parseDouble(param))>=0)) {
						return false;
					}
					break;
				case LESSERTHAN:
					if (!(event.getParameterValue().compareTo(Double.parseDouble(param))<=0)) {
						return false;
					}
					break;
				case ALL:
				default:
					break;

				}
			}
		}
		
		return true;		
    }
}
