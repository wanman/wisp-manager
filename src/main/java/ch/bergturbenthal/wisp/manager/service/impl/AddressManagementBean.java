package ch.bergturbenthal.wisp.manager.service.impl;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CompositeIterator;

import ch.bergturbenthal.wisp.manager.model.Antenna;
import ch.bergturbenthal.wisp.manager.model.Connection;
import ch.bergturbenthal.wisp.manager.model.CustomerConnection;
import ch.bergturbenthal.wisp.manager.model.DHCPSettings;
import ch.bergturbenthal.wisp.manager.model.GatewaySettings;
import ch.bergturbenthal.wisp.manager.model.GlobalDnsServer;
import ch.bergturbenthal.wisp.manager.model.IpAddress;
import ch.bergturbenthal.wisp.manager.model.IpIpv6Tunnel;
import ch.bergturbenthal.wisp.manager.model.IpNetwork;
import ch.bergturbenthal.wisp.manager.model.IpRange;
import ch.bergturbenthal.wisp.manager.model.NetworkDevice;
import ch.bergturbenthal.wisp.manager.model.NetworkInterface;
import ch.bergturbenthal.wisp.manager.model.NetworkInterfaceRole;
import ch.bergturbenthal.wisp.manager.model.RangePair;
import ch.bergturbenthal.wisp.manager.model.Station;
import ch.bergturbenthal.wisp.manager.model.VLan;
import ch.bergturbenthal.wisp.manager.model.address.AddressRangeType;
import ch.bergturbenthal.wisp.manager.model.address.IpAddressType;
import ch.bergturbenthal.wisp.manager.model.devices.NetworkDeviceModel;
import ch.bergturbenthal.wisp.manager.model.devices.NetworkInterfaceType;
import ch.bergturbenthal.wisp.manager.repository.AntennaRepository;
import ch.bergturbenthal.wisp.manager.repository.ConnectionRepository;
import ch.bergturbenthal.wisp.manager.repository.DnsServerRepository;
import ch.bergturbenthal.wisp.manager.repository.IpIpv6TunnelRepository;
import ch.bergturbenthal.wisp.manager.repository.IpRangeRepository;
import ch.bergturbenthal.wisp.manager.repository.NetworkDeviceRepository;
import ch.bergturbenthal.wisp.manager.repository.StationRepository;
import ch.bergturbenthal.wisp.manager.repository.VLanRepository;
import ch.bergturbenthal.wisp.manager.service.AddressManagementService;
import ch.bergturbenthal.wisp.manager.util.CrudRepositoryContainer;

import com.vaadin.data.Container;

@Slf4j
@Component
@Transactional
public class AddressManagementBean implements AddressManagementService {

	private static class IpRangeCrudContainer extends CrudRepositoryContainer<IpRange, Long> implements Container.Hierarchical {
		private final IpRangeRepository repository;

		private IpRangeCrudContainer(final IpRangeRepository repository, final Class<IpRange> entityType) {
			super(repository, entityType);
			this.repository = repository;
		}

		@Override
		public boolean areChildrenAllowed(final Object itemId) {
			return true;
		}

		@Override
		public Collection<?> getChildren(final Object itemId) {
			final IpRange ipRange = loadPojo(itemId);
			final ArrayList<Long> childrenIds = new ArrayList<Long>();
			for (final IpRange reservationRange : ipRange.getReservations()) {
				childrenIds.add(reservationRange.getId());
			}
			return childrenIds;
		}

		@Override
		public Object getParent(final Object itemId) {
			final IpRange parentRange = loadPojo(itemId).getParentRange();
			if (parentRange == null) {
				return null;
			}
			return parentRange.getId();
		}

		@Override
		public boolean hasChildren(final Object itemId) {
			final Collection<IpRange> reservations = loadPojo(itemId).getReservations();
			return reservations != null && !reservations.isEmpty();
		}

		@Override
		protected Long idFromValue(final IpRange entry) {
			return entry.getId();
		}

		@Override
		public boolean isRoot(final Object itemId) {
			final IpRange ipRange = loadPojo(itemId);
			return ipRange == null || ipRange.getParentRange() == null;
		}

		private IpRange loadPojo(final Object itemId) {
			return repository.findOne((Long) itemId);
		}

		@Override
		public Collection<?> rootItemIds() {
			final ArrayList<Long> ret = new ArrayList<Long>();
			for (final IpRange range : repository.findAllRootRanges()) {
				ret.add(range.getId());
			}
			return ret;
		}

		@Override
		public boolean setChildrenAllowed(final Object itemId, final boolean areChildrenAllowed) throws UnsupportedOperationException {
			return false;
		}

		@Override
		public boolean setParent(final Object itemId, final Object newParentId) throws UnsupportedOperationException {
			return false;
		}
	}

