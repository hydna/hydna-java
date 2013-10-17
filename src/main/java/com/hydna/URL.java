package com.hydna;

public class URL {
    private short m_port = 80;
    private String m_path;
    private String m_host;
    private String m_token;
    private String m_auth;
    private String m_protocol = "http";
    private String m_error;
	
    private URL() {
    }
	
    static URL parse(String expr) {
        URL url = new URL();
        String host = expr;
        short port = 80;
        String path = "";
        String tokens = "";
        String auth = "";
        String protocol = "http";
        String error = "";
        int pos;
    
        // Expr can be on the form "http://auth@localhost:80/x00112233?token"
    
        // Take out the protocol
        pos = host.indexOf("://");
        if (pos != -1) {
            protocol = host.substring(0, pos);
            protocol = protocol.toLowerCase();
            host = host.substring(pos + 3);
        }
    
        // Take out the auth
        pos = host.indexOf("@");
        if (pos != -1) {
            auth = host.substring(0, pos);
            host = host.substring(pos + 1);
        }
    
        // Take out the token
        pos = host.lastIndexOf("?");
        if (pos != -1) {
            tokens = host.substring(pos + 1);
            host = host.substring(0, pos);
        }

        // Take out the path
        pos = host.indexOf("/");
        if (pos != -1) {
            path = host.substring(pos + 1);
            host = host.substring(0, pos);
        }

        // Take out the port
        pos = host.lastIndexOf(":");
        if (pos != -1) {
            try {
                port = Short.parseShort(host.substring(pos + 1), 10);
            } catch (NumberFormatException e) {
                error = "Could not read the port \"" + host.substring(pos + 1) + "\""; 
            }
    	
            host = host.substring(0, pos);
        }
	
        url.m_path = path;
        url.m_token = tokens;
        url.m_host = host;
        url.m_port = port;
        url.m_auth = auth;
        url.m_protocol = protocol;
        url.m_error = error;
	
        return url;
    }

    public short getPort() {
        return m_port;
    }

    public String getPath() {
        return m_path;
    }

    public String getHost() {
        return m_host;
    }

    public String getToken() {
        return m_token;
    }

    public String getAuth() {
        return m_auth;
    }

    public String getProtocol() {
        return m_protocol;
    }

    public String getError() {
        return m_error;
    }
}
