/*
 * Copyright (C) 2011-2012 Intel Corporation
 * All rights reserved.
 */
package test.io;

import com.intel.mountwilson.as.hostmanifestreport.data.HostManifestReportType;
import com.intel.mountwilson.as.hosttrustreport.data.HostsTrustReportType;
import com.intel.mtwilson.api.*;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jbuhacoff
 */
public class XmlTest {
    private static Logger log = LoggerFactory.getLogger(XmlTest.class);
    protected static final ObjectMapper mapper = new ObjectMapper();

    // copy of ApiClient.fromXML ... should probably be copied to a utility class XmlUtil in a new module cpg-xml or  JaxbUtil in a new module cpg-jaxb 
    private <T> T fromXML(String document, Class<T> valueType) throws IOException, ApiException, JAXBException {
        JAXBContext jc = JAXBContext.newInstance( valueType );
        Unmarshaller u = jc.createUnmarshaller();
        Object o = u.unmarshal( new StreamSource( new StringReader( document ) ) );
        return (T)o;
    }
    
//    @Test
    public void testDeserializeXmlToHostTrustReport() throws IOException, ApiException, JAXBException {
        String xml = IOUtils.toString(getClass().getResourceAsStream("/HostTrustReportSample.xml"));
        HostsTrustReportType a = fromXML(xml, HostsTrustReportType.class);
        List<com.intel.mountwilson.as.hosttrustreport.data.HostType> list = a.getHost();
        for(com.intel.mountwilson.as.hosttrustreport.data.HostType h : list) {
            System.out.println(h.getHostName()+" "+h.getMLEInfo()+" trusted?"+h.getTrustStatus());            
        }
    }
    
//    @Test
    public void testDeserializeXmlToHostManifestReport() throws IOException, ApiException, JAXBException {
        String xml = IOUtils.toString(getClass().getResourceAsStream("/HostManifestReportSample.xml"));
        HostManifestReportType a = fromXML(xml, HostManifestReportType.class);
        com.intel.mountwilson.as.hostmanifestreport.data.HostType h = a.getHost();
        List<com.intel.mountwilson.as.hostmanifestreport.data.ManifestType> list = h.getManifest();
        for(com.intel.mountwilson.as.hostmanifestreport.data.ManifestType m : list) {
            System.out.println(m.getName()+" "+m.getValue()+" trusted?"+m.getTrustStatus());            
        }
    }
}
