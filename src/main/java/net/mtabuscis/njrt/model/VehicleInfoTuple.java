package net.mtabuscis.njrt.model;

import org.onebusaway.transit_data_federation.services.transit_graph.StopTimeEntry;
import org.onebusaway.transit_data_federation.services.transit_graph.TripEntry;

//holds onto data about a vehicle for TDS retrieval
public class VehicleInfoTuple {
	
	public String _vehicle;
	public TripEntry _trip;
	public VehicleActivity _va;
	public StopTimeEntry _nextStop;

	public VehicleInfoTuple(TripEntry trip, VehicleActivity va, StopTimeEntry nextStop){
		_vehicle = va.getVehicleId();
		_trip = trip;
		_nextStop = nextStop;
		_va = va;
	}

	public String get_vehicle() {
		return _vehicle;
	}

	public TripEntry get_trip() {
		return _trip;
	}

	public VehicleActivity get_va() {
		return _va;
	}

	public StopTimeEntry get_nextStop() {
		return _nextStop;
	}
	
}
