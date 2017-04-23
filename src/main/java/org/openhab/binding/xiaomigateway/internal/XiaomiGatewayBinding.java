/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.xiaomigateway.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang.StringUtils;
import org.openhab.binding.xiaomigateway.XiaomiGatewayBindingProvider;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.types.*;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.HashMap;
import java.util.Map;


/**
 * Implement this class if you are going create an actively polling service
 * like querying a Website/Device.
 *
 * @author Ondrej Pecta
 * @since 1.9.0
 */
public class XiaomiGatewayBinding extends AbstractActiveBinding<XiaomiGatewayBindingProvider> {

    private final int BUFFER_LENGTH = 1024;
    //private final int DEST_PORT = 9898;
    private final String MCAST_ADDR = "224.0.0.50";
    private final int MCAST_PORT = 4321;
    private String gatewayIP = "";
    private int dest_port = 9898;
    private MulticastSocket socket = null;

    private Thread thread;

    //Smart device list
    Map<String, String> devicesList = new HashMap<String, String>();

    //Configuration
    private String key = "";

    //Gateway info
    private String sid = "";
    private String token = "";
    private long rgb = 0;
    private int illumination = 0;
    private long startColor = 1677786880L; //green

    //Gson parser
    private JsonParser parser = new JsonParser();


    byte[] buffer = new byte[BUFFER_LENGTH];
    DatagramPacket dgram = new DatagramPacket(buffer, buffer.length);

    private static final Logger logger =
            LoggerFactory.getLogger(XiaomiGatewayBinding.class);

    public void setItemRegistry(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    public void unsetItemRegistry(ItemRegistry itemRegistry) {
        this.itemRegistry = null;
    }

    /**
     * The BundleContext. This is only valid when the bundle is ACTIVE. It is set in the activate()
     * method and must not be accessed anymore once the deactivate() method was called or before activate()
     * was called.
     */
    private BundleContext bundleContext;

    private ItemRegistry itemRegistry;


    /**
     * the refresh interval which is used to poll values from the XiaomiGateway
     * server (optional, defaults to 60000ms)
     */
    private long refreshInterval = 60000;


    public XiaomiGatewayBinding() {
    }


    /**
     * Called by the SCR to activate the component with its configuration read from CAS
     *
     * @param bundleContext BundleContext of the Bundle that defines this component
     * @param configuration Configuration properties for this component obtained from the ConfigAdmin service
     */
    public void activate(final BundleContext bundleContext, final Map<String, Object> configuration) {
        this.bundleContext = bundleContext;

        // the configuration is guaranteed not to be null, because the component definition has the
        // configuration-policy set to require. If set to 'optional' then the configuration may be null

        // read further config parameters here ...
        readConfiguration(configuration);
        setupSocket();
        setProperlyConfigured(socket != null);
    }

    private void readConfiguration(Map<String, Object> configuration) {
        // to override the default refresh interval one has to add a
        // parameter to openhab.cfg like <bindingName>:refresh=<intervalInMs>
        String refreshIntervalString = (String) configuration.get("refresh");
        if (StringUtils.isNotBlank(refreshIntervalString)) {
            refreshInterval = Long.parseLong(refreshIntervalString);
        }
        String startColorString = (String) configuration.get("startColor");
        if (StringUtils.isNotBlank(startColorString)) {
            //hack - replace long value ending L with blank because of misleading dccumentation
            startColor = Long.parseLong(startColorString.replace("L", ""));
        }
        String keyString = (String) configuration.get("key");
        if (StringUtils.isNotBlank(keyString)) {
            key = keyString;
        }

    }

    private void discoverGateways() {
        try {
            String sendString = "{\"cmd\": \"whois\"}";
            byte[] sendData = sendString.getBytes("UTF-8");
            InetAddress addr = InetAddress.getByName(MCAST_ADDR);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, addr, MCAST_PORT);

            socket.send(sendPacket);
        } catch (IOException e) {
            logger.error(e.toString());
        }
    }

