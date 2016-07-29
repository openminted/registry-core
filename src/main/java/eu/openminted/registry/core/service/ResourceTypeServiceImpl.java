package eu.openminted.registry.core.service;

import eu.openminted.registry.core.dao.ResourceTypeDao;
import eu.openminted.registry.core.dao.SchemaDao;
import eu.openminted.registry.core.domain.ResourceType;
import eu.openminted.registry.core.domain.Schema;
import eu.openminted.registry.core.domain.Tools;
import eu.openminted.registry.core.domain.index.IndexField;
import eu.openminted.registry.core.index.DefaultIndexMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.hibernate.NonUniqueObjectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletRequest;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.List;

/**
 * Created by antleb on 7/14/16.
 */
@Service("resourceTypeService")
@Transactional
public class ResourceTypeServiceImpl implements ResourceTypeService {

	private static Logger logger = Logger.getLogger(ResourceTypeService.class);

	@Autowired
	ResourceTypeDao resourceTypeDao;

	@Autowired
	SchemaDao schemaDao;

	public ResourceTypeServiceImpl() {

	}

	@Override public Schema getSchema(String id){
		Schema schema = schemaDao.getSchema(id);
		return schema;
	}

	@Override public ResourceType getResourceType(String name){
		ResourceType resourceType = resourceTypeDao.getResourceType(name);
		return resourceType;
	}

	@Override public List<ResourceType> getAllResourceType() {
		List<ResourceType> resourceType = resourceTypeDao.getAllResourceType();

		return resourceType;
	}

	@Override public List<ResourceType> getAllResourceType(int from, int to){
		List<ResourceType> resourceType = resourceTypeDao.getAllResourceType(from,to);
		return resourceType;
	}

	@Override public ResourceType addResourceType(ResourceType resourceType) throws ServiceException{

		if (resourceType.getIndexMapperClass() == null)
			resourceType.setIndexMapperClass(DefaultIndexMapper.class.getName());

		if (resourceType.getIndexFields() != null) {
			for (IndexField field:resourceType.getIndexFields())
				field.setResourceType(resourceType);
		}
		try {
			exportIncludes(resourceType,resourceType.getSchemaUrl());
		} catch (ServiceException e) {
			// TODO Auto-generated catch block
			throw new ServiceException(e.getMessage());
		}
		resourceTypeDao.addResourceType(resourceType);
		Schema schema = new Schema();
		schema.setId(stringToMd5(resourceType.getSchema()));
		if(resourceType.getSchemaUrl()!=null){
			schema.setOriginalUrl(resourceType.getSchemaUrl());
		}
		schema.setSchema(resourceType.getSchema());

		schemaDao.addSchema(schema);
		return resourceType;
	}

	public ResourceTypeDao getResourceTypeDao() {
		return resourceTypeDao;
	}

	public void setResourceTypeDao(ResourceTypeDao resourceTypeDao) {
		this.resourceTypeDao = resourceTypeDao;
	}

	private String stringToMd5(String stringToBeConverted){
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			md.update(stringToBeConverted.toString().getBytes());
			byte[] digest = md.digest();
			StringBuffer sb = new StringBuffer();
			for (byte b : digest) {
				sb.append(String.format("%02x", b & 0xff));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException e) {
			return null;
		}
	}

