/*
 * Copyright (C) 2012 B3Partners B.V.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package nl.b3p.viewer.stripes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.sourceforge.stripes.action.*;
import net.sourceforge.stripes.validation.Validate;
import nl.b3p.viewer.config.services.ArcIMSService;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Matthijs Laan
 */
@UrlBinding("/action/proxy/{mode}")
@StrictBinding
public class ProxyActionBean implements ActionBean {
    private ActionBeanContext context;
    
    @Validate
    private String url;
    
    @Validate
    private String mode;

    @Override
    public ActionBeanContext getContext() {
        return context;
    }

    @Override
    public void setContext(ActionBeanContext context) {
        this.context = context;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
    
    @DefaultHandler
    public Resolution proxy() throws Exception {

        HttpServletRequest request = getContext().getRequest();
        
        // Session must exist
        HttpSession sess = request.getSession(false);
        if(sess == null || url == null) {
            return new ErrorResolution(HttpServletResponse.SC_FORBIDDEN, "Proxy requests forbidden");
        }
        
        // TODO maybe add some other checks against illegal proxy use
        
        // We don't do a host check because the user can add custom services
        // using any URL. If the proxying viewer webapp is on a IP whitelist
        // and an attacker knows the URL of the IP-whitelist protected service 
        // this may allow the attacker to request maps from that service if that
        // service does not verify IP using the X-Forwarded-For header we send.
        
        // We only proxy ArcIMS for OpenLayers for the moment
        if(! ArcIMSService.PROTOCOL.equals(mode)) {
            return new ErrorResolution(HttpServletResponse.SC_FORBIDDEN, "Proxy mode unacceptable");
        } else {
            return proxyArcIMS();
        }
    }

    // Not public, proxy() performs proxy checks!
    private Resolution proxyArcIMS() throws Exception {

        HttpServletRequest request = getContext().getRequest();

        if(!"POST".equals(request.getMethod())) {
            return new ErrorResolution(HttpServletResponse.SC_FORBIDDEN);
        }   
        
        Map params = new HashMap(getContext().getRequest().getParameterMap());
        // Only allow these parameters in proxy request
        params.keySet().retainAll(Arrays.asList(
                "ClientVersion",
                "Encode",
                "Form",
                "ServiceName"
        ));
        URL theUrl = new URL(url);
        // Must not allow file / jar etc protocols, only HTTP:
        String path = theUrl.getPath();
        for(Map.Entry<String,String[]> param: (Set<Map.Entry<String,String[]>>)params.entrySet()) {
            if(path.length() == theUrl.getPath().length()) {
                path += "?";
            } else {
                path += "&";
            }
            path += URLEncoder.encode(param.getKey(), "UTF-8") + "=" + URLEncoder.encode(param.getValue()[0], "UTF-8");
        }
        theUrl = new URL("http", theUrl.getHost(), theUrl.getPort(), path);
        
        // TODO logging for inspecting malicious proxy use
        
        ByteArrayOutputStream post = new ByteArrayOutputStream();
        IOUtils.copy(request.getInputStream(), post);
        
        // This check makes some assumptions on how browsers serialize XML
        // created by OpenLayers' ArcXML.js write() function (whitespace etc.),
        // but all major browsers pass this check
        if(!post.toString("US-ASCII").startsWith("<ARCXML version=\"1.1\"><REQUEST><GET_IMAGE")) {
            return new ErrorResolution(HttpServletResponse.SC_FORBIDDEN);
        }

        final HttpURLConnection connection = (HttpURLConnection)theUrl.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setAllowUserInteraction(false);
        connection.setRequestProperty("X-Forwarded-For", request.getRemoteAddr());
        
        connection.connect();
        try { 
            IOUtils.copy(new ByteArrayInputStream(post.toByteArray()), connection.getOutputStream());
        } finally {
            connection.getOutputStream().flush();
            connection.getOutputStream().close();        
        }
        
        return new StreamingResolution(connection.getContentType()) {
            @Override
            protected void stream(HttpServletResponse response) throws IOException {
                try {
                    IOUtils.copy(connection.getInputStream(), response.getOutputStream());
                } finally {
                    connection.disconnect();
                }
                
            }
        };
    }    
}
