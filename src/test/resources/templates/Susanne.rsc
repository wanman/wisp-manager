
/system identity set name="Station Susanne"

/user set admin password="HelloPw"

# all interfaces in routing mode
/interface ethernet
	set [ find mac-address=B2:8E:05:FA:70:90 ] master-port=none speed=1Gbps name=customer
	set [ find mac-address=B2:8E:05:FA:70:91 ] master-port=none speed=1Gbps name=station-connection-1
	set [ find mac-address=B2:8E:05:FA:70:92 ] master-port=none speed=1Gbps name=station-connection-2
	set [ find mac-address=B2:8E:05:FA:70:93 ] master-port=none speed=1Gbps name=station-connection-3
	set [ find mac-address=B2:8E:05:FA:70:94 ] master-port=none speed=1Gbps name=station-connection-4
/interface vlan
	remove numbers=[find]

/interface ipipv6
#	remove numbers=[find]
	add local-address=fd7e:907d:34ab:0:0:0:0:2 mtu=1500 name=tunnel-Berg remote-address=fd7e:907d:34ab:0:0:0:0:0


/interface bridge
	print
#	remove numbers=[find]
	add name=loopback auto-mac=no admin-mac=01:00:00:00:01:00
	
/interface pppoe-client

/ip address	
#	remove numbers=[find]
# Loopback IP
	add address=172.16.0.3/32 interface=loopback network=172.16.0.3
	
# configured IPs
	add address=172.17.4.1/24 interface=customer
	add address=172.16.1.57/29 interface=station-connection-1
	add address=172.16.1.65/29 interface=station-connection-2
	add address=172.16.1.73/29 interface=station-connection-3
	add address=172.16.1.81/29 interface=station-connection-4

# Tunnel Endpoints
	add address=172.16.4.5/30 interface=tunnel-Berg

# ipv4 ospf Routing
/routing ospf instance set [ find default=yes ] router-id=172.16.0.3

/routing ospf interface
	remove numbers=[find dynamic=no]
	add interface=tunnel-Berg network-type=point-to-point
	add interface=customer passive=yes
	add interface=station-connection-1 passive=yes
	add interface=station-connection-2 passive=yes
	add interface=station-connection-3 passive=yes
	add interface=station-connection-4 passive=yes

/routing ospf network
	remove numbers=[find dynamic=no]
	add area=backbone network=172.16.0.3/32
	add area=backbone network=172.17.4.0/24
	add area=backbone network=172.16.1.56/29
	add area=backbone network=172.16.1.64/29
	add area=backbone network=172.16.1.72/29
	add area=backbone network=172.16.1.80/29
	add area=backbone network=172.16.4.4/30


# ipv4 dhcp

/ip pool
	remove numbers=[find]
	add name=customer_pool ranges=172.17.4.20-172.17.4.100
	add name=station-connection-1_pool ranges=172.16.1.58-172.16.1.62
	add name=station-connection-2_pool ranges=172.16.1.66-172.16.1.70
	add name=station-connection-3_pool ranges=172.16.1.74-172.16.1.78
	add name=station-connection-4_pool ranges=172.16.1.82-172.16.1.86
/ip dhcp-server
	remove numbers=[find]
	add address-pool=customer_pool disabled=no interface=customer lease-time=30m name=dhcp_customer
	add address-pool=station-connection-1_pool disabled=no interface=station-connection-1 lease-time=10m name=dhcp_station-connection-1
	add address-pool=station-connection-2_pool disabled=no interface=station-connection-2 lease-time=10m name=dhcp_station-connection-2
	add address-pool=station-connection-3_pool disabled=no interface=station-connection-3 lease-time=10m name=dhcp_station-connection-3
	add address-pool=station-connection-4_pool disabled=no interface=station-connection-4 lease-time=10m name=dhcp_station-connection-4
/ip dhcp-server network	
	remove numbers=[find]
	add address=172.17.4.0/24 gateway=172.17.4.1
	add address=172.16.1.56/29 gateway=172.16.1.57
	add address=172.16.1.64/29 gateway=172.16.1.65
	add address=172.16.1.72/29 gateway=172.16.1.73
	add address=172.16.1.80/29 gateway=172.16.1.81

/ip dhcp-client
	remove numbers=[find]

# ipv4 firewall

/ip firewall filter
	remove numbers=[find]
	add chain=forward connection-state=established
	add chain=forward connection-state=related
	add chain=forward protocol=icmp
	add chain=input connection-state=established
	add chain=input connection-state=related
	add chain=input protocol=icmp
	add action=reject chain=forward in-interface=customer reject-with=icmp-admin-prohibited src-address=!172.17.4.0/24
	add action=reject chain=input in-interface=customer reject-with=icmp-admin-prohibited src-address=!172.17.4.0/24
/ip firewall nat
	remove numbers=[find]


/ipv6 address
	remove numbers=[find]
# Loopback IP
	add address=fd7e:907d:34ab:0:0:0:0:2/128 interface=loopback advertise=no

# Connection IPs
	add address=2001:1620:bba:4:0:0:0:0/64 interface=customer
	add address=fd7e:907d:34ab:107:0:0:0:0/64 interface=station-connection-1
	add address=fd7e:907d:34ab:108:0:0:0:0/64 interface=station-connection-2
	add address=fd7e:907d:34ab:109:0:0:0:0/64 interface=station-connection-3
	add address=fd7e:907d:34ab:10a:0:0:0:0/64 interface=station-connection-4
 
# ospf-v3 Routing 
 
/routing ospf-v3 instance set [ find default=yes ] router-id=172.16.0.3
/routing ospf-v3 interface
	remove numbers=[find]
# Loopback
	add area=backbone interface=loopback passive=yes
	
# Physically Connections
	add area=backbone interface=customer passive=yes
	add area=backbone interface=station-connection-1
	add area=backbone interface=station-connection-2
	add area=backbone interface=station-connection-3
	add area=backbone interface=station-connection-4

/ipv6 nd
	remove numbers=[find default=no]
	set [ find default=yes ] advertise-dns=yes interface=customer
	add interface=station-connection-1 advertise-dns=yes
	add interface=station-connection-2 advertise-dns=yes
	add interface=station-connection-3 advertise-dns=yes
	add interface=station-connection-4 advertise-dns=yes

# IPv6 Firewall
/ipv6 firewall filter
	remove numbers=[find]
	add chain=forward connection-state=established
	add chain=forward connection-state=related
	add chain=forward protocol=icmpv6
	add chain=input connection-state=established
	add chain=input connection-state=related
	add chain=input protocol=icmpv6
	add action=reject chain=forward in-interface=customer reject-with=icmp-admin-prohibited src-address=!2001:1620:bba:4:0:0:0:0/64
	add action=reject chain=input in-interface=customer reject-with=icmp-admin-prohibited src-address=!2001:1620:bba:4:0:0:0:0/64

/ip dns
set servers=2001:4860:4860:0:0:0:0:8888,8.8.8.8

/system reboot