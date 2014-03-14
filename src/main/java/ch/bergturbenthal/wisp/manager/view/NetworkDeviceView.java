package ch.bergturbenthal.wisp.manager.view;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.List;

import javax.ejb.EJB;

import ch.bergturbenthal.wisp.manager.model.MacAddress;
import ch.bergturbenthal.wisp.manager.model.NetworkDevice;
import ch.bergturbenthal.wisp.manager.model.NetworkInterface;
import ch.bergturbenthal.wisp.manager.model.devices.NetworkDeviceModel;
import ch.bergturbenthal.wisp.manager.service.NetworkDeviceManagementBean;
import ch.bergturbenthal.wisp.manager.service.NetworkDeviceProviderBean;

import com.vaadin.addon.jpacontainer.EntityItem;
import com.vaadin.addon.jpacontainer.EntityItemProperty;
import com.vaadin.addon.jpacontainer.JPAContainer;
import com.vaadin.cdi.CDIView;
import com.vaadin.data.Property;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener.ViewChangeEvent;
import com.vaadin.ui.AbstractSelect.ItemCaptionMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.ListSelect;
import com.vaadin.ui.Table;
import com.vaadin.ui.TextField;
import com.vaadin.ui.VerticalLayout;

@CDIView(value = NetworkDeviceView.VIEW_ID)
public class NetworkDeviceView extends CustomComponent implements View {
	public static final String VIEW_ID = "NetworkDevices";
	@EJB
	private NetworkDeviceManagementBean networkDeviceManagementBean;
	@EJB
	private NetworkDeviceProviderBean networkDeviceProviderBean;

	@Override
	public void enter(final ViewChangeEvent event) {

		final JPAContainer<NetworkDevice> devicesContainer = new JPAContainer<>(NetworkDevice.class);
		devicesContainer.setEntityProvider(networkDeviceProviderBean);
		// devicesContainer.addNestedContainerProperty("interfaces.macAddress");
		devicesContainer.setAutoCommit(true);

		final HorizontalLayout horizontalLayout = new HorizontalLayout();
		final ListSelect deviceSelect = new ListSelect("Select a Country", devicesContainer);
		deviceSelect.setItemCaptionMode(ItemCaptionMode.PROPERTY);
		deviceSelect.setItemCaptionPropertyId("title");
		final VerticalLayout selectDeviceLayout = new VerticalLayout();
		selectDeviceLayout.addComponent(deviceSelect);

		for (final NetworkDeviceModel model : NetworkDeviceModel.values()) {
			selectDeviceLayout.addComponent(new Button("add " + model, new ClickListener() {

				@Override
				public void buttonClick(final ClickEvent event) {
					devicesContainer.addEntity(NetworkDevice.createDevice(model));
				}
			}));
		}
		selectDeviceLayout.addComponent(new Button("identify 192.168.88.1", new ClickListener() {

			@Override
			public void buttonClick(final ClickEvent event) {
				try {
					final NetworkDevice detectNetworkDevice = networkDeviceManagementBean.detectNetworkDevice(InetAddress.getByName("192.168.88.1"));
					devicesContainer.addEntity(detectNetworkDevice);

				} catch (final Throwable e) {
					e.printStackTrace();
				}
			}
		}));
		selectDeviceLayout.addComponent(new Button("remove Device", new ClickListener() {

			@Override
			public void buttonClick(final ClickEvent event) {
				final Object selectedValue = deviceSelect.getValue();
				if (selectedValue != null) {
					devicesContainer.removeItem(selectedValue);
				}
			}
		}));

		final FormLayout editDeviceForm = new FormLayout();
		editDeviceForm.setEnabled(false);
		deviceSelect.addValueChangeListener(new ValueChangeListener() {

			@Override
			public void valueChange(final ValueChangeEvent event) {

				final EntityItem<NetworkDevice> deviceItem = devicesContainer.getItem(event.getProperty().getValue());
				editDeviceForm.removeAllComponents();
				editDeviceForm.addComponent(new Label(deviceItem.getItemProperty("title")));
				final EntityItemProperty itemProperty = deviceItem.getItemProperty("interfaces");
				final Property<String> macAddressDataSource = new Property<String>() {

					@Override
					public Class<? extends String> getType() {
						return String.class;
					}

					@Override
					public String getValue() {
						return ((List<NetworkInterface>) itemProperty.getValue()).get(0).getMacAddress().getAddress();
					}

					@Override
					public boolean isReadOnly() {
						return false;
					}

					@Override
					public void setReadOnly(final boolean newStatus) {

					}

					@Override
					public void setValue(final String newValue) throws com.vaadin.data.Property.ReadOnlyException {
						final List<NetworkInterface> interfaces = deviceItem.getEntity().getInterfaces();
						final Iterator<MacAddress> macAddressIterator = deviceItem.getEntity()
																																			.getDeviceModel()
																																			.getAddressIncrementorFactory()
																																			.getAllMacAddresses(new MacAddress(newValue))
																																			.iterator();
						for (final NetworkInterface networkInterface : interfaces) {
							networkInterface.setMacAddress(macAddressIterator.next());
						}
						itemProperty.setValue(interfaces);
					}
				};
				editDeviceForm.addComponent(new TextField("Base-Address", macAddressDataSource));

				final BeanItemContainer<NetworkInterface> dataSource = new BeanItemContainer<>(NetworkInterface.class, deviceItem.getEntity().getInterfaces());
				dataSource.addNestedContainerProperty("macAddress.address");
				final Table table = new Table("interfaces", dataSource);
				table.setVisibleColumns("type", "macAddress.address");
				table.setPageLength(0);

				editDeviceForm.addComponent(table);

				editDeviceForm.addComponent(new Button("Save", new ClickListener() {

					@Override
					public void buttonClick(final ClickEvent event) {
						table.commit();

						devicesContainer.commit();
						editDeviceForm.setEnabled(false);
					}
				}));

				editDeviceForm.setEnabled(true);
			}
		});
		deviceSelect.setImmediate(true);
		deviceSelect.setNullSelectionAllowed(false);
		horizontalLayout.addComponent(selectDeviceLayout);
		horizontalLayout.addComponent(editDeviceForm);
		setCompositionRoot(horizontalLayout);
		horizontalLayout.setSizeFull();
		setSizeFull();

	}

}