    private void setupSocket() {
        try {
            socket = new MulticastSocket(dest_port); // must bind receive side
            socket.joinGroup(InetAddress.getByName(MCAST_ADDR));
        } catch (IOException e) {
            logger.error(e.toString());
        }

        thread = new Thread(new Runnable() {
            public void run() {
                receiveData(socket, dgram);
            }
        });
        thread.start();
    }

    private void receiveData(MulticastSocket socket, DatagramPacket dgram) {
        while (!socket.isClosed()) {
            try {
                socket.receive(dgram);
                String sentence = new String(dgram.getData(), 0,
                        dgram.getLength());

                if (sentence.contains("\\\"mid\\\""))
                    logger.info("Received packet: " + sentence);
                else
                    logger.debug("Received packet: " + sentence);


                JsonObject jobject = parser.parse(sentence).getAsJsonObject();
                String command = jobject.get("cmd").getAsString();

                if (jobject.has("model") && jobject.has("sid")) {
                    String newId = jobject.get("sid").getAsString();
                    String model = jobject.get("model").getAsString();
                    addDevice(newId, model);
                }

                switch (command) {
                    case "iam":
                        getGatewayInfo(jobject);
                        requestRead(sid);
                        requestIdList();
                        break;
                    case "get_id_list_ack":
                        token = jobject.get("token").getAsString();
                        listIds(jobject);
                        break;
                    case "read_ack":
                        listDevice(jobject);
                        break;
                    case "write":
                        logger.error("Received write command which is designed for the gateway. Are you sure you have the right developer key?");
                        logger.error(sentence);
                        break;
                    case "write_ack":
                        if (sentence.contains("\"error")) {
                            logger.error(sentence);
                        }
                        break;
                    case "heartbeat":
                        String model = jobject.get("model").getAsString();
                        if (model.equals("gateway")) {
                            token = jobject.get("token").getAsString();
                            break;
                        }
                        if (model.equals("cube") || model.equals("switch")) {
                            break;
                        }
                        processOtherCommands(jobject);
                        break;
                    case "report":
                        model = jobject.get("model").getAsString();
                        processOtherCommands(jobject);
                        break;
                    default:
                        logger.error("Unknown Xiaomi gateway command: " + command);
                }
            } catch (Exception e) {
                logger.error(e.toString());
            }
        }
    }

    private void addDevice(String newId, String model) {
        if (!devicesList.containsKey(newId)) {
            logger.info("Detected new Xiaomi smart device - sid: " + newId + " model: " + model);
            devicesList.put(newId, model);
        }
    }

    private void processOtherCommands(JsonObject jobject) {

        for (final XiaomiGatewayBindingProvider provider : providers) {
            for (String itemName : provider.getItemNames()) {
                processEvent(provider, itemName, jobject);
            }

        }
    }

