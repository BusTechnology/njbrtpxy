package net.mtabuscis.njrt.model;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.onebusaway.geospatial.model.CoordinatePoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VehicleActivity {

	private static Logger _log = LoggerFactory.getLogger(VehicleActivity.class);

	private Date recordedAtTimestamp;
	private Date receivedAtTimestamp;
	private Date validUntilTimestamp;
	private String vehicleId;
	private String route;
	private String mode;
	private String operator;
	private String destinationId;
	private String destinationName;
	private String latitude;
	private String longitude;
	private String delay;//from clever
	private String blockRef;//not the GTFS block in NJT!!!
	private ArrayList<PredictionRecord> predictions = new ArrayList<PredictionRecord>();
	
	public Date getReceivedAtTimestamp() {
		return this.receivedAtTimestamp;
	}
	public void setReceivedAtTimestamp(Date receivedAtTimestamp) {
		this.receivedAtTimestamp = receivedAtTimestamp;
	}
	public Date getRecordedAtTimestamp() {
		return recordedAtTimestamp;
	}
	public void setRecordedAtTimestamp(Date recordedAtTimestamp) {
		this.recordedAtTimestamp = recordedAtTimestamp;
	}
	public Date getValidUntilTimestamp() {
		return validUntilTimestamp;
	}
	public void setValidUntilTimestamp(Date validUntilTimestamp) {
		this.validUntilTimestamp = validUntilTimestamp;
	}
	public String getVehicleId() {
		return vehicleId;
	}
	public void setVehicleId(String vehicleId) {
		this.vehicleId = vehicleId;
	}
	public String getRoute() {
		return route;
	}

	public void setRoute(String route) {
		this.route = route;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String getOperator() {
		return operator;
	}

	public void setOperator(String operator) {
		this.operator = operator;
	}

	public String getDestinationId() {
		return destinationId;
	}

	public void setDestinationId(String destinationId) {
		this.destinationId = destinationId;
	}

	public String getDestinationName() {
		return destinationName;
	}

	public void setDestinationName(String destinationName) {
		this.destinationName = destinationName;
	}

	public String getLatitude() {
		return latitude;
	}

	public void setLatitude(String latitude) {
		this.latitude = latitude;
	}

	public String getLongitude() {
		return longitude;
	}

	public void setLongitude(String longitude) {
		this.longitude = longitude;
	}

	public String getBlockRef() {
		return blockRef;
	}

	public void setBlockRef(String blockRef) {
		this.blockRef = blockRef;
	}
	
	public String getDelay() {
		return delay;
	}
	public void setDelay(String delay) {
		this.delay = delay;
	}
	
	public ArrayList<PredictionRecord> getPredictions() {
		return predictions;
	}
	
	public void setPredictions(ArrayList<PredictionRecord> predictions) {
		this.predictions = predictions;
	}

	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("[Vehicle Activity::: VehicleId:");
		sb.append(this.getVehicleId());
		sb.append(", receivedAt:");
		sb.append(this.getReceivedAtTimestamp());
		sb.append(", recordedAt:");
		sb.append(this.getRecordedAtTimestamp());
		sb.append(", validUntil:");
		sb.append(this.getValidUntilTimestamp());
		sb.append(", route:");
		sb.append(this.getRoute());
		sb.append(", mode:");
		sb.append(this.getMode());
		sb.append(", operator:");
		sb.append(this.getOperator());
		sb.append(", destinationId:");
		sb.append(this.getDestinationId());
		sb.append(", destinationName:");
		sb.append(this.getDestinationName());
		sb.append(", latitude:");
		sb.append(this.getLatitude());
		sb.append(", longitude:");
		sb.append(this.getLongitude());
		sb.append(", blockRef:");
		sb.append(this.getBlockRef());
		sb.append(", delay:");
		sb.append(this.getDelay());
		sb.append(", predictions: {");
		if(this.getPredictions() != null){
			for(PredictionRecord p : this.getPredictions()){
				sb.append(p.toString());
			}
		}
		sb.append("} ]");
		return sb.toString();
	}

	public static Date dtFromIso8601(String iso){
		DateFormat df1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
		try {
			Date result1 = df1.parse(iso);
			return result1;
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static String textFromBetween(String input, String tag){
		String rr1= "(\\<"+tag+"(.*?)>(.*?)\\</"+tag+">)";
		Pattern p1 = Pattern.compile(rr1, Pattern.DOTALL | Pattern.MULTILINE);
		Matcher m1 = p1.matcher(input);
		if(m1.find()){
			String d1 = m1.group(1);
			return d1.substring(d1.indexOf(">")+1, d1.lastIndexOf("<"));
		}
		else{
			return "";
		}
	}

	public static VehicleActivity getFromXML(String data){
		VehicleActivity va = new VehicleActivity();
		
		String currentTs = VehicleActivity.textFromBetween(data, "ResponseTimestamp");
		va.setReceivedAtTimestamp(VehicleActivity.dtFromIso8601(currentTs));
		
		String recAtTS = VehicleActivity.textFromBetween(data, "RecordedAtTime");
		va.setRecordedAtTimestamp(VehicleActivity.dtFromIso8601(recAtTS));

		String vuTS = VehicleActivity.textFromBetween(data, "ValidUntilTime");
		va.setValidUntilTimestamp(VehicleActivity.dtFromIso8601(vuTS));

		String veh = VehicleActivity.textFromBetween(data, "VehicleMonitoringRef");
		va.setVehicleId(veh);
		
		String line = VehicleActivity.textFromBetween(data, "LineRef");
		va.setRoute(line);
		
		String mode = VehicleActivity.textFromBetween(data, "VehicleMode");
		va.setMode(mode);
		
		String op = VehicleActivity.textFromBetween(data, "OperatorRef");
		va.setOperator(op);
		
		String dest = VehicleActivity.textFromBetween(data, "DestinationRef");
		va.setDestinationId(dest);
		
		String destn = VehicleActivity.textFromBetween(data, "DestinationName");
		va.setDestinationName(destn);
		
		String lat = VehicleActivity.textFromBetween(data, "Latitude");
		va.setLatitude(lat);
		
		String lon = VehicleActivity.textFromBetween(data, "Longitude");
		va.setLongitude(lon);
		
		String block = VehicleActivity.textFromBetween(data, "BlockRef");
		va.setBlockRef(block);		
		
		String delay = VehicleActivity.textFromBetween(data, "Delay");
		va.setDelay(delay);	
		
		ArrayList<PredictionRecord> predictions = PredictionRecord.getPredictionRecordsFromVehicle(data);
		va.setPredictions(predictions);

		return va;
	}

	public CoordinatePoint getCoordinates(){
		if(this.getLatitude() == null){
			_log.info("Couldn't get the latitude for vehicle "+this.getVehicleId());
			return null;
		}
		if(this.getLongitude() == null){
			_log.info("Couldn't get the longitude for vehicle "+this.getVehicleId());
			return null;
		}
		CoordinatePoint vehicleLocationPoint = new CoordinatePoint(Double.parseDouble(this.getLatitude())
				, Double.parseDouble(this.getLongitude()));
		return vehicleLocationPoint;
	}

	//midnight local time
	public Date getServiceDate(){
		SimpleDateFormat justDay = new SimpleDateFormat("yyyyMMdd");
		Date currentDate = null;
		try {
			currentDate = justDay.parse(justDay.format(this.getRecordedAtTimestamp()));
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return currentDate;
	}


}
