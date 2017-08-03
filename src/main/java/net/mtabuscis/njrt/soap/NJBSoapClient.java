package net.mtabuscis.njrt.soap;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.xml.soap.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import net.mtabuscis.njrt.data.DataRetrievalService;

@Component
public class NJBSoapClient implements DataRetrievalService{
	
	private static Logger _log = LoggerFactory.getLogger(NJBSoapClient.class);
	
	@Value("${soap_url}")
	private String SOAP_URL;
	@Value("${soap_endpoint}")
	private String SOAP_ENDPOINT;
	@Value("${username}")
	private String username;
	@Value("${password}")
	private String password;

	//wrapper for interface
	public boolean getXmlSiriData(String datafile){
		boolean data = false;
		try {
			data = retrieveSiriXml(datafile);
		} catch (Exception e) {
			_log.info("Unable to call soap...");
			e.printStackTrace();
		}
		return data;
	}
	
	//namespace has final /, this is an as-is state call with no trackability
	public boolean retrieveSiriXml(String datafile) throws SOAPException, TransformerException, IOException {
		
		// Create SOAP Connection
		SOAPConnectionFactory soapConnectionFactory = SOAPConnectionFactory.newInstance();
		SOAPConnection soapConnection = soapConnectionFactory.createConnection();

		// Send SOAP Message to SOAP Server
		MessageFactory messageFactory = MessageFactory.newInstance();
		SOAPMessage soapMessage = messageFactory.createMessage();
		SOAPPart soapPart = soapMessage.getSOAPPart();
		SOAPEnvelope envelope = soapPart.getEnvelope();
		envelope.addNamespaceDeclaration("soap1", "http://soap.isg.com/");
		envelope.addNamespaceDeclaration("mes", "http://www.xcm.org/transit/siri/001/messages");
		
		SOAPBody soapBody = envelope.getBody();
		
		SOAPElement siribody = soapBody.addChildElement("Siri", "soap1");
		siribody.setAttribute("version", "2.0");
		
		
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'-04:00'"); // Quoted "Z" to indicate UTC, no timezone offset
        String nowAsISO = df.format(new Date());
		
        SOAPElement mainsvcreq = siribody.addChildElement("ServiceRequest", "mes");
        
        SOAPElement reqts = mainsvcreq.addChildElement("RequestTimestamp", "mes");
        reqts.addTextNode(nowAsISO);
        SOAPElement accountid = mainsvcreq.addChildElement("AccountId", "mes");
        accountid.addTextNode("NYCTRANSIT");
        SOAPElement accountkey = mainsvcreq.addChildElement("AccountKey", "mes");
        accountkey.addTextNode("NYCTRANSIT");
        SOAPElement reqref = mainsvcreq.addChildElement("RequestorRef", "mes");
        reqref.addTextNode("123");
        
        SOAPElement svcreq = mainsvcreq.addChildElement("VehicleMonitoringRequest", "mes");
        svcreq.setAttribute("version", "2.0");
        SOAPElement rts = svcreq.addChildElement("RequestTimestamp", "mes");
        rts.addTextNode(nowAsISO);
        SOAPElement lr = svcreq.addChildElement("VehicleMonitoringRef", "mes");
        lr.addTextNode("-1");
        SOAPElement lineRef = svcreq.addChildElement("LineRef", "mes");
        lineRef.addTextNode("-1");
        
        
        //gives us alerts on rail too...
        /*
        SOAPElement mainsvcreq = siribody.addChildElement("ServiceRequest", "mes");
        
        SOAPElement reqts = mainsvcreq.addChildElement("RequestTimestamp", "mes");
        reqts.addTextNode(nowAsISO);
        SOAPElement accountid = mainsvcreq.addChildElement("AccountId", "mes");
        accountid.addTextNode("NYCTRANSIT");
        SOAPElement accountkey = mainsvcreq.addChildElement("AccountKey", "mes");
        accountkey.addTextNode("NYCTRANSIT");
        SOAPElement reqref = mainsvcreq.addChildElement("RequestorRef", "mes");
        reqref.addTextNode("123");
        
        SOAPElement svcreq = mainsvcreq.addChildElement("SituationExchangeRequest", "mes");
        svcreq.setAttribute("version", "2.0");
        SOAPElement rts = svcreq.addChildElement("RequestTimestamp", "mes");
        rts.addTextNode(nowAsISO);
         */
        
		MimeHeaders headers = soapMessage.getMimeHeaders();
		headers.addHeader("SOAPAction", "GetSiriService");
		soapMessage.saveChanges();

		/* Print the request message */
		/*
		System.out.print("Request SOAP Message = ");
		soapMessage.writeTo(System.out);
		System.out.println();
		*/
		
		URL urlendpoint = new URL(new URL(SOAP_URL), SOAP_ENDPOINT, 
				 new URLStreamHandler() {
				    protected URLConnection openConnection(URL url) throws IOException {
		              URL target = new URL(url.toString());
		              URLConnection connection = target.openConnection();
		              // Connection settings
		              connection.setConnectTimeout(10000); // 10 sec
		              connection.setReadTimeout(45000); // 45 sec
		              return(connection);
		            }
		   	    }
			);
		
		SOAPMessage soapResponse = soapConnection.call(soapMessage, urlendpoint);
		
		// Process the SOAP Response
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		Source sourceContent = soapResponse.getSOAPPart().getContent();
		
		File file = new File(datafile);
		FileWriter writer = new FileWriter(file);
		
		StreamResult result = new StreamResult(writer);
		transformer.transform(sourceContent, result);
		
		//close connections
		soapConnection.close();
		writer.close();
		
		return true;
		
	}

}