    private void processEvent(XiaomiGatewayBindingProvider provider, String itemName, JsonObject jobject) {
        String type = provider.getItemType(itemName);
        String eventSid = jobject.get("sid").getAsString();

        if (!(type.startsWith(eventSid) && type.contains(".")))
            return;

        String event = getItemEvent(type);
        switch (event) {
            case "temperature":
                if (isTemperatureEvent(jobject)) {
                    logger.debug("Processing temperature event");
                    processTemperatureEvent(itemName, jobject);
                }
                break;
            case "humidity":
                if (isHumidityEvent(jobject)) {
                    logger.debug("Processing humidity event");
                    processHumidityEvent(itemName, jobject);
                }
                break;
            case "light":
                if (isGatewayEvent(jobject)) {
                    logger.debug("Processing light switch event");
                    processLightSwitchEvent(itemName, jobject);
                }
                break;
            case "color":
                if (isGatewayEvent(jobject)) {
                    logger.debug("Processing color event");
                    processColorEvent(itemName, jobject);
                }
                break;
            case "illumination":
                if (isGatewayEvent(jobject)) {
                    logger.debug("Processing illumination event");
                    processIlluminationEvent(itemName, jobject);
                }
                break;
            case "brightness":
                logger.debug("Processing brightness event");
                processBrightnessEvent(itemName, jobject);
                break;
            case "virtual_switch":
                if (isButtonEvent(jobject, "click")) {
                    logger.debug("Processing virtual switch click event");
                    processVirtualSwitchEvent(itemName);
                }
                break;
            case "click":
                if (isButtonEvent(jobject, "click") || isSwitchEvent(jobject, type, "click")) {
                    logger.debug("Processing click event");
                    eventPublisher.sendCommand(itemName, OnOffType.ON);
                }

                break;
            case "double_click":
                if (isButtonEvent(jobject, "double_click") || isSwitchEvent(jobject, type, "double_click")) {
                    logger.debug("Processing double click event");
                    eventPublisher.sendCommand(itemName, OnOffType.ON);
                }
                break;
            case "both_click":
                if (isDualSwitchEvent(jobject, type)) {
                    logger.debug("Processing both click event");
                    eventPublisher.sendCommand(itemName, OnOffType.ON);
                }
                break;

            case "long_click":
                if (isButtonEvent(jobject, "long_click_press")) {
                    logger.debug("Processing long click event");
                    eventPublisher.sendCommand(itemName, OnOffType.ON);
                }
                break;
            case "long_click_release":
                if (isButtonEvent(jobject, "long_click_release")) {
                    logger.debug("Processing long click release event");
                    eventPublisher.sendCommand(itemName, OnOffType.ON);
                }
                break;
            case "magnet":
                if (isMagnetEvent(jobject)) {
                    logger.debug("Processing magnet event");
                    processMagnetEvent(itemName, jobject);
                }
                break;
            case "motion":
                if (isMotionEvent(jobject)) {
                    logger.debug("Processing motion event");
                    processMotionEvent(itemName, jobject);
                }
                break;
            case "plug":
                if (isPlugEvent(jobject)) {
                    logger.debug("Processing plug event");
                    processPlugEvent(itemName, jobject);
                }
                break;
            case "inuse":
                if (isPlugEvent(jobject)) {
                    logger.debug("Processing plug inuse event");
                    processPlugInuseEvent(itemName, jobject);
                }
                break;
            case "power_consumed":
                if (isPlugEvent(jobject)) {
                    logger.debug("Processing plug power_consumed event");
                    processPlugPowerConsumedEvent(itemName, jobject);
                }
                break;
            case "load_power":
                if (isPlugEvent(jobject)) {
                    logger.debug("Processing plug load_power event");
                    processPlugLoadPowerEvent(itemName, jobject);
                }
                break;
            case "voltage":
                if (hasVoltage(jobject)) {
                    logger.debug("Processing voltage event");
                    processVoltageEvent(itemName, jobject);
                }
            default:
                if (isCubeEvent(jobject)) {
                    processCubeEvent(itemName, type, jobject);
                }
        }
    }

