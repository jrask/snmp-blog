package com.jayway.snmpblogg;

import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.Assert.assertEquals;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static com.jayway.awaitility.Awaitility.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.smi.Gauge32;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.SMIConstants;

import com.jayway.awaitility.Duration;

public class SnmpAgentAndClientTest {
	
	// These are both standard in RFC-1213
	static final OID sysDescr = new OID(".1.3.6.1.2.1.1.1.0");
	static final OID interfacesTable = new OID(".1.3.6.1.2.1.2.2.1");
		
	static Agent agent;
	static SimpleSnmpClient client;
	
	@BeforeClass
	public static void setUp() throws Exception {
		agent = new Agent("0.0.0.0/2001");
		agent.start();
		
		// Since BaseAgent registers some mibs by default we need to unregister
		// one before we register our own sysDescr. Normally you would
		// override that method and register the mibs that you need
		agent.unregisterManagedObject(agent.getSnmpv2MIB());
		
		// Register a system description, use one from you product environment
		// to test with
		agent.registerManagedObject(
				MOScalarFactory.createReadOnly(sysDescr,"MySystemDescr"));
		
		// Build a table. This example is taken from TestAgent and sets up
		// two physical interfaces 
		MOTableBuilder builder = new MOTableBuilder(interfacesTable)
			.addColumnType(SMIConstants.SYNTAX_INTEGER,MOAccessImpl.ACCESS_READ_ONLY)
			.addColumnType(SMIConstants.SYNTAX_OCTET_STRING,MOAccessImpl.ACCESS_READ_ONLY)
			.addColumnType(SMIConstants.SYNTAX_INTEGER,MOAccessImpl.ACCESS_READ_ONLY)
			.addColumnType(SMIConstants.SYNTAX_INTEGER,MOAccessImpl.ACCESS_READ_ONLY)
			.addColumnType(SMIConstants.SYNTAX_GAUGE32,MOAccessImpl.ACCESS_READ_ONLY)
			.addColumnType(SMIConstants.SYNTAX_OCTET_STRING,MOAccessImpl.ACCESS_READ_ONLY)
			.addColumnType(SMIConstants.SYNTAX_INTEGER,MOAccessImpl.ACCESS_READ_ONLY)
			.addColumnType(SMIConstants.SYNTAX_INTEGER,MOAccessImpl.ACCESS_READ_ONLY)
			// Normally you would begin loop over you two domain objects here
			.addRowValue(new Integer32(1))
			.addRowValue(new OctetString("loopback"))
			.addRowValue(new Integer32(24))
			.addRowValue(new Integer32(1500))
			.addRowValue(new Gauge32(10000000))
			.addRowValue(new OctetString("00:00:00:00:01"))
			.addRowValue(new Integer32(1500))
			.addRowValue(new Integer32(1500))
			//next row
			.addRowValue(new Integer32(2))
			.addRowValue(new OctetString("eth0"))
			.addRowValue(new Integer32(24))
			.addRowValue(new Integer32(1500))
			.addRowValue(new Gauge32(10000000))
			.addRowValue(new OctetString("00:00:00:00:02"))
			.addRowValue(new Integer32(1500))
			.addRowValue(new Integer32(1500));

		agent.registerManagedObject(builder.build());
		
		// Setup the client to use our newly started agent
		client = new SimpleSnmpClient("udp:127.0.0.1/2001");

	}
	
	@AfterClass
	public static void tearDown() throws Exception {
		agent.stop();
		client.stop();
	}
	
    
	/**
	 * Simply verifies that we get the same sysDescr as we have registered in our agent
	 */
	@Test
    public void verifySysDescr() throws IOException
    {
    	assertEquals("MySystemDescr", client.getAsString(sysDescr));
    }

	
	/**
	 * Uses asynchronous fetch and test it with Awaitility.
	 */
	@Test
	public void verifySysDescrAsynch() throws Exception {
		final StringResponseListener listener = new StringResponseListener();
		client.getAsString(sysDescr, listener);
		await().until(callTo(listener).getValue(),equalTo("MySystemDescr"));
	}
	
	/**
	 * Verify that the table contents is ok.
	 */
	@Test
    public void verifyTableContents() {

		// You retreive a table by suppling the columns of the table that
		// you need, here we use column 2,6 and 8 so we do not verify the complete
		// table
		List<List<String>> tableContents = client.getTableAsStrings(new OID[]{
    			new OID(interfacesTable.toString() + ".2"),
    			new OID(interfacesTable.toString() + ".6"),
    			new OID(interfacesTable.toString() + ".8")});
		
		//and validate here
		assertEquals(2, tableContents.size());
		assertEquals(3, tableContents.get(0).size());
		assertEquals(3, tableContents.get(1).size());

		// Row 1
		assertEquals("loopback", tableContents.get(0).get(0));
		assertEquals("00:00:00:00:01", tableContents.get(0).get(1));
		assertEquals("1500", tableContents.get(0).get(2));
		
		// Row 2
		assertEquals("eth0", tableContents.get(1).get(0));
		assertEquals("00:00:00:00:02", tableContents.get(1).get(1));
		assertEquals("1500", tableContents.get(1).get(2));
    }

	class StringResponseListener implements ResponseListener {
		
		private String value = null;
		
		@Override
		public void onResponse(ResponseEvent event) {
			System.out.println(event.getResponse());
			if(event.getResponse() != null) {
				value = SimpleSnmpClient.extractSingleString(event);
			}
		}

		public String getValue() {
			System.out.println(value);
			return value;
		}
		
	}
}


