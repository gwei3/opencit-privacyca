package com.intel.mtwilson.as.business.trust;

import com.intel.mountwilson.as.common.ASException;
import com.intel.mtwilson.My;
import com.intel.mtwilson.as.business.HostBO;
import com.intel.mtwilson.as.data.TblHosts;
import com.intel.mtwilson.as.helper.ASComponentFactory;
import com.intel.mtwilson.crypto.CryptographyException;
import com.intel.mtwilson.datatypes.*;
import com.intel.mtwilson.model.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author dsmagadx
 */
public class HostTest {
    //private static final HostTrustBO htbo = new HostTrustBO();
    private static final String knownHost = "10.1.71.149";
    private static HostBO hostBO;
    private static HostTrustBO hostTrustBO;
    private static ObjectMapper mapper = new ObjectMapper();
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    @BeforeClass
    public static void createBusinessObject()  {
        hostBO = new HostBO();
        hostTrustBO = new ASComponentFactory().getHostTrustBO();
    }

    @AfterClass
    public static void releaseBusinessObject()  {
        hostBO = null;
        hostTrustBO = null;
    }

    
    @Test
    public void testCreateTxtHostFromTblHostsRecord() throws CryptographyException, IOException, MalformedURLException {
        TblHosts tblHosts = My.jpa().mwHosts().findByName("10.1.71.149"); //new ASComponentFactory().getHostBO().getHostByName(new Hostname("10.1.71.149"));
        log.debug("tblhosts addon connection string length: {}", tblHosts.getAddOnConnectionInfo() == null ? "NULL" : tblHosts.getAddOnConnectionInfo().length());
        TxtHostRecord txtHostRecord = hostTrustBO.createTxtHostRecord(tblHosts);
        log.debug("txthostrecord addon connection string length: {}", txtHostRecord.AddOn_Connection_String == null ? "NULL" : txtHostRecord.AddOn_Connection_String.length());
        
        // now create a TxtHost from the TxtHostRecord
        TxtHost txtHost = new TxtHost(txtHostRecord);
        log.debug("txthost addon connection string length: {}", txtHost.getAddOn_Connection_String() == null ? "NULL" : txtHost.getAddOn_Connection_String().length());        
    }

    /**
     * The known host 10.1.71.103 should have trust status BIOS:0,VMM:0
     * This is not a good unit test for AS, should be moved to integration test project
     */
    @Test
    public void testGetTrustStatusForKnownHost() throws IOException {
        HostTrustBO htbo = new ASComponentFactory().getHostTrustBO();
        HostTrustStatus response = htbo.getTrustStatus(new Hostname(knownHost));
        System.out.println("testGetTrustStatusForKnownHost response bios: "+response.bios+" vmm: "+response.vmm);
//        assertTrue("BIOS:0,VMM:0".equals(response));
        String saml = htbo.getTrustWithSaml(knownHost);
        System.out.println("saml: "+saml);
    }

        @Test
    public void testGetTrustStatusForKnownHostWithForceVerify() throws IOException {
        HostTrustBO htbo = new ASComponentFactory().getHostTrustBO();
        String saml = "";
        try {
            saml = htbo.getTrustWithSaml(knownHost, false);
        } catch (ASException ae) {
            System.out.println(ae.getLocalizedMessage());
        }
        System.out.println("saml: "+saml);
    }

    
    @Test
    public void testAddHost154() throws IOException {
        InputStream in = getClass().getResourceAsStream("TxtHostRecord-154.json");
        String json = IOUtils.toString(in);
        in.close();
        System.out.println(json);
        // You can either deserialize into TxtHostRecord and then create a TxtHost:
        TxtHostRecord hostRecord = mapper.readValue(json, TxtHostRecord.class);
        TxtHost host1 = new TxtHost(hostRecord);
        // Or you can deserialize a TxtHostRecord directly into TxtHost:
        TxtHost host2 = mapper.readValue(json, TxtHost.class);
        hostBO.addHost(host2);
    }
    
    @Test
    public void testSplitCSV() {
        String hosts = ",abc,def,,xyz ,wofj, owa,,";
        // this apepars in our bulk host trust code:
                Set<String> hostSet = new HashSet<String>();
                for(String host : Arrays.asList(hosts.split(","))) {
            log.debug("Host: '{}'", host);
            if( !host.trim().isEmpty() ) {
                hostSet.add(host.trim());
            }
                    
                }
        for(String host : hostSet) {
            log.debug("Added Host: '{}'", host);
        }
    }
}
