package net.mtabuscis.njrt.gtfs;

import org.onebusaway.geospatial.services.SphericalGeometryLibrary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.mtabuscis.njrt.controller.DataServer;
import net.mtabuscis.njrt.data.DataRetrievalService;
import net.mtabuscis.njrt.model.VehicleActivity;
import net.mtabuscis.njrt.model.VehicleInfoTuple;
import net.mtabuscis.njrt.model.VehicleLocationRecord;
import org.onebusaway.geospatial.model.CoordinatePoint;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.transit_data.model.AgencyWithCoverageBean;
import org.onebusaway.transit_data.model.ListBean;
import org.onebusaway.transit_data.model.StopBean;
import org.onebusaway.transit_data.model.VehicleStatusBean;
import org.onebusaway.transit_data.model.realtime.VehicleLocationRecordBean;
import org.onebusaway.transit_data.services.TransitDataService;
import org.onebusaway.transit_data_federation.impl.shapes.PointAndIndex;
import org.onebusaway.transit_data_federation.impl.transit_graph.StopEntryImpl;
import org.onebusaway.transit_data_federation.impl.transit_graph.StopTimeEntryImpl;
import org.onebusaway.transit_data_federation.impl.transit_graph.TripEntryImpl;
import org.onebusaway.transit_data_federation.model.ShapePoints;
import org.onebusaway.transit_data_federation.services.AgencyAndIdLibrary;
import org.onebusaway.transit_data_federation.services.shapes.ShapePointService;
import org.onebusaway.transit_data_federation.services.transit_graph.StopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TransitGraphDao;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;
import org.onebusaway.transit_data_federation.bundle.tasks.transit_graph.DistanceAlongShapeLibrary;
import org.onebusaway.transit_data_federation.bundle.tasks.transit_graph.DistanceAlongShapeLibrary.DistanceAlongShapeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedHeader.Incrementality;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.FeedMessage.Builder;
import com.google.transit.realtime.GtfsRealtimeConstants;
import me.xdrop.fuzzywuzzy.FuzzySearch;

@Component
public class TransitDataMatcher {

	private static Logger _log = LoggerFactory.getLogger(TransitDataMatcher.class);

	public static final int EXPIRATION_SECONDS = 300;//5 minutes of no record, expire from feed
	
	@Autowired
	private TransitDataService _tds;

	@Autowired
	private TransitGraphDao _tgd;
	
	@Autowired
	private ShapePointService _shapeSvc;
	
	@Autowired 
	private GtfsRtBuilder _gtfsRtBuilder;
	
	@Autowired
	private DataRetrievalService _dataSvc;
	
	@Autowired
	private DataServer _server;
	
	private static HashMap<String, VehicleInfoTuple> _tripCache = new HashMap<String, VehicleInfoTuple>(20000);

	private ArrayList<String> _usedTrips = new ArrayList<String>();
	
	private static final String TMP_FILE_NAME = "/tmp/njb_data.xml";
	
