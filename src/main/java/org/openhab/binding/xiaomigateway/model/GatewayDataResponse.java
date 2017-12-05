package org.openhab.binding.xiaomigateway.model;

import com.google.gson.annotations.SerializedName;

public class GatewayDataResponse {
    //common
    private Number voltage;
    private String status;

    //Temp sensor
    private String temperature;
    private String humidity;
    private String pressure;

    //Smoke sensor
    private String density;

    //Gateway
    private String ip;
    private Number rgb;
    private Number illumination;

    //Plug
    private String inuse;
    @SerializedName("power_consumed")
    private String powerConsumed;
    @SerializedName("load_power")
    private String loadPower;

    //Button
    @SerializedName("channel_0")
    private String channel0;
    @SerializedName("channel_1")
    private String channel1;
    @SerializedName("dual_channel")
    private String dualChannel;

    //cube
    private String rotate;

    public Number getVoltage() {
        return voltage;
    }

    public String getTemperature() {
        return temperature;
    }

    public String getHumidity() {
        return humidity;
    }

    public String getPressure() {
        return pressure;
    }

    public String getIp() {
        return ip;
    }

    public Number getRgb() {
        return rgb;
    }

    public Number getIllumination() {
        return illumination;
    }

    public String getStatus() {
        return status;
    }

    public String getInuse() {
        return inuse;
    }

    /*
    public String getPowerConsumed() {
        return powerConsumed;
    }

    public String getLoadPower() {
        return loadPower;
    }

    public String getChannel0() {
        return channel0;
    }

    public String getChannel1() {
        return channel1;
    }

    public String getDualChannel() {
        return dualChannel;
    }*/

    public String getChannel(String channel) {
        switch(channel) {
            case "channel_0":
                return channel0;
            case "channel_1":
                return channel1;
            case "dual_channel":
                return dualChannel;
            default:
                return null;
        }
    }

    public String getPlugPowerValue(String event) {
        switch(event) {
            case "power_consumed":
                return powerConsumed;
            case "load_power":
                return loadPower;
            default:
                return null;
        }
    }

    public String getHTPSensorValue(String sensor) {
        switch (sensor) {
            case "temperature":
                return temperature;
            case "humidity":
                return humidity;
            case "pressure":
                return pressure;
            default:
                return null;
        }
    }

    public String getRotate() {
        return rotate;
    }

    public String getDensity() {
        return density;
    }
}
