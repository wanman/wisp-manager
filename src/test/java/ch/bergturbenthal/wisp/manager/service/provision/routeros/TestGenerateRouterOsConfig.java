package ch.bergturbenthal.wisp.manager.service.provision.routeros;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import ch.bergturbenthal.wisp.manager.WispManager;
import ch.bergturbenthal.wisp.manager.model.NetworkDevice;
import ch.bergturbenthal.wisp.manager.model.Password;
import ch.bergturbenthal.wisp.manager.model.Station;
import ch.bergturbenthal.wisp.manager.model.devices.NetworkDeviceType;
import ch.bergturbenthal.wisp.manager.repository.PasswordRepository;
import ch.bergturbenthal.wisp.manager.repository.StationRepository;
import ch.bergturbenthal.wisp.manager.service.AddressManagementService;
import ch.bergturbenthal.wisp.manager.service.ConnectionService;
import ch.bergturbenthal.wisp.manager.service.DemoSetupService;
import ch.bergturbenthal.wisp.manager.service.NetworkDeviceManagementService;
import ch.bergturbenthal.wisp.manager.service.StationService;
import ch.bergturbenthal.wisp.manager.service.TestHelperBean;

@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = WispManager.class)
public class TestGenerateRouterOsConfig {
	@Autowired
	private AddressManagementService addressManagementBean;
	@Autowired
	private ConnectionService connectionService;
	@Autowired
	private DemoSetupService demoSetupService;
	@Autowired
	private NetworkDeviceManagementService networkDeviceManagementBean;
	@Autowired
	private PasswordRepository passwordRepository;
	@Autowired
	private StationRepository stationRepository;
	@Autowired
	private StationService stationService;
	@Autowired
	private TestHelperBean testHelperBean;
	@Autowired
	private PlatformTransactionManager transactionManager;
	private TransactionTemplate transactionTemplate;

	private void checkStation(final Station station, final String filename) {
		final Long stationId = station.getId();
		transactionTemplate.execute(new TransactionCallback<Void>() {

			@Override
			public Void doInTransaction(final TransactionStatus status) {
				try {
					final Station station = stationRepository.findOne(stationId);
					final File file = new File("target/result/" + filename);
					if (!file.getParentFile().exists()) {
						file.getParentFile().mkdirs();
					}
					@Cleanup
					final OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file));
					final String generatedConfig = networkDeviceManagementBean.generateConfig(station.getDevice());
					writer.write(generatedConfig);
					final ClassPathResource classPathResource = new ClassPathResource("templates/" + filename);

					@Cleanup
					final InputStreamReader reader = new InputStreamReader(classPathResource.getInputStream(), "utf-8");
					final String template = IOUtils.toString(reader);
					Assert.assertEquals("Difference in " + station.getName(), template, generatedConfig);
					return null;

				} catch (final IOException e) {
					throw new AssertionError("error in test", e);
				}
			}
		});
	}

	private String createFileName(final Station station) {
		return station.getName().replace("ä", "ae") + ".rsc";
	}

	@Before
	public void initData() throws UnknownHostException {
		transactionTemplate = new TransactionTemplate(transactionManager);
		transactionTemplate.execute(new TransactionCallback<Void>() {

			@Override
			public Void doInTransaction(final TransactionStatus status) {
				testHelperBean.clearData();
				final Password routerPw = new Password();
				routerPw.setDeviceType(NetworkDeviceType.STATION);
				routerPw.setPassword("HelloPw");
				passwordRepository.save(routerPw);
				demoSetupService.initDemoData();
				for (final Station station : stationService.listAllStations()) {
					demoSetupService.fillDummyDevice(station);
					addressManagementBean.fillStation(station);
				}
				return null;
			}
		});
	}

	private void switchDevices(final Long id0, final Long id1) {
		final Station station0 = stationRepository.findOne(id0);
		final Station station1 = stationRepository.findOne(id1);
		final NetworkDevice device0 = station0.getDevice();
		final NetworkDevice device1 = station1.getDevice();
		station0.setDevice(device1);
		station1.setDevice(device0);
	}

	@Test
	public void testGenerateRbConfig() throws UnknownHostException, IOException {

		final List<Station> stationList = new ArrayList<Station>(stationService.listAllStations());
		for (final Station station : stationList) {
			checkStation(station, createFileName(station));
		}
		for (final Station station : stationList) {
			checkStation(station, createFileName(station));
		}
		Collections.reverse(stationList);
		for (final Station station : stationList) {
			checkStation(station, createFileName(station));
		}
		transactionTemplate.execute(new TransactionCallback<Void>() {

			@Override
			public Void doInTransaction(final TransactionStatus status) {
				switchDevices(stationList.get(0).getId(), stationList.get(1).getId());
				switchDevices(stationList.get(2).getId(), stationList.get(3).getId());
				return null;
			}
		});
		for (final Station station : stationList) {
			checkStation(station, "sw-" + createFileName(station));
		}
	}

}