	@Scheduled(cron="*/30 * * * * *")
	public void init(){
		
		_log.info("Starting update from source");
		
		_usedTrips.clear();

		String data = "";
		try {
			_dataSvc.getXmlSiriData(TMP_FILE_NAME);
			//we can have up to 2147483645 characters in a string, during rush hour, we only hit about 10000000 characters which is under this by a lot.
			data = new String(Files.readAllBytes(Paths.get(TMP_FILE_NAME)));//auto closes...
		} catch (IOException e) {
			_log.error("Couldn't retrieve any data.");
			e.printStackTrace();
			return;
		}
		
		FeedMessage.Builder feedBuilder = FeedMessage.newBuilder();
		feedBuilder.clear();//clear the builder
		Date timestamp = null;
		String rr1= "(\\<VehicleMonitoringDelivery(.*?)>(.*?)</VehicleMonitoringDelivery>)";
		Pattern p1 = Pattern.compile(rr1, Pattern.DOTALL | Pattern.MULTILINE);
		Matcher matcher = p1.matcher(data);
		int count = 0;
		
		VehicleActivity va = null;//last one
		while (matcher.find()){
			count++;
			String vehicleXml = matcher.group(1);
			va = VehicleActivity.getFromXML(vehicleXml);
			
			timestamp = va.getReceivedAtTimestamp();
			
			TripEntry trip = findGoodMatchingTrip(va);
			if(trip == null){
				continue;
			}
			
			VehicleLocationRecord vlr = new VehicleLocationRecord();
			vlr.makeRecordFromVehActTrip(va, trip, getDistanceAlongTrip(trip, va));
			VehicleLocationRecordBean vlrb = vlr.getVehicleLocationRecordBean();
			
			_tds.submitVehicleLocation(vlrb);
			
			_usedTrips.add(trip.getId().toString());
			
			//cache vehicle tuple
			StopTimeEntry nextStop = getClosestStop(trip, va.getCoordinates());
			VehicleInfoTuple vit = new VehicleInfoTuple(trip, va, nextStop);
			_tripCache.put(vit.get_vehicle(), vit);
			_log.debug("Putting vehicle position in cache "+vit.get_vehicle());
			FeedEntity positionUpdate = _gtfsRtBuilder.buildGTFSRTVehiclePosition(trip, va);
			_server.updateOrGetVehiclePositions(vit.get_vehicle(), positionUpdate);
		}
		
		
		//only publish vehicles within EXPIRATION_SECONDS
		if(va != null){
			ListBean<VehicleStatusBean> tdsdata = _tds.getAllVehiclesForAgency(va.getOperator(), va.getReceivedAtTimestamp().getTime());
			for(VehicleStatusBean vsb : tdsdata.getList()){
				//only if the last location update is within the expiration interval, should we add this to the feed
				if( (vsb.getLastLocationUpdateTime() + (EXPIRATION_SECONDS*1000) ) > va.getReceivedAtTimestamp().getTime()){
					AgencyAndId veh = AgencyAndId.convertFromString(vsb.getVehicleId());
					_log.debug("Looking to update vehicle "+veh.getId()+" with time "+vsb.getLastLocationUpdateTime());
					VehicleInfoTuple vit = _tripCache.get(veh.getId());
					FeedEntity entity = _gtfsRtBuilder.buildGTFSRTTripUpdate(vit.get_trip(), vit.get_va(), vit.get_nextStop());
					feedBuilder.addEntity(entity);
				}
			}
		}
		
		_log.info("Complete trips: "+count);
		_log.info("Matching trips: "+feedBuilder.getEntityCount());
		
		cacheFeed(feedBuilder, timestamp);
		
		_log.info("Update complete");
	}
	
	
	private TripEntry findGoodMatchingTrip(VehicleActivity va){
		String agency_id = getAgencyId(va);
		if(agency_id == null){
			_log.warn("Error: agency id couldn't be determined for va "+va);
			return null;
		}
		
		//attempt this via the block first...
		ArrayList<TripEntry> blockBasedTrips = getTripsBasedOnBlockId(va);
		if(blockBasedTrips.size() == 0){
			_log.warn("No block id was associated with trip "+String.valueOf(va));
			return null;
		}
		
		int highest = 0;
		TripEntry winner = null;
		for(TripEntry candidate : blockBasedTrips){
			//is there a valid distance along trip?
			Double dat = getDistanceAlongTrip(candidate, va);
			if(dat != null){
				//filter out further based on destination (route may be useful at some point too)
				StopBean candidateDestionation = getLastStop(candidate.getStopTimes());
				_log.debug("Considering trip "+candidate.getId().toString()+"   v "+va.getDestinationName()+"   a "+candidateDestionation.getName()); 
				int ratio = FuzzySearch.ratio(va.getDestinationName(), candidateDestionation.getName());
				if(ratio > highest && !_usedTrips.contains(candidate.getId().toString())){
					highest = ratio;
					winner = candidate;
				}
			}
		}
		return winner;//will contain a route inside
	}
	
