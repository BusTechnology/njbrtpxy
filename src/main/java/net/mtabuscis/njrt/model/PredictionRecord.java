package net.mtabuscis.njrt.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PredictionRecord {

	private String stopNameLong;
	private String stopNameShort;
	private Date arrivalTime;
	
	public Date getArrivalTime() {
		return arrivalTime;
	}
	public void setArrivalTime(Date arrivalTime) {
		this.arrivalTime = arrivalTime;
	}
	public String getStopNameLong() {
		return stopNameLong;
	}
	public void setStopNameLong(String stopNameLong) {
		this.stopNameLong = stopNameLong;
	}
	public String getStopNameShort() {
		return stopNameShort;
	}
	public void setStopNameShort(String stopNameShort) {
		this.stopNameShort = stopNameShort;
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("<");
		sb.append("Stop: ");
		sb.append(this.getStopNameShort());
		sb.append(", Ref: ");
		sb.append(this.getStopNameLong());
		sb.append(", Time: ");
		sb.append(this.getArrivalTime());
		sb.append(">");
		return sb.toString();
	}
	
	public static ArrayList<PredictionRecord> getPredictionRecordsFromVehicle(String data){
		
		ArrayList<PredictionRecord> preds = new ArrayList<PredictionRecord>();
		
		String rr1= "(\\<OnwardCall>(.*?)</OnwardCall>)";
		Pattern p1 = Pattern.compile(rr1, Pattern.DOTALL | Pattern.MULTILINE);
		Matcher matcher = p1.matcher(data);
		while (matcher.find()){
			String onwardCallXml = matcher.group(1);
			PredictionRecord p = new PredictionRecord();
			String longStopName = VehicleActivity.textFromBetween(onwardCallXml, "StopPointName");
			p.setStopNameLong(longStopName);
			String shortStopName = VehicleActivity.textFromBetween(onwardCallXml, "StopPointRef");
			p.setStopNameShort(shortStopName);
			String eta = VehicleActivity.textFromBetween(onwardCallXml, "AimedArrivalTime");
			p.setArrivalTime(VehicleActivity.dtFromIso8601(eta));
			preds.add(p);
		}
		
		return preds;
	}
	
}
