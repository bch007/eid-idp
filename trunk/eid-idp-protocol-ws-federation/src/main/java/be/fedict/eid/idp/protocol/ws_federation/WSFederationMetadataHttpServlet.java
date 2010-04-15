/*
 * eID Identity Provider Project.
 * Copyright (C) 2010 FedICT.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version
 * 3.0 as published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, see 
 * http://www.gnu.org/licenses/.
 */

package be.fedict.eid.idp.protocol.ws_federation;

import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.crypto.MarshalException;
import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignContext;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import oasis.names.tc.saml._2_0.metadata.EntityDescriptorType;
import oasis.names.tc.saml._2_0.metadata.KeyDescriptorType;
import oasis.names.tc.saml._2_0.metadata.KeyTypes;
import oasis.names.tc.saml._2_0.metadata.ObjectFactory;
import oasis.names.tc.saml._2_0.metadata.RoleDescriptorType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xml.security.utils.Constants;
import org.apache.xpath.XPathAPI;
import org.oasis_open.docs.wsfed.authorization._200706.ClaimType;
import org.oasis_open.docs.wsfed.authorization._200706.DescriptionType;
import org.oasis_open.docs.wsfed.authorization._200706.DisplayNameType;
import org.oasis_open.docs.wsfed.federation._200706.ClaimTypesOfferedType;
import org.oasis_open.docs.wsfed.federation._200706.EndpointType;
import org.oasis_open.docs.wsfed.federation._200706.SecurityTokenServiceType;
import org.w3._2000._09.xmldsig_.KeyInfoType;
import org.w3._2000._09.xmldsig_.X509DataType;
import org.w3._2005._08.addressing.AttributedURIType;
import org.w3._2005._08.addressing.EndpointReferenceType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import be.fedict.eid.idp.spi.IdentityProviderConfiguration;
import be.fedict.eid.idp.spi.IdentityProviderConfigurationFactory;

public class WSFederationMetadataHttpServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	private static final Log LOG = LogFactory
			.getLog(WSFederationMetadataHttpServlet.class);

	@Override
	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		LOG.debug("doGet");
		response.setContentType("application/samlmetadata+xml");

		IdentityProviderConfiguration configuration = IdentityProviderConfigurationFactory
				.getInstance(request);

		OutputStream outputStream = response.getOutputStream();
		try {
			writeMetadata(request, configuration, outputStream);
		} catch (Exception e) {
			throw new ServletException("error: " + e.getMessage(), e);
		}
	}

	private void writeMetadata(HttpServletRequest request,
			IdentityProviderConfiguration configuration,
			OutputStream outputStream) throws JAXBException, ServletException,
			ParserConfigurationException, CertificateEncodingException,
			TransformerConfigurationException,
			TransformerFactoryConfigurationError, TransformerException,
			IOException, NoSuchAlgorithmException,
			InvalidAlgorithmParameterException, MarshalException,
			XMLSignatureException {
		ObjectFactory objectFactory = new ObjectFactory();
		EntityDescriptorType entityDescriptor = objectFactory
				.createEntityDescriptorType();

		String location = "https://" + request.getServerName() + ":"
				+ request.getServerPort() + request.getContextPath()
				+ "/protocol/ws-federation";
		LOG.debug("location: " + location);
		entityDescriptor.setEntityID(location);
		String id = "saml-metadata-" + UUID.randomUUID().toString();
		entityDescriptor.setID(id);

		org.oasis_open.docs.wsfed.federation._200706.ObjectFactory fedObjectFactory = new org.oasis_open.docs.wsfed.federation._200706.ObjectFactory();
		SecurityTokenServiceType securityTokenService = fedObjectFactory
				.createSecurityTokenServiceType();
		List<RoleDescriptorType> roleDescriptors = entityDescriptor
				.getRoleDescriptorOrIDPSSODescriptorOrSPSSODescriptor();
		roleDescriptors.add(securityTokenService);
		securityTokenService.getProtocolSupportEnumeration().add(
				"http://docs.oasis-open.org/wsfed/federation/200706");

		List<EndpointType> passiveRequestorEndpoints = securityTokenService
				.getPassiveRequestorEndpoint();
		EndpointType endpoint = fedObjectFactory.createEndpointType();
		passiveRequestorEndpoints.add(endpoint);

		org.w3._2005._08.addressing.ObjectFactory addrObjectFactory = new org.w3._2005._08.addressing.ObjectFactory();

		EndpointReferenceType endpointReference = addrObjectFactory
				.createEndpointReferenceType();
		endpoint.getEndpointReference().add(endpointReference);
		AttributedURIType address = addrObjectFactory.createAttributedURIType();
		endpointReference.setAddress(address);

		address.setValue(location);

		List<KeyDescriptorType> keyDescriptors = securityTokenService
				.getKeyDescriptor();
		KeyDescriptorType keyDescriptor = objectFactory
				.createKeyDescriptorType();
		keyDescriptors.add(keyDescriptor);
		keyDescriptor.setUse(KeyTypes.SIGNING);
		org.w3._2000._09.xmldsig_.ObjectFactory dsObjectFactory = new org.w3._2000._09.xmldsig_.ObjectFactory();
		KeyInfoType keyInfo = dsObjectFactory.createKeyInfoType();
		keyDescriptor.setKeyInfo(keyInfo);
		List<Object> keyInfoObjects = keyInfo.getContent();
		X509DataType x509Data = dsObjectFactory.createX509DataType();
		keyInfoObjects.add(dsObjectFactory.createX509Data(x509Data));

		X509Certificate certificate = configuration.getIdentity();
		x509Data.getX509IssuerSerialOrX509SKIOrX509SubjectName().add(
				dsObjectFactory.createX509DataTypeX509Certificate(certificate
						.getEncoded()));

		ClaimTypesOfferedType claimTypesOffered = fedObjectFactory
				.createClaimTypesOfferedType();
		securityTokenService.setClaimTypesOffered(claimTypesOffered);
		List<ClaimType> claimTypes = claimTypesOffered.getClaimType();
		org.oasis_open.docs.wsfed.authorization._200706.ObjectFactory authObjectFactory = new org.oasis_open.docs.wsfed.authorization._200706.ObjectFactory();
		ClaimType nameClaimType = authObjectFactory.createClaimType();
		claimTypes.add(nameClaimType);
		nameClaimType
				.setUri("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name");
		nameClaimType.setOptional(true);
		DisplayNameType nameDisplayName = authObjectFactory
				.createDisplayNameType();
		nameDisplayName.setValue("Name");
		nameClaimType.setDisplayName(nameDisplayName);
		DescriptionType nameDescription = authObjectFactory
				.createDescriptionType();
		nameDescription.setValue("The name of the subject.");
		nameClaimType.setDescription(nameDescription);

		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
				.newInstance();
		documentBuilderFactory.setNamespaceAware(true);
		DocumentBuilder documentBuilder = documentBuilderFactory
				.newDocumentBuilder();
		Document document = documentBuilder.newDocument();

		JAXBContext context = JAXBContext
				.newInstance(
						ObjectFactory.class,
						org.oasis_open.docs.wsfed.federation._200706.ObjectFactory.class);
		Marshaller marshaller = context.createMarshaller();
		marshaller.setProperty("com.sun.xml.bind.namespacePrefixMapper",
				new WSFederationNamespacePrefixMapper());
		marshaller.marshal(objectFactory
				.createEntityDescriptor(entityDescriptor), document);

		PrivateKey privateKey = configuration.getPrivateIdentityKey();

		signDocument(document, privateKey, certificate, id);

		writeDocument(document, outputStream);
	}

	private void signDocument(Document document, PrivateKey privateKey,
			X509Certificate certificate, String documentId)
			throws TransformerException, NoSuchAlgorithmException,
			InvalidAlgorithmParameterException, MarshalException,
			XMLSignatureException {

		Element nsElement = document.createElement("ns");
		nsElement.setAttributeNS(Constants.NamespaceSpecNS, "xmlns:md",
				"urn:oasis:names:tc:SAML:2.0:metadata");
		Node roleDescriptorNode = XPathAPI.selectSingleNode(document,
				"//md:RoleDescriptor", nsElement);
		if (null == roleDescriptorNode) {
			throw new IllegalStateException(
					"RoleDescriptor element not present");
		}

		XMLSignatureFactory signatureFactory = XMLSignatureFactory.getInstance(
				"DOM", new org.jcp.xml.dsig.internal.dom.XMLDSigRI());

		XMLSignContext signContext = new DOMSignContext(privateKey, document
				.getDocumentElement(), roleDescriptorNode);
		signContext.putNamespacePrefix(
				javax.xml.crypto.dsig.XMLSignature.XMLNS, "ds");
		DigestMethod digestMethod = signatureFactory.newDigestMethod(
				DigestMethod.SHA1, null);

		List<Transform> transforms = new LinkedList<Transform>();
		transforms.add(signatureFactory.newTransform(Transform.ENVELOPED,
				(TransformParameterSpec) null));
		Transform exclusiveTransform = signatureFactory
				.newTransform(CanonicalizationMethod.EXCLUSIVE,
						(TransformParameterSpec) null);
		transforms.add(exclusiveTransform);

		Reference reference = signatureFactory.newReference("#" + documentId,
				digestMethod, transforms, null, null);

		SignatureMethod signatureMethod = signatureFactory.newSignatureMethod(
				SignatureMethod.RSA_SHA1, null);
		CanonicalizationMethod canonicalizationMethod = signatureFactory
				.newCanonicalizationMethod(CanonicalizationMethod.EXCLUSIVE,
						(C14NMethodParameterSpec) null);
		SignedInfo signedInfo = signatureFactory.newSignedInfo(
				canonicalizationMethod, signatureMethod, Collections
						.singletonList(reference));

		List<Object> keyInfoContent = new LinkedList<Object>();
		KeyInfoFactory keyInfoFactory = KeyInfoFactory.getInstance();
		List<Object> x509DataObjects = new LinkedList<Object>();
		x509DataObjects.add(certificate);
		X509Data x509Data = keyInfoFactory.newX509Data(x509DataObjects);
		keyInfoContent.add(x509Data);
		KeyInfo keyInfo = keyInfoFactory.newKeyInfo(keyInfoContent);

		javax.xml.crypto.dsig.XMLSignature xmlSignature = signatureFactory
				.newXMLSignature(signedInfo, keyInfo);
		xmlSignature.sign(signContext);
	}

	protected void writeDocument(Document document,
			OutputStream documentOutputStream)
			throws TransformerConfigurationException,
			TransformerFactoryConfigurationError, TransformerException,
			IOException {
		Result result = new StreamResult(documentOutputStream);
		Transformer xformer = TransformerFactory.newInstance().newTransformer();
		Source source = new DOMSource(document);
		xformer.transform(source, result);
	}
}