	private String getAgencyId(VehicleActivity va){
		//get the gtfs agency
		List<AgencyWithCoverageBean> agencies = _tds.getAgenciesWithCoverage();
		String agency_id = null;
		int highest = 0;
		for(AgencyWithCoverageBean a : agencies){
			int ratio = FuzzySearch.ratio(va.getOperator(), a.getAgency().getId());
			if(ratio > highest){
				agency_id = a.getAgency().getId();
				highest = ratio;
			}
		}
		return agency_id;
	}
	
	private ArrayList<TripEntry> getTripsBasedOnBlockId(VehicleActivity va){
		ArrayList<TripEntry> candidates = new ArrayList<TripEntry>();
		for(TripEntry t : _tgd.getAllTrips()){
			if(t.getBlock().getId().getId().equals(va.getBlockRef())){
				candidates.add(t);
			}
		}
		return candidates;
	}
	
	private StopBean getLastStop(List<StopTimeEntry> stops){
		AgencyAndId lastStopid = stops.get(stops.size()-1).getStop().getId();
		StopBean theLastStop = _tds.getStop(lastStopid.toString());
		return theLastStop;
	}
	
	private Double getDistanceAlongTrip(TripEntry trip, VehicleActivity va){
		DistanceAlongShapeLibrary shapeLib = new DistanceAlongShapeLibrary();
		
		va.getCoordinates();
		
		ShapePoints shapes = _shapeSvc.getShapePointsForShapeId(trip.getShapeId());
		
		List<StopTimeEntryImpl> stoptimes = new ArrayList<StopTimeEntryImpl>();
		stoptimes.add((StopTimeEntryImpl)trip.getStopTimes().get(0));
		
		//hack for reusing the stops library for close shape matching since I couldn't find it in OBA - PJM
		StopTimeEntryImpl vehicle = new StopTimeEntryImpl();
		StopEntryImpl vehiclestop = new StopEntryImpl(AgencyAndIdLibrary.convertFromString(trip.getId().getAgencyId()+"_"+va.getVehicleId()), 
				va.getCoordinates().getLat(), va.getCoordinates().getLon());
		vehicle.setTrip((TripEntryImpl) trip);
		vehicle.setStop(vehiclestop);
		stoptimes.add(vehicle);
		
		try {
			PointAndIndex[] data = shapeLib.getDistancesAlongShape(shapes, stoptimes);
			return data[1].distanceAlongShape;
		} catch (DistanceAlongShapeException e) {
			_log.debug("The vehicle is too far from the shape, skipping  " +String.valueOf(va.toString()));
		}
		
		return null;
	}
	
	private boolean cacheFeed(Builder feedBuilder, Date timestamp){
		if(timestamp != null){
			FeedHeader.Builder header=FeedHeader.newBuilder();
			header.setTimestamp(System.currentTimeMillis()/1000);
			header.setIncrementality(Incrementality.FULL_DATASET);
			header.setGtfsRealtimeVersion(GtfsRealtimeConstants.VERSION);
			feedBuilder.setHeader(header);
			FeedMessage gtfsrt = feedBuilder.build();
			if(gtfsrt.getEntityList().size() > 0){
				_log.info("Updating tripUpdates feed url with "+gtfsrt.getEntityList().size()+" updates.");
				_server.updateOrGetTripUpdates(gtfsrt);
			}else{
				_log.warn("The feed entity list is empty, not updating the feed as a percaution.");
			}
		}
		return false;
	}
	
	//for gtfsrt specifically
	private StopTimeEntry getClosestStop(TripEntry t, CoordinatePoint vehicleLocation){
		//traverse all the trips and figure out what stop we're closest to
		double distance = -1;
		StopTimeEntry closestStop = null;
		for(StopTimeEntry ste : t.getStopTimes()){
			double dis = SphericalGeometryLibrary.distance(ste.getStop().getStopLocation(), vehicleLocation);
			if(distance == -1){
				distance = dis;
				closestStop = ste;
			}else{
				if(dis < distance){
					distance = dis;
					closestStop = ste;
				}
			}
		}
		return closestStop;
	}
	
}
