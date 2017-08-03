package net.mtabuscis.njrt.gtfs;

import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.transit_data.model.StopBean;
import org.onebusaway.transit_data.services.TransitDataService;
import org.onebusaway.transit_data_federation.services.AgencyAndIdLibrary;
import org.onebusaway.transit_data_federation.services.transit_graph.StopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.Position;
import com.google.transit.realtime.GtfsRealtime.TripDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate;
import com.google.transit.realtime.GtfsRealtime.VehicleDescriptor;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeEvent;
import com.google.transit.realtime.GtfsRealtime.TripUpdate.StopTimeUpdate;
import com.google.transit.realtime.GtfsRealtime.VehiclePosition;

import net.mtabuscis.njrt.model.PredictionRecord;
import net.mtabuscis.njrt.model.VehicleActivity;

@Component
public class GtfsRtBuilder {

	private static Logger _log = LoggerFactory.getLogger(GtfsRtBuilder.class);
	
	@Autowired
	private TransitDataService _tds;
	
	public FeedEntity buildGTFSRTVehiclePosition(TripEntry trip, VehicleActivity va){
		
		TripDescriptor.Builder tripDescriptor = TripDescriptor.newBuilder();
		tripDescriptor.setTripId(trip.getId().getId());

		VehicleDescriptor.Builder vehicleDescriptor = VehicleDescriptor.newBuilder();
		vehicleDescriptor.setId(va.getVehicleId());

		Position.Builder position = Position.newBuilder();
		position.setLatitude((float) Float.parseFloat(va.getLatitude()));
		position.setLongitude((float) Float.parseFloat(va.getLongitude()));

		VehiclePosition.Builder vehiclePosition = VehiclePosition.newBuilder();
		vehiclePosition.setPosition(position);
		vehiclePosition.setTrip(tripDescriptor);
		vehiclePosition.setVehicle(vehicleDescriptor);
		vehiclePosition.setTimestamp(va.getRecordedAtTimestamp().getTime() / 1000);

		FeedEntity.Builder vehiclePositionEntity = FeedEntity.newBuilder();
		vehiclePositionEntity.setId(va.getVehicleId());
		vehiclePositionEntity.setVehicle(vehiclePosition);

		return vehiclePositionEntity.build();
	}
	
	public FeedEntity buildGTFSRTTripUpdate(TripEntry trip, VehicleActivity va, StopTimeEntry nextStop){
		
		FeedEntity.Builder entity = FeedEntity.newBuilder();
		//build the trip update with the trip id...
		TripUpdate.Builder tripUpdate = TripUpdate.newBuilder();
		long delay = 0;//rolling delay to fill in missing stops
		long lastDepartTime = 0;//make sure nothing goes negative...
		boolean seen = false;
		long nowSeconds = va.getRecordedAtTimestamp().getTime() / 1000;
		_log.debug("First stop is "+nextStop.getStop().getId());
		
		for(StopTimeEntry ste : trip.getStopTimes()){
			_log.debug("Looking at stop "+ste.getStop().getId());
			//only allow predictions for next stop on
			if(!seen){
				if(ste.getStop().getId().equals(nextStop.getStop().getId())){
					seen = true;
				}else{
					continue;//continue until we hit the first stop...
				}
			}
			
			long scheduledDepartureTimeSeconds = ste.getArrivalTime() + (va.getServiceDate().getTime()/1000);
			AgencyAndId stopId = ste.getStop().getId();
			StopBean stop = _tds.getStop(stopId.toString());
			StopTimeUpdate.Builder stub = StopTimeUpdate.newBuilder();
			stub.setStopId(AgencyAndIdLibrary.convertFromString(stop.getId()).getId());
			stub.setScheduleRelationship(StopTimeUpdate.ScheduleRelationship.SCHEDULED);
			stub.setStopSequence(ste.getSequence() + 1);
			StopTimeEvent.Builder se = StopTimeEvent.newBuilder();
			
			boolean filled = false;
			for(PredictionRecord p : va.getPredictions()){
				if(p.getStopNameLong().equalsIgnoreCase(stop.getName())){
					//match to a known prediction time point record
					
					//calculate schedule delay for downstream stops and update the delay
					long predictedDepartureTimeSeconds = (long)p.getArrivalTime().getTime() / 1000;
					delay = scheduledDepartureTimeSeconds - predictedDepartureTimeSeconds;
					
					//no negative stop times...
					if(predictedDepartureTimeSeconds <= lastDepartTime){
						predictedDepartureTimeSeconds = lastDepartTime + 1;
					}
					
					//only future times allowed (depart now as fallback...)
					if(predictedDepartureTimeSeconds <= nowSeconds){
						predictedDepartureTimeSeconds = nowSeconds + 1;
					}
					_log.debug("PTime "+predictedDepartureTimeSeconds+" sq: "+ste.getSequence()+" new delay "+delay);
					se.setTime(predictedDepartureTimeSeconds);//number of seconds since unix epoch
					lastDepartTime = predictedDepartureTimeSeconds;
					StopTimeEvent tim = se.build();
					stub.setDeparture(tim);
					stub.setArrival(tim);
					StopTimeUpdate stopTimeUpdate = stub.build();
					tripUpdate.addStopTimeUpdate(stopTimeUpdate);
					filled = true;
					break;//from inner loop
				}
			}
			if(filled){
				continue;
			}
			
			//if we get here, there was no prediction, fill in the prediction using a schedule delay strategy
			
			//no negative stop times
			long dtime;
			if((scheduledDepartureTimeSeconds + delay) < lastDepartTime){
				dtime = lastDepartTime + 1;
			}else{
				if((scheduledDepartureTimeSeconds + delay) == lastDepartTime){
					dtime = scheduledDepartureTimeSeconds + delay + 1;
				}else{
					dtime = scheduledDepartureTimeSeconds + delay;
				}
			}
			
			//only future times allowed (depart now as fallback...)
			if(dtime <= nowSeconds){
				dtime = nowSeconds + 1;
			}

			_log.debug("Stop Time (nopred) "+dtime+" sq: "+ste.getSequence()+" with delay "+delay);
			
			se.setTime(dtime);
			lastDepartTime = dtime;
			StopTimeEvent tim = se.build();
			stub.setDeparture(tim);
			stub.setArrival(tim);
			StopTimeUpdate stopTimeUpdate = stub.build();
			tripUpdate.addStopTimeUpdate(stopTimeUpdate);
		}
		
		TripDescriptor.Builder tripDescriptor = TripDescriptor.newBuilder();
		tripDescriptor.setRouteId(trip.getRoute().getId().getId());
		tripDescriptor.setTripId(trip.getId().getId());
		tripDescriptor.setScheduleRelationship(TripDescriptor.ScheduleRelationship.SCHEDULED);
		tripUpdate.setTrip(tripDescriptor.build());
		VehicleDescriptor.Builder vehicle = VehicleDescriptor.newBuilder();
		vehicle.setId(va.getVehicleId());
		tripUpdate.setVehicle(vehicle.build());
		tripUpdate.setTimestamp(nowSeconds);
		TripUpdate ftu = tripUpdate.build();
		entity.setTripUpdate(ftu);
		entity.setId(trip.getId().getId());//use trip id
		FeedEntity ret = entity.build();
		return ret;
	}

}
