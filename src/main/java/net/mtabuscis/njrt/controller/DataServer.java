package net.mtabuscis.njrt.controller;

import java.io.IOException;
import java.util.HashMap;

import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.google.transit.realtime.GtfsRealtimeConstants;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader.Incrementality;

@Controller
public class DataServer {

	private static FeedMessage LATEST_TRIPUPS_MESSAGE;
	//vehicle positions should always be very up to date... generate them on the fly with each request *15 seconds*
	private static HashMap<String, FeedEntity> VEHICLE_POSITIONS = new HashMap<String, FeedEntity>(25000);
	
	public synchronized FeedMessage updateOrGetVehiclePositions(String vehicleId, FeedEntity positionUpdate){
		if(vehicleId != null){
			VEHICLE_POSITIONS.put(vehicleId, positionUpdate);
			return null;
		}else{
			FeedMessage.Builder feedBuilder = FeedMessage.newBuilder();
			FeedHeader.Builder header=FeedHeader.newBuilder();
			header.setTimestamp(System.currentTimeMillis()/1000);
			header.setIncrementality(Incrementality.FULL_DATASET);
			header.setGtfsRealtimeVersion(GtfsRealtimeConstants.VERSION);
			feedBuilder.setHeader(header);
			for(FeedEntity ent : VEHICLE_POSITIONS.values()){
				long fiveMinutesAgo = System.currentTimeMillis()-300000L;
				if((ent.getVehicle().getTimestamp()*1000) >= fiveMinutesAgo){
					feedBuilder.addEntity(ent);
				}
			}
			return feedBuilder.build();
		}
	}
	
	//only one person can get this at a time
	public synchronized FeedMessage updateOrGetTripUpdates(FeedMessage m){
		if(m == null){
			return LATEST_TRIPUPS_MESSAGE;
		}else{
			LATEST_TRIPUPS_MESSAGE = m;
		}
		return null;
	}
	
	@RequestMapping(value = "/tripUpdates")
	public void getTripUpdates(HttpServletResponse response) throws IOException{
		this.updateOrGetTripUpdates(null).writeTo(response.getOutputStream());
	}
	
	@RequestMapping(value = "/vehiclePositions")
	public void getVehiclePositions(HttpServletResponse response) throws IOException{
		this.updateOrGetVehiclePositions(null, null).writeTo(response.getOutputStream());
	}
	
	@RequestMapping(value = "/alerts")
	public void getAlerts(HttpServletResponse response) throws IOException{
		//TODO
	}
	
}
