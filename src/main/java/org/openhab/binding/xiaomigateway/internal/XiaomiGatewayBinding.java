/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.xiaomigateway.internal;

import java.io.IOException;
import java.net.*;
import java.util.Map;

import com.google.gson.*;
import org.openhab.binding.xiaomigateway.XiaomiGatewayBindingProvider;

import org.apache.commons.lang.StringUtils;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

    //Configuration
    private String key = "";

    //Gateway info
    private String sid = "";
    private String token = "";

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
    private long refreshInterval = -1;


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


        // to override the default refresh interval one has to add a
        // parameter to openhab.cfg like <bindingName>:refresh=<intervalInMs>
        String refreshIntervalString = (String) configuration.get("refresh");
        if (StringUtils.isNotBlank(refreshIntervalString)) {
            refreshInterval = Long.parseLong(refreshIntervalString);
        }
        String keyString = (String) configuration.get("key");
        if (StringUtils.isNotBlank(refreshIntervalString)) {
            key = keyString;
        }

        // read further config parameters here ...

        setupSocket();
        discoverGateways();
        //setProperlyConfigured(true);
    }

    private void discoverGateways() {
        try {
            String sendString = "{\"cmd\": \"whois\"}";
            byte[] sendData = sendString.getBytes("UTF-8");
            InetAddress addr = InetAddress.getByName(MCAST_ADDR);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, addr, MCAST_PORT);

            socket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void setupSocket() {


        try {
            socket = new MulticastSocket(dest_port); // must bind receive side
            socket.joinGroup(InetAddress.getByName(MCAST_ADDR));
        } catch (IOException e) {
            e.printStackTrace();
        }

        thread = new Thread(new Runnable() {
            public void run() {
                receiveData(socket, dgram);
            }
        });
        thread.start();
    }

    private void receiveData(MulticastSocket socket, DatagramPacket dgram) {

        try {
            while (true) {
                socket.receive(dgram);
                String sentence = new String(dgram.getData(), 0,
                        dgram.getLength());

                logger.info("Xiaomi received packet: " + sentence);

                JsonObject jobject = parser.parse(sentence).getAsJsonObject();
                String command = jobject.get("cmd").getAsString();

                if (command.equals("iam")) {
                    getGatewayInfo(jobject);
                    requestIdList();
                    continue;
                }
                if (command.equals("get_id_list_ack")) {
                    token = jobject.get("token").getAsString();
                    listIds(jobject);
                    continue;
                }
                if (command.equals("read_ack")) {
                    listDevice(jobject);
                    processOtherCommands(jobject);
                    continue;
                }
                if (command.equals("heartbeat") && jobject.get("model").getAsString().equals("gateway")) {
                    token = jobject.get("token").getAsString();
                    continue;
                }

                //report and non gateway heartbeat
                processOtherCommands(jobject);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processOtherCommands(JsonObject jobject) {
        //command report
        String sid = jobject.get("sid").getAsString();

        for (final XiaomiGatewayBindingProvider provider : providers) {
            for (String itemName : provider.getItemNames()) {
                String type = provider.getItemType(itemName);
                if(!type.startsWith(sid))
                    continue;

                if (type.endsWith("temperature") && isTemperatureEvent(jobject)) {
                    logger.debug("XiaomiGateway: processing temperature event");
                    processTemperatureEvent(itemName, jobject);
                }
                if (type.endsWith("humidity") && isHumidityEvent(jobject)) {
                    logger.debug("XiaomiGateway: processing humidity event");
                    processHumidityEvent(itemName, jobject);
                }
                if (type.endsWith(".virtual_switch") && isButtonEvent(jobject, "click")) {
                    logger.debug("XiaomiGateway: processing virtual switch click event");
                    processVirtualSwitchEvent(itemName);
                }
                if (type.endsWith(".click") && isButtonEvent(jobject, "click")) {
                    logger.debug("XiaomiGateway: processing click event");
                    eventPublisher.postUpdate(itemName, OnOffType.ON);
                }
                if (type.endsWith(".double_click") && isButtonEvent(jobject, "double_click")) {
                    logger.debug("XiaomiGateway: processing double click event");
                    eventPublisher.postUpdate(itemName, OnOffType.ON);
                }
                if (type.endsWith(".long_click") && isButtonEvent(jobject, "long_click_press")) {
                    logger.debug("XiaomiGateway: processing long click event");
                    eventPublisher.postUpdate(itemName, OnOffType.ON);
                }
                if (type.endsWith(".magnet") && isMagnetEvent(jobject)) {
                    logger.debug("XiaomiGateway: processing magnet event");
                    processMagnetEvent(itemName, jobject);
                }

            }

        }
    }

    private void getGatewayInfo(JsonObject jobject) {
        sid = jobject.get("sid").getAsString();
        dest_port = jobject.get("port").getAsInt();
        gatewayIP = jobject.get("ip").getAsString();
        logger.info("Discovered Xiaomi Gateway - sid: " + sid + " ip: " + gatewayIP + " port: " + dest_port);
        setProperlyConfigured(true);
    }

    private void listIds(JsonObject jobject) {
        String data = jobject.get("data").getAsString();
        JsonArray ja = parser.parse(data).getAsJsonArray();
        logger.info("Discovered total of " + ja.size() + " Xiaomi smart devices");
        for (JsonElement je : ja) {
            requestRead(je.getAsString());
        }
    }

    private void listDevice(JsonObject jobject) {
        String sid = jobject.get("sid").getAsString();
        String model = jobject.get("model").getAsString();
        logger.info("Detected Xiaomi smart device - sid: " + sid + " model: " + model);
    }

    private void processVirtualSwitchEvent(String itemName) {
        State oldValue;
        State newState = OnOffType.OFF;
        try {
            oldValue = itemRegistry.getItem(itemName).getState();
            newState = oldValue.equals(OnOffType.ON) ? OnOffType.OFF : OnOffType.ON;
        } catch (ItemNotFoundException e) {
            e.printStackTrace();
        }
        eventPublisher.postUpdate(itemName, newState);
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
            e.printStackTrace();
        }

    }

    private void processTemperatureEvent(String itemName, JsonObject jobject) {
        String data = jobject.get("data").getAsString();
        JsonObject jo = parser.parse(data).getAsJsonObject();
        Float temp = formatValue(jo.get("temperature").getAsString());
        eventPublisher.postUpdate(itemName, new DecimalType(temp));
    }

    private void processHumidityEvent(String itemName, JsonObject jobject) {
        String data = jobject.get("data").getAsString();
        JsonObject jo = parser.parse(data).getAsJsonObject();
        Float hum = formatValue(jo.get("humidity").getAsString());
        eventPublisher.postUpdate(itemName, new DecimalType(hum));
    }

    private boolean isMagnetEvent(JsonObject jobject) {
        return jobject != null && !jobject.isJsonNull() && jobject.get("model").getAsString().equals("magnet");
    }

    private boolean isButtonEvent(JsonObject jobject, String click) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            if(jo == null || jo.get("status") == null)
                return false;
            return jobject != null && !jobject.isJsonNull() && jobject.get("model").getAsString().equals("switch") && jo.get("status").getAsString().equals(click);
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private boolean isTemperatureEvent(JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            return jobject != null && !jobject.isJsonNull() && jobject.get("model").getAsString().equals("sensor_ht") && jo.get("temperature") != null;
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private boolean isHumidityEvent(JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            return jobject != null && !jobject.isJsonNull() && jobject.get("model").getAsString().equals("sensor_ht") && jo.get("humidity") != null;
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private Float formatValue(String value) {
        if (value.length() == 4)
            return Float.parseFloat(value.substring(0, 2) + "." + value.substring(2));
        else
            return Float.parseFloat(value);
    }

    /**
     * Called by the SCR when the configuration of a binding has been changed through the ConfigAdmin service.
     *
     * @param configuration Updated configuration properties
     */
    public void modified(final Map<String, Object> configuration) {
        // update the internal configuration accordingly
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
    }

    private void requestIdList() {
        try {
            String sendString = "{\"cmd\": \"get_id_list\"}";
            byte[] sendData = sendString.getBytes("UTF-8");
            InetAddress addr = InetAddress.getByName(gatewayIP);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, addr, dest_port);

            socket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
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
            e.printStackTrace();
        }
    }

    private void requestWrite(String device, String[] keys, Object[] values) {
        try {
            String sendString = "{\"cmd\": \"write\", \"sid\": \"" + device + "\", \"data\": {" + getData(keys, values) + ", \"key\": " + getKey() + "}}";
            byte[] sendData = sendString.getBytes("UTF-8");
            InetAddress addr = InetAddress.getByName(gatewayIP);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, addr, dest_port);
            logger.info("Sending to device: " + device + " message: " + sendString );
            socket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
    private void requestWriteGateway(String device, String[] keys, Object[] values) {
        try {
            String key = getKey();
            String sendString = "{\"cmd\": \"write\", \"model\": \"gateway\", \"sid\": \"" + device + "\", \"short_id\": \"0\", \"key\": \"" + key + "\", \"data\": \"{" + getData(keys, values) + ",\"key\":\\\"" + key + "\\\"}\"}";
            byte[] sendData = sendString.getBytes("UTF-8");
            InetAddress addr = InetAddress.getByName(gatewayIP);
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, addr, dest_port);
            logger.info("Sending to gateway: " + device + " message: " + sendString );
            socket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    */

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
        logger.info("Encrypting \"" + token + "\" with key \"" + key + "\"" );
        return EncryptionHelper.encrypt(token, key);
    }

    private String getValue(Object o) {
        if (o instanceof String) {
            return "\"" + o + "\"";
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
