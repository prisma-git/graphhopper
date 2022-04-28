package at.prismasolutions.graphhopper.extension;

import java.time.Instant;

public class GHEvent {
	private GHEventType type;
	
	private Instant startDate;
	private Instant endDate;
	private int edgeID;
	private short direction;
	private double statFrom;
	private double statTo;

	private String caption;
	private String extType;
	private String extId;
	private String extEdgeId;
	private String shape;
	private String parameterName;
	private Double parameterValue;
	private double factor;
	public GHEventType getType() {
		return type;
	}
	public void setType(GHEventType type) {
		this.type = type;
	}
	public Instant getStartDate() {
		return startDate;
	}
	public void setStartDate(Instant startDate) {
		this.startDate = startDate;
	}
	public Instant getEndDate() {
		return endDate;
	}
	public void setEndDate(Instant endDate) {
		this.endDate = endDate;
	}
	public int getEdgeID() {
		return edgeID;
	}
	public void setEdgeID(int edgeID) {
		this.edgeID = edgeID;
	}
	public short getDirection() {
		return direction;
	}
	public void setDirection(short direction) {
		this.direction = direction;
	}
	public double getStatFrom() {
		return statFrom;
	}
	public void setStatFrom(double statFrom) {
		this.statFrom = statFrom;
	}
	public double getStatTo() {
		return statTo;
	}
	public void setStatTo(double statTo) {
		this.statTo = statTo;
	}
	public String getCaption() {
		return caption;
	}
	public void setCaption(String caption) {
		this.caption = caption;
	}
	public String getExtType() {
		return extType;
	}
	public void setExtType(String extType) {
		this.extType = extType;
	}
	public String getExtId() {
		return extId;
	}
	public void setExtId(String extId) {
		this.extId = extId;
	}
	public String getExtEdgeId() {
		return extEdgeId;
	}
	public void setExtEdgeId(String extEdgeId) {
		this.extEdgeId = extEdgeId;
	}
	public String getShape() {
		return shape;
	}
	public void setShape(String shape) {
		this.shape = shape;
	}
	public String getParameterName() {
		return parameterName;
	}
	public void setParameterName(String parameterName) {
		this.parameterName = parameterName;
	}
	public Double getParameterValue() {
		return parameterValue;
	}
	public void setParameterValue(Double parameterValue) {
		this.parameterValue = parameterValue;
	}
	public double getFactor() {
		return factor;
	}
	public void setFactor(double factor) {
		this.factor = factor;
	}

}