	private void exportIncludes(ResourceType resourceType, String baseUrl) throws ServiceException{
		String type = resourceType.getPayloadType();
		boolean isFromUrl;
		if(resourceType.getSchemaUrl().equals("not_set")){
			isFromUrl = false;
		}else{
			isFromUrl = true;
		}

		if(type.equals("xml")){
			try {
				DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
				dbFactory.setNamespaceAware(true);
				dbFactory.setValidating(true);
				DocumentBuilder dBuilder;

				dBuilder = dbFactory.newDocumentBuilder();

				Document doc = dBuilder.parse(new InputSource(new StringReader(resourceType.getSchema())));
				doc.getDocumentElement().normalize();


				XPathFactory factory = XPathFactory.newInstance();
				XPath xpath = factory.newXPath();
				final String prefixFinal = "";

				// there's no default implementation for NamespaceContext...seems kind of silly, no?
				xpath.setNamespaceContext(new NamespaceContext() {
					public String getNamespaceURI(String prefix) {
						if (prefix == null) return "http://www.w3.org/2001/XMLSchema";
						else if ("xml".equals(prefix)) return XMLConstants.XML_NS_URI;
						else if ("xs".equals(prefix)) return "http://www.w3.org/2001/XMLSchema";
						else if ("xsd".equals(prefix)) return "http://www.w3.org/2001/XMLSchema";
						return XMLConstants.NULL_NS_URI;
					}

					// This method isn't necessary for XPath processing.
					public String getPrefix(String uri) {
						throw new UnsupportedOperationException();
					}

					// This method isn't necessary for XPath processing either.
					public Iterator getPrefixes(String uri) {
						throw new UnsupportedOperationException();
					}
				});
				String expression = "//xs:include/attribute::schemaLocation";
				NodeList nodeList = (NodeList) xpath.compile(expression).evaluate(doc, XPathConstants.NODESET);
				for (int i = 0; i < nodeList.getLength(); i++) {
					Node nNode = nodeList.item(i);
					String response = "";
					response = nNode.getTextContent();
					int validation = isValidUrl(response,isFromUrl);
					if(validation!=0){
						String schemaResponse = "";
						if(validation==2){
							response = baseUrl.replace(baseUrl.substring(baseUrl.lastIndexOf("/")+1), response);
						}
						try {
							schemaResponse = Tools.getText(response);
						} catch (Exception e) {
							throw new ServiceException("failed to download file(s)");
						}
						Schema schema = schemaDao.getSchema(stringToMd5(schemaResponse));
						if(schema!=null){
							//schema already exists
							nodeList.item(i).setNodeValue(getBaseEnvLinkURL()+"/schemaService/"+schema.getId()+"");
						}else{
							//add schema in db and call the "exportIncludes" function again
							resourceType.setSchema(schemaResponse);
							exportIncludes(resourceType,response);
							schema = new Schema();
							schema.setId(stringToMd5(resourceType.getSchema()));
							schema.setSchema(resourceType.getSchema());
							schema.setOriginalUrl(nodeList.item(i).getNodeValue());
							nodeList.item(i).setNodeValue(getBaseEnvLinkURL()+"/schemaService/"+schema.getId()+"");
							try{
								schemaDao.addSchema(schema);
							}catch (NonUniqueObjectException e){

							}
						}
					}else{
						throw new ServiceException("includes contain relative paths that cannot be resolved");
					}
//			        out = out.concat(getText(response,type,whatFor));
				}
				resourceType.setSchema(documentToString(doc));
//			     resourceType.setSchema(nodeList.getLength()+"");
			} catch (ParserConfigurationException e){
//		    	  out = out.concat(e.getMessage());
			} catch (SAXException e) {
//		    	  out = out.concat(e.getMessage());
			} catch (IOException e) {
//		    	  out = out.concat(e.getMessage());
			} catch (XPathExpressionException e) {
//		    	  out = out.concat(e.getMessage());
			}
		}

	}
	private static int isValidUrl(String Url, boolean isFromUrl){
		URI u;
		try {
			u = new URI(Url);
		} catch (URISyntaxException e) {
			return 0;
		}

		if(u.isAbsolute()){
			return 1;
		}else{
			if(isFromUrl){
				return 2;
			}else{
				return 0;
			}
		}
	}

	protected static String getBaseEnvLinkURL() {

		String baseEnvLinkURL=null;
		HttpServletRequest currentRequest =
				((ServletRequestAttributes) RequestContextHolder.
						currentRequestAttributes()).getRequest();
		// lazy about determining protocol but can be done too
		baseEnvLinkURL = "http://" + currentRequest.getServerName();
		if(currentRequest.getLocalPort() != 80) {
			baseEnvLinkURL += ":" + currentRequest.getLocalPort();
		}
		if(!StringUtils.isEmpty(currentRequest.getContextPath())) {
			baseEnvLinkURL += currentRequest.getContextPath();
		}
		return baseEnvLinkURL;
	}

	private String documentToString(Document document){
		try {
			StringWriter sw = new StringWriter();
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer transformer = tf.newTransformer();
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
			transformer.setOutputProperty(OutputKeys.METHOD, "xml");
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

			transformer.transform(new DOMSource(document), new StreamResult(sw));
			return sw.toString();
		} catch (Exception ex) {
			throw new RuntimeException("Error converting to String", ex);
		}
	}
}