    private void processLightSwitchEvent(String itemName, JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            if (jo == null || !jo.has("rgb"))
                return;
            rgb = jo.get("rgb").getAsLong();
            State oldValue = itemRegistry.getItem(itemName).getState();
            State newValue = rgb > 0 ? OnOffType.ON : OnOffType.OFF;

            if (!newValue.equals(oldValue))
                eventPublisher.postUpdate(itemName, newValue);
        } catch (Exception ex) {
            logger.error(ex.toString());
        }
    }

    private void processColorEvent(String itemName, JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            if (jo == null || !jo.has("rgb"))
                return;
            rgb = jo.get("rgb").getAsLong();
            State oldValue = itemRegistry.getItem(itemName).getState();
            //HSBType
            long br = rgb / 65536 / 256;
            Color color = new Color((int) (rgb - (br * 65536 * 256)));
            State newValue = new HSBType(color);

            if (!newValue.equals(oldValue))
                eventPublisher.postUpdate(itemName, newValue);
        } catch (Exception ex) {
            logger.error(ex.toString());
        }
    }

    private void processBrightnessEvent(String itemName, JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            if (jo == null || !jo.has("rgb"))
                return;
            rgb = jo.get("rgb").getAsLong();
            State oldValue = itemRegistry.getItem(itemName).getState();
            //HSBType
            int brightness = (int) (rgb / 65536 / 256);
            State newValue = new PercentType(brightness);

            if (!newValue.equals(oldValue))
                eventPublisher.postUpdate(itemName, newValue);
        } catch (Exception ex) {
            logger.error(ex.toString());
        }
    }

    private void processIlluminationEvent(String itemName, JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            if (jo == null || !jo.has("illumination"))
                return;
            illumination = jo.get("illumination").getAsInt();
            State oldValue = itemRegistry.getItem(itemName).getState();
            State newValue = new DecimalType(illumination);
            if (!newValue.equals(oldValue))
                eventPublisher.postUpdate(itemName, newValue);
        } catch (Exception ex) {
            logger.error(ex.toString());
        }
    }

    private void processCubeEvent(String itemName, String type, JsonObject jobject) {
        String event = getStatusEvent(jobject);
        boolean publish = false;
        if (isRotateCubeEvent(jobject)) {
            event = isLeftRotate(jobject) ? "rotate_left" : "rotate_right";
        }
        logger.debug("XiaomiGateway: processing cube event " + event);
        switch (event) {
            case "flip90":
                publish = type.endsWith(".flip90");
                break;
            case "flip180":
                publish = type.endsWith(".flip180");
                break;
            case "move":
                publish = type.endsWith(".move");
                break;
            case "tap_twice":
                publish = type.endsWith(".tap_twice");
                break;
            case "shake_air":
                publish = type.endsWith(".shake_air");
                break;
            case "swing":
                publish = type.endsWith(".swing");
                break;
            case "alert":
                publish = type.endsWith(".alert");
                break;
            case "free_fall":
                publish = type.endsWith(".free_fall");
                break;
            case "rotate_left":
                publish = type.endsWith(".rotate_left");
                break;
            case "rotate_right":
                publish = type.endsWith(".rotate_right");
                break;
            default:
                logger.error("Unknown cube event: " + event);
        }

        if (publish)
            eventPublisher.sendCommand(itemName, OnOffType.ON);
    }

    private boolean isLeftRotate(JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            return jobject.get("model").getAsString().equals("cube") && jo != null && jo.get("rotate") != null && jo.get("rotate").getAsString().startsWith("-");
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private boolean isRotateCubeEvent(JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            return jobject.get("model").getAsString().equals("cube") && jo != null && jo.get("rotate") != null;
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private String getStatusEvent(JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            if (jo == null || !jo.has("status"))
                return null;
            return jo.get("status").getAsString();
        } catch (Exception ex) {
            logger.error(ex.toString());
            return null;
        }
    }

    private boolean checkModel(JsonObject jobject, String model) {
        return jobject != null && jobject.has("model") && jobject.get("model").getAsString().equals(model);
    }

    private boolean isCubeEvent(JsonObject jobject) {
        return checkModel(jobject, "cube");
    }

    private boolean isMotionEvent(JsonObject jobject) {
        return checkModel(jobject, "motion");
    }

    private boolean isPlugEvent(JsonObject jobject) {
        return checkModel(jobject, "plug");
    }

    private boolean hasVoltage(JsonObject jobject) {
        if (jobject != null && jobject.has("data")) {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            if (jo.has("voltage")) {
                return true;
            }
        }
        return false;
    }

    private void getGatewayInfo(JsonObject jobject) {
        sid = jobject.get("sid").getAsString();
        dest_port = jobject.get("port").getAsInt();
        gatewayIP = jobject.get("ip").getAsString();
        logger.info("Discovered Xiaomi Gateway - sid: " + sid + " ip: " + gatewayIP + " port: " + dest_port);
    }

    private void listIds(JsonObject jobject) {
        String data = jobject.get("data").getAsString();
        JsonArray ja = parser.parse(data).getAsJsonArray();
        if (devicesList.size() == 0)
            logger.info("Discovered total of " + ja.size() + " Xiaomi smart devices");
        requestRead(sid);
        for (JsonElement je : ja) {
            requestRead(je.getAsString());
        }
    }

    private void listDevice(JsonObject jobject) {
        String newId = jobject.get("sid").getAsString();
        String model = jobject.get("model").getAsString();
        addDevice(newId, model);
        if (model.equals("gateway") || model.equals("sensor_ht") || model.equals("motion") || model.equals("magnet") || model.equals("plug")) {
            //read value
            processOtherCommands(jobject);
        }
    }

    private void processMotionEvent(String itemName, JsonObject jobject) {
        String data = jobject.get("data").getAsString();
        JsonObject jo = parser.parse(data).getAsJsonObject();
        String stat = (jo.has("status")) ? jo.get("status").getAsString().toLowerCase() : "no_motion";
        State oldValue;
        try {
            oldValue = itemRegistry.getItem(itemName).getState();
            State newValue = stat.equals("motion") ? OpenClosedType.OPEN : OpenClosedType.CLOSED;
            if (!newValue.equals(oldValue) || newValue.equals(OpenClosedType.OPEN))
                eventPublisher.postUpdate(itemName, newValue);
        } catch (ItemNotFoundException e) {
            logger.error(e.toString());
        }
    }

    private void processVoltageEvent(String itemName, JsonObject jobject) {
        String data = jobject.get("data").getAsString();
        JsonObject jo = parser.parse(data).getAsJsonObject();
        State newValue = (jo.has("voltage")) ? new DecimalType(jo.get("voltage").getAsInt()) : new DecimalType(0);
        try {
            State oldValue = itemRegistry.getItem(itemName).getState();
            if (!newValue.equals(oldValue))
                eventPublisher.postUpdate(itemName, newValue);
        } catch (ItemNotFoundException e) {
            logger.error(e.toString());
        }
    }

    private void processPlugEvent(String itemName, JsonObject jobject) {
        String data = jobject.get("data").getAsString();
        JsonObject jo = parser.parse(data).getAsJsonObject();
        String stat = (jo.has("status")) ? jo.get("status").getAsString().toLowerCase() : "off";
        State oldValue;
        try {
            oldValue = itemRegistry.getItem(itemName).getState();
            State newValue = stat.equals("on") ? OnOffType.ON : OnOffType.OFF;
            if (!newValue.equals(oldValue) || newValue.equals(OnOffType.ON))
                eventPublisher.postUpdate(itemName, newValue);
        } catch (ItemNotFoundException e) {
            logger.error(e.toString());
        }
    }

    private void processPlugPowerConsumedEvent(String itemName, JsonObject jobject) {
        processPlugPowerEvent(itemName, jobject, "power_consumed");
    }

    private void processPlugLoadPowerEvent(String itemName, JsonObject jobject) {
        processPlugPowerEvent(itemName, jobject, "load_power");
    }

    private void processPlugPowerEvent(String itemName, JsonObject jobject, String event) {
        String data = jobject.get("data").getAsString();
        JsonObject jo = parser.parse(data).getAsJsonObject();
        State newValue;
        State oldValue;
        if (jo.has(event)) {
            newValue = new DecimalType(jo.get(event).getAsInt());
        } else {
            if (jo.has("status") && jo.get("status").getAsString().toLowerCase().equals("off") && event.equals("load_power")) {
                //if status is off then power consumption is 0
                newValue = new DecimalType(0);
            } else
                return;
        }
        try {
            oldValue = itemRegistry.getItem(itemName).getState();
            if (!newValue.equals(oldValue))
                eventPublisher.postUpdate(itemName, newValue);
        } catch (ItemNotFoundException e) {
            logger.error(e.toString());
        }

    }

    private void processPlugInuseEvent(String itemName, JsonObject jobject) {
        String data = jobject.get("data").getAsString();
        JsonObject jo = parser.parse(data).getAsJsonObject();
        State newValue;
        if (jo.has("inuse")) {
            newValue = (jo.get("inuse").getAsString().equals("1")) ? OnOffType.ON : OnOffType.OFF;
        } else {
            if (jo.has("status") && jo.get("status").getAsString().toLowerCase().equals("off")) {
                //If power is off, in use is off too
                newValue = OnOffType.OFF;
            } else
                return;
        }

        State oldValue;
        try {
            oldValue = itemRegistry.getItem(itemName).getState();
            if (!newValue.equals(oldValue) || newValue.equals(OnOffType.ON))
                eventPublisher.postUpdate(itemName, newValue);
        } catch (ItemNotFoundException e) {
            logger.error(e.toString());
        }
    }

    private void processVirtualSwitchEvent(String itemName) {
        State oldValue;
        Command command = OnOffType.OFF;
        try {
            oldValue = itemRegistry.getItem(itemName).getState();
            command = oldValue.equals(OnOffType.ON) ? OnOffType.OFF : OnOffType.ON;
        } catch (ItemNotFoundException e) {
            logger.error(e.toString());
        }
        eventPublisher.sendCommand(itemName, command);
    }

    private void processMagnetEvent(String itemName, JsonObject jobject) {
        String data = jobject.get("data").getAsString();
        JsonObject jo = parser.parse(data).getAsJsonObject();
        String stat = jo.get("status").getAsString().toLowerCase();
        State oldValue;
        try {
            oldValue = itemRegistry.getItem(itemName).getState();
            State newValue = stat.equals("close") ? OpenClosedType.CLOSED : OpenClosedType.OPEN;
            if (!newValue.equals(oldValue))
                eventPublisher.postUpdate(itemName, newValue);
        } catch (ItemNotFoundException e) {
            logger.error(e.toString());
        }
    }

    private void processTemperatureEvent(String itemName, JsonObject jobject) {
        processSensorHTEvent(itemName, jobject, "temperature");
    }

    private void processHumidityEvent(String itemName, JsonObject jobject) {
        processSensorHTEvent(itemName, jobject, "humidity");
    }

    private void processSensorHTEvent(String itemName, JsonObject jobject, String sensor) {
        String data = jobject.get("data").getAsString();
        JsonObject jo = parser.parse(data).getAsJsonObject();
        Float val = formatValue(jo.get(sensor).getAsString());
        try {
            State oldValue = itemRegistry.getItem(itemName).getState();
            State newValue = new DecimalType(val);
            if (!newValue.equals(oldValue))
                eventPublisher.postUpdate(itemName, newValue);
        } catch (ItemNotFoundException e) {
            logger.error(e.toString());
        }
    }

    private boolean isMagnetEvent(JsonObject jobject) {
        return checkModel(jobject, "magnet");
    }

    private boolean isGatewayEvent(JsonObject jobject) {
        return checkModel(jobject, "gateway");
    }

    private boolean isButtonEvent(JsonObject jobject, String click) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            if (jo == null || !jo.has("status"))
                return false;
            return jobject != null && jobject.has("model") && jobject.get("model").getAsString().equals("switch") && jo.get("status").getAsString().equals(click);
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private boolean isSwitchEvent(JsonObject jobject, String itemType, String click) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            if (jo == null || (!jo.has("channel_0") && !jo.has("channel_1")))
                return false;
            String channel = getItemChannel(itemType);
            return (checkModel(jobject, "86sw1") || checkModel(jobject, "86sw2")) && jo.has(channel) && jo.get(channel).getAsString().equals(click);
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private boolean isDualSwitchEvent(JsonObject jobject, String itemType) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            if (jo == null || !jo.has("dual_channel"))
                return false;
            String channel = getItemChannel(itemType);
            return checkModel(jobject, "86sw2") && jo.has(channel) && jo.get(channel).getAsString().equals("both_click");
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private boolean isTemperatureEvent(JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            return checkModel(jobject, "sensor_ht") && jo.has("temperature");
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private boolean isHumidityEvent(JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            return checkModel(jobject, "sensor_ht") && jo.has("humidity");
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private Float formatValue(String value) {
        if( value.length() > 1) {
            return Float.parseFloat(value.substring(0, value.length() - 2) + "." + value.substring(2));
        }
        else {
            return Float.parseFloat(value);
        }
    }

    /**
     * Called by the SCR when the configuration of a binding has been changed through the ConfigAdmin service.
     *
     * @param config Updated configuration properties
     */
    public void modified(final Map<String, Object> config) {
        // update the internal configuration accordingly
        if (config != null) {
            readConfiguration(config);
        }
    }

    /**
     * Called by the SCR to deactivate the component when either the configuration is removed or
     * mandatory references are no longer satisfied or the component has simply been stopped.
     *
     * @param reason Reason code for the deactivation:<br>
     *               <ul>
     *               <li> 0 – Unspecified
     *               <li> 1 – The component was disabled
     *               <li> 2 – A reference became unsatisfied
     *               <li> 3 – A configuration was changed
     *               <li> 4 – A configuration was deleted
     *               <li> 5 – The component was disposed
     *               <li> 6 – The bundle was stopped
     *               </ul>
     */
    public void deactivate(final int reason) {
        this.bundleContext = null;
        if (this.socket != null)
            socket.close();
        if (thread != null && thread.isAlive())
            thread.interrupt();
        devicesList.clear();

        // deallocate resources here that are no longer needed and
        // should be reset when activating this binding again
    }


    /**
     * @{inheritDoc}
     */
    @Override
    protected long getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected String getName() {
        return "XiaomiGateway Refresh Service";
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected void execute() {
        // the frequently executed code (polling) goes here ...
        // logger.debug("execute() method is called!");
        if (!bindingsExist()) {
            return;
        }

        if (sid.equals("") || token.equals("")) {
            discoverGateways();
        } else {
            updateDevicesStatus();
        }
    }

    private void updateDevicesStatus() {
        for (String id : devicesList.keySet()) {
            requestRead(id);
        }
    }


    private void requestIdList() {
        try {
            String sendString = "{\"cmd\": \"get_id_list\"}";
            byte[] sendData = sendString.getBytes("UTF-8");
            InetAddress addr = InetAddress.getByName(gatewayIP);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, addr, dest_port);

            socket.send(sendPacket);
        } catch (IOException e) {
            logger.error(e.toString());
        }
    }

    private void requestRead(String device) {
        try {
            String sendString = "{\"cmd\": \"read\", \"sid\": \"" + device + "\"}";
            byte[] sendData = sendString.getBytes("UTF-8");
            InetAddress addr = InetAddress.getByName(gatewayIP);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, addr, dest_port);

            socket.send(sendPacket);
        } catch (IOException e) {
            logger.error(e.toString());
        }
    }

    private void requestWrite(String device, String[] keys, Object[] values) {
        try {
            String sendString = "{\"cmd\": \"write\", \"sid\": \"" + device + "\", \"data\": \"{" + getData(keys, values) + ", \\\"key\\\": \\\"" + getKey() + "\\\"}\"}";
            byte[] sendData = sendString.getBytes("UTF-8");
            InetAddress addr = InetAddress.getByName(gatewayIP);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, addr, dest_port);
            logger.debug("Sending to device: " + device + " message: " + sendString);
            socket.send(sendPacket);
        } catch (IOException e) {
            logger.error(e.toString());
        }
    }


    private void requestWriteGateway(String[] keys, Object[] values) {
        try {
            String key = getKey();
            //String sendString = "{\"cmd\": \"write\", \"model\": \"gateway\", \"sid\": \"" + device + "\", \"short_id\": \"0\", \"key\": \"" + key + "\", \"data\": \"{" + getData(keys, values) + ",\"key\":\\\"" + key + "\\\"}\"}";
            String sendString = "{\"cmd\": \"write\", \"model\": \"gateway\", \"sid\": \"" + sid + "\", \"short_id\": \"0\", \"data\": \"{" + getData(keys, values) + ",\\\"key\\\":\\\"" + key + "\\\"}\"}";
            byte[] sendData = sendString.getBytes("UTF-8");
            InetAddress addr = InetAddress.getByName(gatewayIP);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, addr, dest_port);
            logger.debug("Sending to gateway: " + sid + " message: " + sendString);
            socket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private String getData(String[] keys, Object[] values) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;

        if (keys.length != values.length)
            return "";

        for (int i = 0; i < keys.length; i++) {
            String k = keys[i];
            if (!first)
                builder.append(",");
            else
                first = false;

            //write key
            builder.append("\\\"").append(k).append("\\\"").append(": ");

            //write value
            builder.append(getValue(values[i]));
        }
        return builder.toString();
    }

    private String getKey() {
        logger.debug("Encrypting \"" + token + "\" with key \"" + key + "\"");
        return EncryptionHelper.encrypt(token, key);
    }

    private String getValue(Object o) {
        if (o instanceof String) {
            return "\\\"" + o + "\\\"";
        } else
            return o.toString();
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected void internalReceiveCommand(String itemName, Command command) {
        // the code being executed when a command was sent on the openHAB
        // event bus goes here. This method is only called if one of the
        // BindingProviders provide a binding for the given 'itemName'.
        logger.debug("internalReceiveCommand({},{}) is called!", itemName, command);
        String itemType = getItemType(itemName);
        if (!(command instanceof PercentType || command instanceof OnOffType || command instanceof HSBType)) {
            logger.error("Only OnOff/HSB/Percent command types currently supported");
            return;
        }
        if (!(itemType.endsWith(".light") || itemType.endsWith(".color") || itemType.endsWith(".brightness") || itemType.endsWith(".plug"))) {
            //only channel/plug items
            return;
        }

        if ((itemType.endsWith(".light") || itemType.endsWith(".color") || itemType.endsWith(".brightness")) && sid.equals(getItemSid(itemType))) {
            if (command instanceof OnOffType) {
                changeGatewayColor(command.equals(OnOffType.OFF) ? 0 : startColor);
            } else if (command instanceof HSBType) {
                HSBType hsb = (HSBType) command;
                long color = getRGBColor(hsb);
                changeGatewayColor(color);
            } else {
                if (rgb == 0)
                    return;

                //Percent type
                PercentType brightness = (PercentType) command;
                long currentBrightness = (rgb / 65536 / 256);
                long color = rgb - (currentBrightness * 65536 * 256) + brightness.longValue() * 65536 * 256;
                changeGatewayColor(color);
            }
            return;
        }

        if (itemType.endsWith(".plug")) {
            String sid = getItemSid(itemType);
            requestWrite(sid, new String[]{"status"}, new Object[]{command.toString().toLowerCase()});
        /*} else if (itemType.endsWith(".channel_0") || itemType.endsWith(".channel_1")) {
            //86ctrl_neutral1/2
            String sid = getItemSid(itemType);
            String channel = getItemChannel(itemType);
            requestWrite(sid, new String[]{channel}, new Object[]{command.toString().toLowerCase()});
        } else if (command.equals(OnOffType.ON) && (itemType.endsWith(".click") || itemType.endsWith(".double_click") || itemType.endsWith(".dual_channel.both_click"))) {
            //86sw1/2
            String sid = getItemSid(itemType);
            String channel = getItemChannel(itemType);
            String event = getItemEvent(itemType);
            requestWrite(sid, new String[]{channel}, new Object[]{event});*/
        } else {
            if (!command.equals(OnOffType.OFF))
                logger.error("Unsupported channel/event: " + itemType + " or command: " + command);
        }

    }

    private long getRGBColor(HSBType hsb) {
        long brightness = rgb / 65535 / 256;
        long red = (long) (hsb.getRed().floatValue() / 100 * 255);
        long green = (long) (hsb.getGreen().floatValue() / 100 * 255);
        long blue = (long) (hsb.getBlue().floatValue() / 100 * 255);
        return 65536 * 256 * brightness + 65536 * red + 256 * green + blue;
    }

    private void changeGatewayColor(long color) {
        requestWriteGateway(new String[]{"rgb"}, new Object[]{color});
    }

    private String getItemEvent(String itemType) {
        if (!itemType.contains("."))
            return "";
        int pos = itemType.lastIndexOf('.');
        return itemType.substring(pos + 1);
    }

    private String getItemChannel(String itemName) {
        if (!itemName.contains("."))
            return "";
        String[] parts = itemName.split("\\.");
        if (parts.length > 2)
            return parts[1];
        else
            return parts[0];
    }

    private String getItemSid(String itemType) {
        if (!itemType.contains("."))
            return "";
        return itemType.split("\\.")[0];
    }

    private String getItemType(String itemName) {
        for (final XiaomiGatewayBindingProvider provider : providers) {
            if (provider.getItemNames().contains(itemName))
                return provider.getItemType(itemName);
        }
        return "";
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected void internalReceiveUpdate(String itemName, State newState) {
        // the code being executed when a state was sent on the openHAB
        // event bus goes here. This method is only called if one of the
        // BindingProviders provide a binding for the given 'itemName'.
        logger.debug("internalReceiveUpdate({},{}) is called!", itemName, newState);
    }


}
