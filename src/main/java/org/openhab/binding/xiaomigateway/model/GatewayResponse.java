package org.openhab.binding.xiaomigateway.model;

public class GatewayResponse {
    private String cmd;
    private String model;
    private String sid;
    private String port;
    private String ip;

    private String token;
    private String data;

    public String getCmd() {
        return cmd;
    }

    public String getModel() {
        return model;
    }

    public String getSid() {
        return sid;
    }

    public String getToken() {
        return token;
    }

    public String getData() {
        return data;
    }

    public String getPort() {
        return port;
    }

    public String getIp() {
        return ip;
    }
}
