package ch.bergturbenthal.wisp.manager.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaQuery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ch.bergturbenthal.wisp.manager.model.Connection;
import ch.bergturbenthal.wisp.manager.model.NetworkDevice;
import ch.bergturbenthal.wisp.manager.model.Position;
import ch.bergturbenthal.wisp.manager.model.Station;
import ch.bergturbenthal.wisp.manager.service.AddressManagementService;
import ch.bergturbenthal.wisp.manager.service.StationService;

@Component
@Transactional
public class StationServiceBean implements StationService {
	@Autowired
	private AddressManagementService addressManagementBean;
	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public Station addStation(final Position position) {
		final Station station = new Station();
		station.setPosition(position);
		entityManager.persist(station);
		station.setName("Station-" + station.getId());
		return station;
	}

	@Override
	public Iterable<Connection> findConnectionsOfStation(final long station) {
		final Station stationEntity = findStation(station);
		final List<Connection> ret = new ArrayList<>();
		ret.addAll(stationEntity.getBeginningConnections());
		ret.addAll(stationEntity.getEndingConnections());
		return ret;
	}

	@Override
	public Station findStation(final long id) {
		return entityManager.find(Station.class, Long.valueOf(id));
	}

	@Override
	public Collection<Station> listAllStations() {
		return queryAll(Station.class);
	}

	@Override
	public Station moveStation(final long station, final Position newPosition) {
		final Station storedStation = entityManager.find(Station.class, Long.valueOf(station));
		if (storedStation == null) {
			return null;
		}
		storedStation.setPosition(newPosition);
		entityManager.persist(storedStation);
		return storedStation;
	}

	private <T> Collection<T> queryAll(final Class<T> type) {
		final CriteriaQuery<T> criteriaQuery = entityManager.getCriteriaBuilder().createQuery(type);
		criteriaQuery.from(type);
		return entityManager.createQuery(criteriaQuery).getResultList();
	}

	@Override
	public void removeStation(final Station bean) {
		entityManager.remove(entityManager.find(Station.class, bean.getId()));
	}

	@Override
	public void updateStation(final Station station) {
		if (station.getId() == null) {
			entityManager.persist(station);
		} else {
			entityManager.merge(station);
		}
		if (station.getDevice() != null) {
			final NetworkDevice device = station.getDevice();
			if (device.getId() == null) {
				entityManager.persist(device);
			} else {
				entityManager.merge(device);
			}
		}
	}

}