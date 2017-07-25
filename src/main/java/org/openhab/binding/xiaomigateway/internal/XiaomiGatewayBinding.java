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
import java.net.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.openhab.binding.xiaomigateway.internal.EncryptionHelper.parseHexBinary;

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
    private DatagramSocket udpSocket = null;

    private Thread thread;
    private Thread miioThread;

    Map<Integer, MiioDevice> miioDeviceList = new HashMap();
    Map<Integer, String> miioTokenList = new HashMap();

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

    private boolean first = true;
    private boolean second = true;

    //Gson parser
    private JsonParser parser = new JsonParser();


    byte[] buffer = new byte[BUFFER_LENGTH];
    byte[] bufferMiio = new byte[BUFFER_LENGTH];
    DatagramPacket dgram = new DatagramPacket(buffer, buffer.length);

    DatagramPacket dgramMiio = new DatagramPacket(bufferMiio, bufferMiio.length);

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

        //yeelink.light.strip1
        //miioTokenList.put(50335780, "cea635800e253b972bb05c42ebfad419");

        //rockrobo.vacuum.v1
        miioTokenList.put(64506184, "424d6a5077556d7667714535686c4933");

        //lumi.gateway.v3
        //miioTokenList.put(44687093, "7d146de2d48ad9b4260082e7ce725a4e");

        //philips.light.sread1
        //miioTokenList.put(46552086, "15ce1573b9b7597a97cad8bd57fdd83c");

        //xiaomi.wifispeaker.v1
        //miioTokenList.put(54664169, "636a356945696c6c76566d48324e6d48");

        decryptMessage("213100600000000003d8494859764ec623be32157138c3fe696f73461966bd668fd5c9147147500b71326f9116d2f63d5d62e3fd91a4d22dd12bbc626ce229ec7e3eba192602de4abfcd2398060b6963e906365bed60f8d522a1307db49bae73","424d6a5077556d7667714535686c4933");
        decryptMessage("213100600000000003d8494859764ec695723fc78b775d7fca234672d87a98b11131f4f616cc9608f0fb5c141057c6caed6df21c0c668937cfe458d77ea89701015219e113a3e2fce1e0c373cd7b927156f83fc545a3f40923731bb085582507", "424d6a5077556d7667714535686c4933");
        decryptMessage("213100500000000003d8494859764ec7a3c3bb3f957670b791ce2f114a11580a9fbf349c97228bb509ff430da9f1ccf7d5d20a340bc4fbb4acd798eef9fa2b28fb7c22c333090ac4d1319194f1a354d2", "424d6a5077556d7667714535686c4933");
        decryptMessage("213100500000000003d8494859764ec8aa733bea7b1dbd5c12c185187abe79b1544c60b0d21b37fb3349383764d9a8aebdfa42d7ffd6cc7b52eae4062520fa88af8cdcbeb97d8c224d33f84310a2515c", "424d6a5077556d7667714535686c4933");
        /*
        decryptMessage("213100600000000003421be9597633d77ec5f720a61d90278400d4ce5874870b6b564d0f4cceba45d3fbe8c137bfe949eb00636a9ec7b8db883614b0e3f52886b4aa03c4254ec13ed5f214fed5cdf00063920304189b6c4a825095e14239fedd", "636a356945696c6c76566d48324e6d48");
        decryptMessage("213101700000000003421be9597633d7b280b75757e56ff2b65159f4c4f3e3dc408ff935318fbdcaa860b25ce564d41df52a2a66a6e852f56a8d416eb7bf8b62bd8d6fafffc76b90f2941ca4f7be8dff8e4e7b847b710429ed8da1a6f728aec405e989f4018672c66ee1f723cf9d05dc2946160ea4256b42a391e584cc9a1c812400eda4ec60bfe427f8d8b68b004c51a1e33f447f80a6f6977506f1110a2740a86eb788d0a14aba7373177310a86dafb4deefd4026a8d490e80819c94a97c07bd54a1674592419fe2d005cff41c07623c0b7cde606c025d691ab92f3abde620acce151f081bb7ecc219bd3a5a937d75d31b9d56e8e170ed58c13d24dafc9912741aaaace658d6a69be7ca0148d4e1399375553945116c5445391149cc2ca39e8d7e5f0e838eb2e9e82450bac438ba7f253e8a037cc4a9c6596d725ec2bde1d7b0a8ad4db6cbc8e1e996857e3b2971d1a3e1a29ea7f2d5cd3578b88d6f9345d3f1998493c59029b89a79aea29196286d", "636a356945696c6c76566d48324e6d48");
        decryptMessage("213100500000000003421be9597633da0070dcdbd979e639a6502de16e6ab7fb07fe3ef16089a391e58103cdd589e8ce54b51d410302c2b9d9a9bbb972fc2920b86aea3c1d6b6cbf0790e5c0e33ecc5a", "636a356945696c6c76566d48324e6d48");
        decryptMessage("213100400000000003421be9597633da4e166bf23f01b60dd680a191bf09cb45d8a007f220dd0b53806a1c8dccde5d80a48e3a77e5d13d2795e4437b10f30f1a","636a356945696c6c76566d48324e6d48");
        decryptMessage("213100500000000003421be9597633e2f12c1fc9ad89683fe9a41ef314a567684ed6904f75397808f40e79d1d4f8bdd9e64d65ad99a310b6176ab0704ecf6f4c297b80b6e48039c19bbd5f0f83b141a7","636a356945696c6c76566d48324e6d48");
        decryptMessage("213100500000000003421be959763da3816a0566cd0254fe78557838c116d8ac451a35907bc06002b070d1ed9d6e1e9245272410d6324f6825549c4d571a3af00d5b7506df92c0825d0217090f99de00","636a356945696c6c76566d48324e6d48");
        */
    }

    private void decryptMessage(String message, String token) {
        byte[] toDecrypt = parseHexBinary(message);
        //MiioMessage msg = new MiioMessage(parseHexBinary("636a356945696c6c76566d48324e6d48"), toDecrypt);
        MiioMessage msg = new MiioMessage(toDecrypt, token);
        logger.info("decrypted message: {}", new String(msg.getDecryptedData()));
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
            udpSocket = new DatagramSocket();
            //udpSocket.joinGroup(InetAddress.getByName("255.255.255.255"));
        } catch (IOException e) {
            logger.error(e.toString());
        }

        thread = new Thread(new Runnable() {
            public void run() {
                receiveData(socket, dgram);
            }
        });
        thread.start();

        miioThread = new Thread(new Runnable() {
            public void run() {
                receiveMiioData(udpSocket, dgramMiio);
            }
        });
        miioThread.start();


    }

    private void receiveMiioData(DatagramSocket udpSocket, DatagramPacket dgram) {
        while (true) {
            try {
                udpSocket.receive(dgram);
                String sentence = new String(dgram.getData(), 0,
                        dgram.getLength());

                byte[] response = Arrays.copyOf(dgram.getData(), dgram.getLength());
                logger.debug("Received udp packet: {}", EncryptionHelper.bytesToHex(response));

                int id = (int) MiioMessage.readUInt32(response, 8);
                logger.debug("Received udp packet from device: {}", id);

                if (miioTokenList.containsKey(id)) {
                    logger.info("Device token is: {}", miioTokenList.get(id));
                    MiioMessage miio = new MiioMessage(response, miioTokenList.get(id));
                    int stamp = miio.getStamp();

                    if (!miioDeviceList.containsKey(id)) {
                        MiioDevice newDevice = new MiioDevice(id, miioTokenList.get(id), dgram.getAddress(), stamp);
                        miioDeviceList.put(id, newDevice);
                        //logger.info("Added device: {}", newDevice.toString());
                        //if (miio.isHandshakeResponse() ) {
                        logger.info("The message is a handshake one!");
                        Thread.sleep(1000);
                        requestTestWrite(id);
                        first = false;
                        //}
                    } else {
                        // update stamp
                        miioDeviceList.get(id).setStamp(stamp);
                    }

                    if (miio.hasChecksum() && miio.isValid()) {
                        logger.info("The message has valid checksum!");
                        byte[] decrypted = miio.getDecryptedData();
                        logger.info("Decrypted message: {}", new String(decrypted));

                        Thread.sleep(1000);
                        if (second) {
                            requestTestWrite2(id);
                            second = false;
                        }
                    }


                } else {
                    logger.warn("Cannot find token for device: {}", id);
                }
            } catch (SocketException e) {
                logger.error("receiveMiioData exception: {}", e.toString());
                if (udpSocket.isClosed()) {
                    return;
                }
            } catch (Exception ex) {
                logger.error("receiveMiioData exception: {}", ex.toString());
            }
        }
    }


    private void receiveData(MulticastSocket socket, DatagramPacket dgram) {
        while (true) {
            try {
                socket.receive(dgram);
                String sentence = new String(dgram.getData(), 0,
                        dgram.getLength());

                logger.debug("Received packet: {}", sentence);

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
                        logger.error("Received write command which is designed for the gateway. Are you sure you have the right developer key? {}", sentence);
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
                        processOtherCommands(jobject);
                        break;
                    default:
                        logger.error("Unknown Xiaomi gateway command: {}", command);
                }
            } catch (SocketException ex) {
                logger.error("receiveData exception: {}", ex.toString());
                if (socket.isClosed()) {
                    return;
                }
            } catch (Exception e) {
                logger.error("receiveData exception: {}", e.toString());
            }
        }
    }

    private void addDevice(String newId, String model) {
        if (!devicesList.containsKey(newId)) {
            logger.info("Detected new Xiaomi smart device - sid: {} model: {}", newId, model);
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
            case "pressure":
                if (isPressureEvent(jobject)) {
                    logger.debug("Processing pressure event");
                    processPressureEvent(itemName, jobject);
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
            case "switch":
                if (isWallSwitchEvent(jobject, type)) {
                    logger.debug("Processing wall switch event");
                    processWallSwitchEvent(itemName, type, jobject);
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
                if (isCommonPlugEvent(jobject)) {
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
                if (isCommonPlugEvent(jobject)) {
                    logger.debug("Processing plug power_consumed event");
                    processPlugPowerConsumedEvent(itemName, jobject);
                }
                break;
            case "load_power":
                if (isCommonPlugEvent(jobject)) {
                    logger.debug("Processing plug load_power event");
                    processPlugLoadPowerEvent(itemName, jobject);
                }
                break;
            case "voltage":
                if (hasVoltage(jobject)) {
                    logger.debug("Processing voltage event");
                    processVoltageEvent(itemName, jobject);
                }
                break;
            case "alarm":
                if (isAlarmEvent(jobject)) {
                    logger.debug("Processing alarm event");
                    processAlarmEvent(itemName, jobject);
                }
                break;
            default:
                if (isCubeEvent(jobject)) {
                    processCubeEvent(itemName, type, jobject);
                }
        }
    }

    private void processWallSwitchEvent(String itemName, String itemType, JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            String channel = getItemChannel(itemType);
            if (jo == null || !(jo.has(channel)))
                return;
            String value = jo.get(channel).getAsString().toLowerCase();
            State oldValue = itemRegistry.getItem(itemName).getState();
            State newValue = value.equals("on") ? OnOffType.ON : OnOffType.OFF;
            if (!newValue.equals(oldValue))
                eventPublisher.postUpdate(itemName, newValue);
        } catch (Exception ex) {
            logger.error(ex.toString());
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

        if (event == null) {
            //it has no event data, maybe voltage only?
            return;
        }

        boolean publish = false;
        if (isRotateCubeEvent(jobject)) {
            event = isLeftRotate(jobject) ? "rotate_left" : "rotate_right";
        }
        logger.debug("XiaomiGateway: processing cube event {}", event);
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
                logger.error("Unknown cube event: {}", event);
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

    private boolean isCommonPlugEvent(JsonObject jobject) {
        return checkModel(jobject, "plug") || checkModel(jobject, "86plug");
    }

    private boolean isAlarmEvent(JsonObject jobject) {
        return checkModel(jobject, "smoke") || checkModel(jobject, "natgas");
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
        logger.info("Discovered Xiaomi Gateway - sid: {} ip: {} port: {}", sid, gatewayIP, dest_port);
    }

    private void listIds(JsonObject jobject) {
        String data = jobject.get("data").getAsString();
        JsonArray ja = parser.parse(data).getAsJsonArray();
        if (devicesList.size() == 0)
            logger.info("Discovered total of {} Xiaomi smart devices", ja.size());
        requestRead(sid);
        for (JsonElement je : ja) {
            requestRead(je.getAsString());
        }
    }

    private void listDevice(JsonObject jobject) {
        String newId = jobject.get("sid").getAsString();
        String model = jobject.get("model").getAsString();
        addDevice(newId, model);
        processOtherCommands(jobject);
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

    private void processAlarmEvent(String itemName, JsonObject jobject) {
        String data = jobject.get("data").getAsString();
        JsonObject jo = parser.parse(data).getAsJsonObject();
        String stat = (jo.has("status")) ? jo.get("status").getAsString().toLowerCase() : "0";
        State oldValue;
        try {
            oldValue = itemRegistry.getItem(itemName).getState();
            State newValue = new DecimalType(Integer.parseInt(stat));
            if (!newValue.equals(oldValue))
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
            newValue = new DecimalType(jo.get(event).getAsDouble());
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
        processSensorHTPEvent(itemName, jobject, "temperature");
    }

    private void processHumidityEvent(String itemName, JsonObject jobject) {
        processSensorHTPEvent(itemName, jobject, "humidity");
    }

    private void processPressureEvent(String itemName, JsonObject jobject) {
        processSensorHTPEvent(itemName, jobject, "pressure");
    }

    private void processSensorHTPEvent(String itemName, JsonObject jobject, String sensor) {
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
            return checkModel(jobject, "switch") && jo != null && jo.has("status") && jo.get("status").getAsString().equals(click);
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private boolean isSwitchEvent(JsonObject jobject, String itemType, String click) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            String channel = getItemChannel(itemType);
            return (checkModel(jobject, "86sw1") || checkModel(jobject, "86sw2")) && jo != null && jo.has(channel) && jo.get(channel).getAsString().equals(click);
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private boolean isWallSwitchEvent(JsonObject jobject, String itemType) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            String channel = getItemChannel(itemType);
            return (checkModel(jobject, "ctrl_ln1") || checkModel(jobject, "ctrl_ln2")) && jo != null && jo.has(channel);
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private boolean isDualSwitchEvent(JsonObject jobject, String itemType) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            String channel = getItemChannel(itemType);
            return checkModel(jobject, "86sw2") && channel.equals("dual_channel") && jo != null && jo.has(channel) && jo.get(channel).getAsString().equals("both_click");
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private boolean isTemperatureEvent(JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            return (checkModel(jobject, "sensor_ht") || checkModel(jobject, "weather.v1")) && jo.has("temperature");
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private boolean isHumidityEvent(JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            return (checkModel(jobject, "sensor_ht") || checkModel(jobject, "weather.v1")) && jo.has("humidity");
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private boolean isPressureEvent(JsonObject jobject) {
        try {
            String data = jobject.get("data").getAsString();
            JsonObject jo = parser.parse(data).getAsJsonObject();
            return checkModel(jobject, "weather.v1") && jo.has("pressure");
        } catch (Exception ex) {
            logger.error(ex.toString());
            return false;
        }
    }

    private Float formatValue(String value) {
        if (value.length() > 1) {
            return Float.parseFloat(value.substring(0, value.length() - 2) + "." + value.substring(2));
        } else {
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

        if (this.udpSocket != null)
            udpSocket.close();
        if (miioThread != null && miioThread.isAlive())
            miioThread.interrupt();
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
        logger.debug("execute() method is called!");
        if (!bindingsExist()) {
            return;
        }

        if (first) {
            discoverMiioDevices();
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
            logger.debug("Sending to device: {} message: {}", device, sendString);
            socket.send(sendPacket);
        } catch (IOException e) {
            logger.error(e.toString());
        }
    }

    private void discoverMiioDevices() {
        try {
            byte[] sendData = parseHexBinary("21310020ffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
            InetAddress addr = InetAddress.getByName("255.255.255.255");
            //InetAddress addr = InetAddress.getByName("192.168.2.43");
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, addr, 54321);
            logger.debug("Sending handshake ...");
            udpSocket.send(sendPacket);
        } catch (IOException e) {
            logger.error(e.toString());
        }
    }

    private void requestTestWrite(int id) {
        MiioDevice device = miioDeviceList.get(id);
        //String sendString = "{\"id\":" + device.getMessageId() + ",\"method\":\"miIO.info\",\"params\":[]}";
        //String sendString = "{\"id\":" + device.getMessageId() + ",\"method\":\"get_prop\",\"params\":[\"umi\"]}";
        String sendString = "{\"id\":" + device.getMessageId() + ",\"method\":\"get_consumable\",\"params\":[]}";

        logger.info("Sending message: {}", sendString);
        int stamp = device.getStamp();
        int secondsPassed = device.getSecondsPassed();
        logger.info("Seconds passed: {}", secondsPassed);

        requestMiioWrite(sendString, id, stamp + secondsPassed);
    }

    private void requestTestWrite2(int id) {
        MiioDevice device = miioDeviceList.get(id);
        //String sendString = "{\"id\":" + device.getMessageId() + ",\"method\":\"set_power\",\"params\":[\"Off\"]}";
        //String sendString = "{\"id\":" + device.getMessageId() + ",\"method\":\"power\",\"params\":[]}";
        //String sendString = "{\"id\":" + device.getMessageId() + ",\"method\":\"next_channel\",\"params\":[]}";
        String sendString = "{\"id\":" + device.getMessageId() + ",\"method\":\"get_status\",\"params\":[]}";
        int stamp = device.getStamp();

        logger.info("Sending message: {}", sendString);
        int secondsPassed = device.getSecondsPassed();
        logger.info("Seconds passed: {}", secondsPassed);
        requestMiioWrite(sendString, id, stamp + secondsPassed);
    }

    private void requestMiioWrite(String sendString, int deviceId, int stamp) {
        try {

            MiioDevice device = miioDeviceList.get(deviceId);
            InetAddress addr = device.getAddress();
            MiioMessage msg = new MiioMessage(device.getToken());
            byte[] toSend = msg.createEncryptedMessage(sendString, deviceId, stamp);
            //logger.info("Decrypted data for sending: {}", new String(msg.getDecryptedData()));


            //logger.info("data length: {}", toSend.length);

            //logger.info("message size from header: {}", EncryptionHelper.readUInt16(header, 2));
            //logger.info("Header after: {}", EncryptionHelper.bytesToHex(finalHeader));


            DatagramPacket sendPacket = new DatagramPacket(toSend, toSend.length, addr, 54321);
            udpSocket.send(sendPacket);
        } catch (Exception e) {
            logger.error("requestMiio write exception: {}", e.toString());
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
            logger.debug("Sending to gateway: {} message: {}", sid, sendString);
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
                logger.error("Unsupported channel/event: {} or command: {}", itemType, command);
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