	@Autowired
	private AntennaRepository antennaRepository;
	@Autowired
	private ConnectionRepository connectionRepository;
	@Autowired
	private DnsServerRepository dnsServerRepository;
	@Autowired
	private IpIpv6TunnelRepository ipIpv6TunnelRepository;
	@Autowired
	private IpRangeRepository ipRangeRepository;
	@Autowired
	private NetworkDeviceRepository networkDeviceRepository;
	@Autowired
	private StationRepository stationRepository;
	@Autowired
	private VLanRepository vLanRepository;

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.wisp.manager.service.impl.AddressManagementService#addGlobalDns(ch.bergturbenthal.wisp.manager.model.IpAddress)
	 */
	@Override
	public void addGlobalDns(final IpAddress address) {
		dnsServerRepository.save(new GlobalDnsServer(address));
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.wisp.manager.service.impl.AddressManagementService#addRootRange(java.net.InetAddress, int, int, java.lang.String)
	 */
	@Override
	public IpRange addRootRange(final InetAddress rangeAddress, final int rangeMask, final int reservationMask, final String comment) {
		if (reservationMask < rangeMask) {
			throw new IllegalArgumentException("Error to create range for " + rangeAddress
																					+ "/"
																					+ rangeMask
																					+ ": reservationMask ("
																					+ reservationMask
																					+ ") mask must be greater or equal than range mask ("
																					+ rangeMask
																					+ ")");
		}
		final IpNetwork reserveNetwork = new IpNetwork(new IpAddress(rangeAddress), rangeMask);
		final IpRange foundParentNetwork = findParentRange(reserveNetwork);
		if (foundParentNetwork != null) {
			throw new IllegalArgumentException("new range " + reserveNetwork + " overlaps with existsing " + foundParentNetwork);
		}
		final IpRange reservationRange = new IpRange(reserveNetwork, reservationMask, AddressRangeType.ROOT);
		reservationRange.setComment(comment);
		ipRangeRepository.save(reservationRange);
		return reservationRange;
	}

	private VLan appendVlan(final int vlanId, final RangePair parentAddresses) {
		final RangePair address = new RangePair();
		final VLan vLan = new VLan();
		vLan.setVlanId(Integer.valueOf(vlanId));
		if (parentAddresses.getV4Address() != null) {
			address.setV4Address(reserveRange(parentAddresses.getV4Address(), AddressRangeType.ASSIGNED, 32, null));
		}
		if (parentAddresses.getV6Address() != null) {
			address.setV6Address(reserveRange(parentAddresses.getV6Address(), AddressRangeType.ASSIGNED, 128, null));
		}
		vLan.setAddress(address);
		return vLan;
	}

	private void assignGateway(final NetworkInterface networkInterface, final GatewaySettings gatewaySettings) {
		networkInterface.setGatewaySettings(gatewaySettings);
		networkInterface.setRole(NetworkInterfaceRole.GATEWAY);
		networkInterface.setInterfaceName("Gateway " + gatewaySettings.getGatewayName());
	}

	private void clearIntermediateParent(final IpRange parentRange) {
		if (parentRange == null) {
			return;
		}
		if (parentRange.getType() != AddressRangeType.INTERMEDIATE) {
			return;
		}
		if (parentRange.getReservations().size() > 1) {
			return;
		}
		clearIntermediateParent(parentRange.getParentRange());
		ipRangeRepository.delete(parentRange);
	}

	@Override
	public CrudRepositoryContainer<IpRange, Long> createIpContainer() {
		return new IpRangeCrudContainer(ipRangeRepository, IpRange.class);
	}

	@Override
	public String describeRangeUser(final IpRange ipRange) {
		final VLan foundVlan = vLanRepository.findVlanByRange(ipRange);
		if (foundVlan != null) {
			final NetworkInterface networkInterface = foundVlan.getNetworkInterface();
			if (networkInterface != null) {
				final NetworkDevice networkDevice = networkInterface.getNetworkDevice();
				return "Network-Device: " + networkDevice.getTitle() + "; " + networkInterface.getInterfaceName() + ";" + foundVlan.getVlanId();
			}
			final CustomerConnection customerConnection = foundVlan.getCustomerConnection();
			if (customerConnection != null) {
				final Station station = customerConnection.getStation();
				return "Station Network: " + station.getName() + ";" + customerConnection.getName() + ";" + foundVlan.getVlanId();
			}
		}
		final Antenna foundAntenna = antennaRepository.findAntennaForRange(ipRange);
		if (foundAntenna != null) {
			return "Antenna: " + foundAntenna.getTitle();
		}
		// final Connection foundConnection = connectionRepository.findConnectionForRange(ipRange);
		// if (foundConnection != null) {
		// return "Connection: " + foundConnection.getTitle();
		// }
		final NetworkDevice foundNetworkDevice = networkDeviceRepository.findDeviceForRange(ipRange);
		if (foundNetworkDevice != null) {
			return "Network-Device: " + foundNetworkDevice.getTitle();
		}
		final Station stationLoopback = stationRepository.findStationLoopbackForRange(ipRange);
		if (stationLoopback != null) {
			return "Station Loopback: " + stationLoopback.getName();
		}
		final CustomerConnection customerNetwork = stationRepository.findStationNetworkForRange(ipRange);
		if (customerNetwork != null) {
			return "Customer Network: " + customerNetwork.getName();
		}
		return null;
	}

	private <T> List<T> emptyIfNull(final List<T> collection) {
		if (collection == null) {
			return java.util.Collections.emptyList();
		}
		return collection;
	}

	private <T> Set<T> ensureMutableSet(final Set<T> set) {
		if (set == null) {
			return new HashSet<>();
		}
		return set;
	}

	private void fillAntenna(final Antenna antenna, final RangePair parentAddresses) {
		if (antenna.getAddresses() == null) {
			antenna.setAddresses(new RangePair());
		}
		final RangePair rangePair = antenna.getAddresses();
		if (rangePair.getV4Address() == null) {
			rangePair.setV4Address(reserveRange(parentAddresses.getV4Address(), AddressRangeType.ASSIGNED, 32, null));
		}
		if (rangePair.getV6Address() == null) {
			rangePair.setV6Address(reserveRange(parentAddresses.getV6Address(), AddressRangeType.ASSIGNED, 128, null));
		}
	}

	private void fillLanIfNone(final Station station) {
		final Set<CustomerConnection> customerConnections;
		if (station.getCustomerConnections() == null) {
			customerConnections = new HashSet<CustomerConnection>();
		} else {
			customerConnections = station.getCustomerConnections();
		}
		for (final CustomerConnection customerConnection : customerConnections) {
			Set<VLan> customerNetworks;
			if (customerConnection.getOwnNetworks() == null) {
				customerNetworks = new HashSet<VLan>();
			} else {
				customerNetworks = customerConnection.getOwnNetworks();
			}
			if (customerNetworks.isEmpty()) {
				// add vlan 0 if none defined
				final VLan vLan = new VLan();
				vLan.setVlanId(0);
				vLan.setCustomerConnection(customerConnection);
				customerNetworks.add(vLan);
			}
			for (final VLan vLan : VLan.sortVLans(customerNetworks)) {
				final RangePair address;
				if (vLan.getAddress() == null) {
					address = new RangePair();
				} else {
					address = vLan.getAddress();
				}
				final boolean addDhcpSettings = address.getV4Address() == null;
				fillRangePair(address, AddressRangeType.USER, 25, 32, 64, 128, null);
				if (addDhcpSettings) {
					final IpRange v4AddressRange = address.getV4Address();
					final DHCPSettings dhcpSettings = new DHCPSettings();
					dhcpSettings.setLeaseTime(Long.valueOf(TimeUnit.MINUTES.toMillis(30)));
					dhcpSettings.setStartIp(new IpAddress(v4AddressRange.getRange().getAddress().getAddressOfNetwork(20)));
					dhcpSettings.setEndIp(new IpAddress(v4AddressRange.getRange().getAddress().getAddressOfNetwork(100)));
					vLan.setDhcpSettings(dhcpSettings);
				}
				vLan.setAddress(address);
			}
			customerConnection.setOwnNetworks(customerNetworks);
		}
		station.setCustomerConnections(customerConnections);
	}

	private void fillLoopbackAddress(final Station station) {
		final RangePair loopback;
		if (station.getLoopback() == null) {
			loopback = new RangePair();
		} else {
			loopback = station.getLoopback();
		}
		fillRangePair(loopback, AddressRangeType.LOOPBACK, 32, 32, 128, 128, "Station " + station.getName());
		station.setLoopback(loopback);
	}

	private void fillNetworkDevice(final Station station) {
		final NetworkDevice networkDevice = station.getDevice();
		if (networkDevice == null) {
			return;
		}
		final Collection<IpAddress> dnsServers = listGlobalDnsServers();
		final Set<IpAddress> dnsServersOfDevice = ensureMutableSet(networkDevice.getDnsServers());
		dnsServersOfDevice.retainAll(dnsServers);
		dnsServersOfDevice.addAll(dnsServers);
		networkDevice.setDnsServers(dnsServersOfDevice);
		// collect unassigned interfaces and connections at this station
		final Set<CustomerConnection> remainingCustomerConnections = new HashSet<CustomerConnection>(station.getCustomerConnections());
		// collect unassigned gateway settings
		final Set<GatewaySettings> remainingGatewaySettings = new HashSet<GatewaySettings>();
		for (final GatewaySettings gateway : station.getGatewaySettings()) {
			switch (gateway.getGatewayType()) {
			case LAN:
			case PPPOE:
				remainingGatewaySettings.add(gateway);
				break;
			default:
				break;
			}
		}
		final List<NetworkInterface> freeInterfaces = new ArrayList<>();
		final Set<NetworkInterface> userAssignedInterfaces = new HashSet<>();
		for (final NetworkInterface networkInterface : emptyIfNull(networkDevice.getInterfaces())) {
			if (networkInterface.getType() != NetworkInterfaceType.LAN) {
				continue;
			}
			final GatewaySettings gatewaySettings = networkInterface.getGatewaySettings();
			if (gatewaySettings != null) {
				switch (gatewaySettings.getGatewayType()) {
				case LAN:
				case PPPOE:
					remainingGatewaySettings.remove(gatewaySettings);
					networkInterface.getNetworks().clear();
					assignGateway(networkInterface, gatewaySettings);
					continue;
				case HE:
					// remove -> HE needs no physical interface
					networkInterface.setGatewaySettings(null);
					break;
				default:
				}
			}
			final Set<VLan> networks = networkInterface.getNetworks();
			if (networks == null || networks.isEmpty()) {
				// no connection -> free interface
				freeInterfaces.add(networkInterface);
				networkInterface.setRole(NetworkInterfaceRole.UNDEFINED);
			} else {
				// find connections and stations for this interface
				final Set<CustomerConnection> foundCustomerConnections = new HashSet<>();
				for (final VLan vLan : VLan.sortVLans(networks)) {
					final RangePair connectionAddress = vLan.getAddress();
					if (connectionAddress == null) {
						continue;
					}
					if (connectionAddress.getV4Address() != null) {
						final IpRange parentRange = connectionAddress.getV4Address().getParentRange();
						foundCustomerConnections.add(stationRepository.findStationNetworkForRange(parentRange));
						vLanRepository.findVlanByRange(parentRange);
					}
					if (connectionAddress.getV6Address() != null) {
						final IpRange parentRange = connectionAddress.getV6Address().getParentRange();
						foundCustomerConnections.add(stationRepository.findStationNetworkForRange(parentRange));
					}
				}
				// remove not found entries
				foundCustomerConnections.remove(null);
				if (foundCustomerConnections.isEmpty()) {
					// unassigned interface
					freeInterfaces.add(networkInterface);
					networkInterface.setRole(NetworkInterfaceRole.UNDEFINED);
					continue;
				}
				if (!foundCustomerConnections.isEmpty()) {
					for (final CustomerConnection foundCustomerConnection : foundCustomerConnections) {
						if (station.equals(foundCustomerConnection.getStation())) {
							// locally connected interface
							networkInterface.setInterfaceName(makeInterfaceName(foundCustomerConnection));
							networkInterface.setRole(NetworkInterfaceRole.NETWORK);
							userAssignedInterfaces.add(networkInterface);
							remainingCustomerConnections.remove(foundCustomerConnection);
						}
					}
					continue;
				}
				// every other case is inconsistent and will be cleaned
				log.warn("Inconsistent connection at interface " + networkInterface + " cleaning");
				networkInterface.getNetworks().clear();
				freeInterfaces.add(networkInterface);
				networkInterface.setRole(NetworkInterfaceRole.UNDEFINED);
			}
		}
		// assign remaining interfaces to connections
		final Iterator<NetworkInterface> freeInterfacesIterator = freeInterfaces.iterator();
		final Iterator<GatewaySettings> remainingGatewayIterator = remainingGatewaySettings.iterator();
		while (freeInterfacesIterator.hasNext() && remainingGatewayIterator.hasNext()) {
			final NetworkInterface networkInterface = freeInterfacesIterator.next();
			final GatewaySettings gatewaySettings = remainingGatewayIterator.next();
			assignGateway(networkInterface, gatewaySettings);
		}

		final Iterator<CustomerConnection> remainingCustomerConnectionsIterator = remainingCustomerConnections.iterator();
		while (freeInterfacesIterator.hasNext() && remainingCustomerConnectionsIterator.hasNext()) {
			final NetworkInterface networkInterface = freeInterfacesIterator.next();
			// setup customer connections
			final CustomerConnection customerConnection = remainingCustomerConnectionsIterator.next();
			final Set<VLan> ownNetworks = customerConnection.getOwnNetworks();
			if (ownNetworks != null && !ownNetworks.isEmpty()) {
				final Set<VLan> networks = ensureMutableSet(networkInterface.getNetworks());
				final Map<Integer, VLan> networksByVlan = orderNetworksByVlan(networks);
				for (final VLan vlan : VLan.sortVLans(ownNetworks)) {
					final VLan deviceVlan = networksByVlan.get(vlan.getVlanId());
					if (deviceVlan != null) {
						final RangePair deviceAddressPair = deviceVlan.getAddress();
						final RangePair stationAddressPair = vlan.getAddress();
						if (deviceAddressPair.getV4Address().getParentRange() == stationAddressPair.getV4Address() && deviceAddressPair.getV6Address().getParentRange() == stationAddressPair.getV6Address()) {
							if (vlan.getDhcpSettings() != null) {
								deviceVlan.setDhcpSettings(vlan.getDhcpSettings());
							}
							// keep if settings are valid
							continue;
						} else {
							// address-data invalid -> remove and renew
							networks.remove(deviceVlan);
						}
					}
					final VLan ifaceVlan = appendVlan(vlan.getVlanId(), vlan.getAddress());
					networks.add(ifaceVlan);
					ifaceVlan.setNetworkInterface(networkInterface);
					if (vlan.getDhcpSettings() != null) {
						ifaceVlan.setDhcpSettings(vlan.getDhcpSettings());
					}
				}
			}
			networkInterface.setInterfaceName(makeInterfaceName(customerConnection));
			networkInterface.setRole(NetworkInterfaceRole.NETWORK);
			userAssignedInterfaces.add(networkInterface);
		}
		int ifNumber = 1;
		while (freeInterfacesIterator.hasNext()) {
			final NetworkInterface networkInterface = freeInterfacesIterator.next();
			final Set<VLan> networks = ensureMutableSet(networkInterface.getNetworks());
			if (networks.isEmpty()) {
				final IpRange v4AddressRange = findAndReserveAddressRange(AddressRangeType.CONNECTION, IpAddressType.V4, 29, 32, AddressRangeType.ASSIGNED, "");
				final IpRange v6AddressRange = findAndReserveAddressRange(AddressRangeType.CONNECTION, IpAddressType.V6, 64, 128, AddressRangeType.ASSIGNED, "");
				final RangePair rangePair = new RangePair(v4AddressRange, v6AddressRange);
				final VLan vLan = new VLan();
				vLan.setAddress(rangePair);
				networks.add(vLan);
				vLan.setNetworkInterface(networkInterface);
				networkInterface.setNetworks(networks);
			}
			// TODO Assign new ranges and set a title on every connection
			// final VLan vLan = appendVlan(0, connection.getAddresses());
			//
			// vLan.setNetworkInterface(networkInterface);
			// networks.add(vLan);
			// networkInterface.setNetworks(networks);
			networkInterface.setRole(NetworkInterfaceRole.ROUTER_LINK);
			networkInterface.setInterfaceName("Station-Connection-" + (ifNumber++));
		}

		fillTunnels(station, networkDevice);
	}

	private void fillRangePair(	final RangePair pair,
															final AddressRangeType rangeType,
															final int v4Netmask,
															final int v4NextMask,
															final int v6Netmask,
															final int v6NextMask,
															final String comment) {
		if (pair.getV4Address() == null) {
			pair.setV4Address(findAndReserveAddressRange(rangeType, IpAddressType.V4, v4Netmask, v4NextMask, AddressRangeType.ASSIGNED, comment));
		}
		if (pair.getV6Address() == null) {
			pair.setV6Address(findAndReserveAddressRange(rangeType, IpAddressType.V6, v6Netmask, v6NextMask, AddressRangeType.ASSIGNED, comment));
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.wisp.manager.service.impl.AddressManagementService#fillStation(ch.bergturbenthal.wisp.manager.model.Station)
	 */
	@Override
	public Station fillStation(final Station station) {
		fillLoopbackAddress(station);
		fillLanIfNone(station);
		fillNetworkDevice(station);
		return station;
	}

	private void fillTunnels(final Station station, final NetworkDevice networkDevice) {
		// setup available tunnel connections
		final HashSet<Station> foreignTunnelStations;
		if (station.isTunnelConnection()) {
			foreignTunnelStations = new HashSet<Station>();
			for (final Station tunnelStation : stationRepository.findAll()) {
				foreignTunnelStations.add(tunnelStation);
			}
		} else {
			foreignTunnelStations = new HashSet<Station>(stationRepository.findTunnelConnectionStations());
		}
		foreignTunnelStations.remove(station);
		final Map<Station, IpIpv6Tunnel> configuredTunnels = new HashMap<Station, IpIpv6Tunnel>();
		for (final IpIpv6Tunnel tunnel : networkDevice.getTunnelBegins()) {
			final Station partnerStation = tunnel.getEndDevice().getStation();
			if (partnerStation == null) {
				ipIpv6TunnelRepository.delete(tunnel);
			} else {
				configuredTunnels.put(partnerStation, tunnel);
			}
		}
		for (final IpIpv6Tunnel tunnel : networkDevice.getTunnelEnds()) {
			final Station partnerStation = tunnel.getStartDevice().getStation();
			if (partnerStation == null) {
				ipIpv6TunnelRepository.delete(tunnel);
			} else {
				configuredTunnels.put(partnerStation, tunnel);
			}
		}
		for (final Station tunnelPartnerStation : foreignTunnelStations) {
			final NetworkDevice partnerDevice = tunnelPartnerStation.getDevice();
			if (partnerDevice == null) {
				continue;
			}
			final IpIpv6Tunnel existingTunnel = configuredTunnels.remove(tunnelPartnerStation);
			if (existingTunnel == null) {
				final IpIpv6Tunnel tunnel = new IpIpv6Tunnel();
				tunnel.setStartDevice(networkDevice);
				tunnel.setEndDevice(partnerDevice);
				final IpRange tunnelAddressRange = findAndReserveAddressRange(AddressRangeType.TUNNEL, IpAddressType.V4, 30, 32, AddressRangeType.ASSIGNED, null);
				tunnel.setV4Address(tunnelAddressRange);
				networkDevice.getTunnelBegins().add(tunnel);
				partnerDevice.getTunnelEnds().add(tunnel);
				ipIpv6TunnelRepository.save(tunnel);
			}
		}
		for (final IpIpv6Tunnel tunnel : configuredTunnels.values()) {
			ipIpv6TunnelRepository.delete(tunnel);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.wisp.manager.service.impl.AddressManagementService#findAllRootRanges()
	 */
	@Override
	public List<IpRange> findAllRootRanges() {
		return ipRangeRepository.findAllRootRanges();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * ch.bergturbenthal.wisp.manager.service.impl.AddressManagementService#findAndReserveAddressRange(ch.bergturbenthal.wisp.manager.model.address.
	 * AddressRangeType, ch.bergturbenthal.wisp.manager.model.address.IpAddressType, int, int,
	 * ch.bergturbenthal.wisp.manager.model.address.AddressRangeType, java.lang.String)
	 */
	@Override
	public IpRange findAndReserveAddressRange(final AddressRangeType rangeType,
																						final IpAddressType addressType,
																						final int maxNetSize,
																						final int nextDistributionSize,
																						final AddressRangeType typeOfReservation,
																						final String comment) {
		final IpRange parentRange = findMatchingRange(rangeType, addressType, maxNetSize);
		if (parentRange == null) {
			return null;
		}
		return reserveRange(parentRange, typeOfReservation == null ? AddressRangeType.ASSIGNED : typeOfReservation, nextDistributionSize, comment);
	}

	private IpRange findMatchingRange(final AddressRangeType rangeType, final IpAddressType addressType, final int maxNetSize) {
		for (final IpRange range : ipRangeRepository.findMatchingRange(rangeType, addressType, maxNetSize)) {
			if (range.getAvailableReservations() <= range.getReservations().size()) {
				// range full
				continue;
			}
			return range;
		}
		// no matching range found
		return null;
	}

	private IpRange findParentRange(final IpNetwork reserveNetwork) {
		return findParentRange(reserveNetwork, findAllRootRanges());
	}

	private IpRange findParentRange(final IpNetwork reserveNetwork, final Collection<IpRange> collection) {
		for (final IpRange range : collection) {
			final IpNetwork checkNetwork = range.getRange();
			if (overlap(checkNetwork, reserveNetwork)) {
				final IpRange subRange = findParentRange(reserveNetwork, range.getReservations());
				if (subRange != null) {
					return subRange;
				}
				return range;
			}
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.wisp.manager.service.impl.AddressManagementService#initAddressRanges()
	 */
	@Override
	public void initAddressRanges() {
		try {
			final List<IpRange> resultList = findAllRootRanges();
			// log.info("Ranges: " + resultList);
			if (resultList.isEmpty()) {
				final IpRange ipV6GlobalReservationRange = addRootRange(Inet6Address.getByName("2001:1620:bba::"), 48, 56, "Global v6 Range");
				reserveRange(ipV6GlobalReservationRange, AddressRangeType.USER, 64, "User Ranges");

				final IpRange ipV4ReservationRange = addRootRange(Inet4Address.getByName("172.16.0.0"), 12, 16, "Internal v4 Range");
				final IpRange smallV4Ranges = reserveRange(ipV4ReservationRange, AddressRangeType.ADMINISTRATIVE, 24, "Some small Ranges");
				reserveRange(smallV4Ranges, AddressRangeType.LOOPBACK, 32, null);
				for (int i = 0; i < 3; i++) {
					reserveRange(smallV4Ranges, AddressRangeType.CONNECTION, 29, null);
				}
				reserveRange(smallV4Ranges, AddressRangeType.TUNNEL, 30, "IpIpv6-Tunnels");
				reserveRange(ipV4ReservationRange, AddressRangeType.USER, 24, null);
				final IpRange ipV6SiteLocalReservationRange = addRootRange(Inet6Address.getByName("fd7e:907d:34ab::"), 48, 56, "Internal v6 Range");
				final IpRange singleRanges = reserveRange(ipV6SiteLocalReservationRange, AddressRangeType.ADMINISTRATIVE, 64, "Ranges for single addresses");
				reserveRange(singleRanges, AddressRangeType.LOOPBACK, 128, null);
				reserveRange(ipV6SiteLocalReservationRange, AddressRangeType.CONNECTION, 64, null);
				// reserveRange(ipV6SiteLocalReservationRange, AddressRangeType.USER, 64, null);
			}
			if (listGlobalDnsServers().isEmpty()) {
				addGlobalDns(new IpAddress(InetAddress.getByName("8.8.8.8")));
				addGlobalDns(new IpAddress(InetAddress.getByName("2001:4860:4860::8888")));
			}
		} catch (final UnknownHostException e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.wisp.manager.service.impl.AddressManagementService#listGlobalDnsServers()
	 */
	@Override
	public Collection<IpAddress> listGlobalDnsServers() {
		final Collection<IpAddress> ret = new ArrayList<>();
		for (final GlobalDnsServer server : dnsServerRepository.findAll()) {
			ret.add(server.getAddress());
		}
		return ret;
	}

	@Override
	public Iterable<InetAddress> listPossibleNetworkDevices() {
		return new Iterable<InetAddress>() {

			@Override
			public Iterator<InetAddress> iterator() {
				final CompositeIterator<InetAddress> compositeIterator = new CompositeIterator<InetAddress>();
				final Collection<InetAddress> defaultAddresses = new HashSet<InetAddress>();
				for (final NetworkDeviceModel model : NetworkDeviceModel.values()) {
					defaultAddresses.add(model.getFactoryDefaultAddress());
				}
				compositeIterator.add(defaultAddresses.iterator());
				for (final IpRange loopbackRange : ipRangeRepository.findV4LoopbackRanges()) {
					compositeIterator.add(iteratorForRange(loopbackRange));
				}
				for (final IpRange range : ipRangeRepository.findMatchingRange(AddressRangeType.CONNECTION, IpAddressType.V4, 32)) {
					compositeIterator.add(iteratorForRange(range));
				}
				return compositeIterator;
			}

			private Iterator<InetAddress> iteratorForRange(final IpRange loopbackRange) {
				return new Iterator<InetAddress>() {
					final IpAddress address;
					long index = 1;
					final long lastAddressIndex;
					{
						final IpNetwork range = loopbackRange.getRange();
						lastAddressIndex = (1l << (32 - range.getNetmask())) - 1;
						address = range.getAddress();
					}

					@Override
					public boolean hasNext() {
						return index < lastAddressIndex;
					}

					@Override
					public InetAddress next() {
						return address.getAddressOfNetwork(index++);
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException("cannot remove a possible address");
					}
				};
			}
		};
	}

	private String makeInterfaceName(final CustomerConnection customerConnection) {
		if (customerConnection.getName() == null) {
			return "customer";
		}
		return "customer-" + customerConnection.getName();
	}

	private Map<Integer, VLan> orderNetworksByVlan(final Set<VLan> networks) {
		final Map<Integer, VLan> ret = new LinkedHashMap<Integer, VLan>();
		for (final VLan vLan : networks) {
			ret.put(vLan.getVlanId(), vLan);
		}
		return ret;
	}

	private boolean overlap(final IpNetwork checkNetwork, final IpNetwork reserveNetwork) {
		if (checkNetwork.containsAddress(reserveNetwork.getAddress())) {
			return true;
		}
		return reserveNetwork.containsAddress(checkNetwork.getAddress());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.wisp.manager.service.impl.AddressManagementService#removeGlobalDns(ch.bergturbenthal.wisp.manager.model.IpAddress)
	 */
	@Override
	public void removeGlobalDns(final IpAddress address) {
		dnsServerRepository.delete(address);
	}

	@Override
	public void removeRange(final IpRange ipRange) {
		if (ipRange.getType() != AddressRangeType.ASSIGNED && ipRange.getReservations().isEmpty()) {
			ipRangeRepository.delete(ipRange);
		}

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ch.bergturbenthal.wisp.manager.service.impl.AddressManagementService#reserveRange(ch.bergturbenthal.wisp.manager.model.IpRange,
	 * ch.bergturbenthal.wisp.manager.model.address.AddressRangeType, int, java.lang.String)
	 */
	@Override
	public IpRange reserveRange(final IpRange parentRange, final AddressRangeType type, final int mask, final String comment) {
		if (mask < parentRange.getRangeMask()) {
			throw new IllegalArgumentException("To big range: " + mask + " parent allowes " + parentRange.getRangeMask());
		}
		final boolean isV4 = parentRange.getRange().getAddress().getAddressType() == IpAddressType.V4;
		final BigInteger parentRangeStartAddress = parentRange.getRange().getAddress().getRawValue();
		final BigInteger rangeSize = BigInteger.valueOf(1).shiftLeft((isV4 ? 32 : 128) - parentRange.getRangeMask());
		// for single v4-address -> skip first and last address
		final boolean isV4SingleAddress = parentRange.getRangeMask() == 32 && isV4;
		final long availableReservations = isV4SingleAddress ? parentRange.getAvailableReservations() - 1 : parentRange.getAvailableReservations();
		nextReservation:
		for (int i = isV4SingleAddress ? 1 : 0; i < availableReservations; i++) {
			final BigInteger candidateAddress = parentRangeStartAddress.add(rangeSize.multiply(BigInteger.valueOf(i)));
			final Collection<IpRange> reservations = parentRange.getReservations();
			for (final IpRange reservationRange : reservations) {
				if (reservationRange.getRange().getAddress().getRawValue().equals(candidateAddress)) {
					continue nextReservation;
				}
			}
			// reservation is free
			final IpRange newRange = new IpRange(new IpNetwork(new IpAddress(candidateAddress), parentRange.getRangeMask()), mask, type);
			newRange.setParentRange(parentRange);
			parentRange.getReservations().add(newRange);
			newRange.setComment(comment);
			log.info("Reserved: " + newRange);
			return ipRangeRepository.save(newRange);
		}
		// no free reservation found in range
		return null;
	}

	@Override
	public boolean setAddressManually(final RangePair addressPair, final String address, final IpAddressType addressType) {
		try {
			if (address == null || address.trim().isEmpty()) {
				addressPair.setIpAddress(null, addressType);
				// if (offsetPair != null) {
				// offsetPair.setExpectedOffset(null, addressType);
				// }
				return true;
			}
			final IpRange reservationBefore = addressPair.getIpAddress(addressType);
			if (reservationBefore != null) {
				clearIntermediateParent(reservationBefore.getParentRange());
				ipRangeRepository.delete(reservationBefore);
				addressPair.setIpAddress(null, addressType);
			}
			final String[] addressParts = address.split("/", 2);
			final InetAddress inetAddress = InetAddress.getByName(addressParts[0]);
			final int singleAddressMask;
			switch (addressType) {
			case V4:
				if (!(inetAddress instanceof Inet4Address)) {
					log.info("Wrong v4-Address: " + address);
					return false;
				}
				singleAddressMask = 32;
				break;
			case V6:
				if (!(inetAddress instanceof Inet6Address)) {
					log.info("Wrong v6-Address: " + address);
					return false;
				}
				singleAddressMask = 128;
				break;
			default:
				log.info("Unknown Address-Type: " + addressType);
				return false;
			}
			final int addressMask;
			if (addressParts.length > 1) {
				addressMask = Integer.parseInt(addressParts[1]);
			} else {
				addressMask = singleAddressMask;
			}
			final IpAddress enteredIpAddress = new IpAddress(inetAddress);
			final IpNetwork reserveNetwork = new IpNetwork(enteredIpAddress, addressMask);
			final IpRange foundParentRange = findParentRange(reserveNetwork);
			final IpRange reservedIntermediateRange;
			if (foundParentRange == null) {
				// reserve special range
				if (addressMask > singleAddressMask - 2) {
					log.info("Cannot create reservation for " + address);
					return false;
				}
				final IpRange rootRange = addRootRange(inetAddress, addressMask, addressMask, "");
				reservedIntermediateRange = reserveRange(rootRange, AddressRangeType.INTERMEDIATE, singleAddressMask, "");
			} else {
				final IpNetwork ipNetwork = new IpNetwork(enteredIpAddress, foundParentRange.getRangeMask());
				for (final IpRange checkRange : foundParentRange.getReservations()) {
					if (ipNetwork.getAddress().getRawValue().equals(checkRange.getRange().getAddress().getRawValue())) {
						log.info("Address-Range " + ipNetwork + " is already reserved");
						return false;
					}
				}
				reservedIntermediateRange = new IpRange(ipNetwork, singleAddressMask, AddressRangeType.INTERMEDIATE);
				reservedIntermediateRange.setParentRange(foundParentRange);
				foundParentRange.getReservations().add(reservedIntermediateRange);
				ipRangeRepository.save(reservedIntermediateRange);
			}
			final IpNetwork reservationNetwork = new IpNetwork(enteredIpAddress, singleAddressMask);
			final IpRange reservedRange = new IpRange(reservationNetwork, singleAddressMask, AddressRangeType.ASSIGNED);
			reservedRange.setParentRange(reservedIntermediateRange);
			reservedIntermediateRange.getReservations().add(reservedRange);
			ipRangeRepository.save(reservedRange);
			addressPair.setIpAddress(reservedRange, addressType);
			return true;
		} catch (final UnknownHostException e) {
			log.info("Unknown IP Address", e);
			return false;
		}
	}

	private void updateInterfaceTitle(final NetworkInterface networkInterface, final Connection connection) {
		networkInterface.setInterfaceName("connection: " + connection.getTitle());
	}
}
