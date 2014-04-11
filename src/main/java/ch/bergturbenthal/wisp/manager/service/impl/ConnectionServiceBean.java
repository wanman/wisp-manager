package ch.bergturbenthal.wisp.manager.service.impl;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ch.bergturbenthal.wisp.manager.model.Connection;
import ch.bergturbenthal.wisp.manager.model.Station;
import ch.bergturbenthal.wisp.manager.repository.ConnectionRepository;
import ch.bergturbenthal.wisp.manager.service.ConnectionService;

@Component
@Transactional
public class ConnectionServiceBean implements ConnectionService {
	@Autowired
	private ConnectionRepository connectionRepository;
	@PersistenceContext
	private EntityManager entityManager;

	@Override
	public Connection connectStations(final Station s1, final Station s2) {
		final Station s1Merged = entityManager.merge(s1);
		final Station s2Merged = entityManager.merge(s2);
		final Connection connection = new Connection();
		connection.setStartStation(s1Merged);
		connection.setEndStation(s2Merged);
		s1Merged.getBeginningConnections().add(connection);
		s2Merged.getEndingConnections().add(connection);
		entityManager.persist(connection);
		return connection;
	}

	@Override
	public Iterable<Connection> listAllConnections() {
		return connectionRepository.findAll();
	}

}
