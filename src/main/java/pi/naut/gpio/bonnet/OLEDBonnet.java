package pi.naut.gpio.bonnet;

import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import io.micronaut.runtime.event.annotation.EventListener;
import pi.naut.gpio.bonnet.layout.WelcomeLayout;
import pi.naut.gpio.config.PinConfiguration;
import pi.naut.gpio.controller.DisplayController;
import pi.naut.gpio.controller.PinController;
import util.StateList;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

@Singleton
public class OLEDBonnet {

	// Controllers
	@Inject
	private PinController pinController;
	@Inject
	private DisplayController displayController;

	@Inject
	private StateList<Layout> layouts;

	@PostConstruct
	private void initialize() {
		displayLayout(WelcomeLayout.NAME);
		applyGlobalEvents();
	}

	@EventListener
	void refreshDisplay(RefreshDisplayEvent refreshDisplayEvent) {
		if (layouts.hasCurrent() && refreshDisplayEvent.getSource().contains(layouts.current().name())) {
			displayController.display(layouts.current());
		}
	}

	public void displayLayout(Layout layout) {
		if (layout == null) {
			return;
		}
		displayController.display(layout);
		applyLayoutEvents(layout);
	}

	public void displayLayout(String layoutName) {
		layouts.getList()
				.stream()
				.filter(l -> l.name().equals(layoutName))
				.findAny()
				.ifPresent(this::displayLayout);
	}

	private void applyLayoutEvents(Layout layout) {
		Map<String, GpioPinDigitalInput> pins = pinController.getInputPins().entrySet()
				.stream()
				.filter(pin -> !PinConfiguration.JOYSTICK_CENTER.equals(pin.getKey())) // exclude global pin
				.collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

		pins.values().forEach(pin -> {
			pin.removeAllListeners();
			pin.removeAllTriggers();
		});
		layout.applyListeners(this).forEach((pin, listener) -> pins.get(pin).addListener(listener));
		layout.applyTriggers(this).forEach((pin, trigger) -> pins.get(pin).addTrigger(trigger));
	}

	private void applyGlobalEvents() {
		// Cycle through primary layoutFactory with the CENTER JOYSTICK
		pinController.getInputPins().get(PinConfiguration.JOYSTICK_CENTER)
				.addListener((GpioPinListenerDigital) event -> {
					if (event.getState().isHigh()) {
						nextPrimaryLayout();
					}
				});
	}

	private void nextPrimaryLayout() {
		while (!layouts.next().isPrimary()) ;
		displayLayout(layouts.current());
	}

}