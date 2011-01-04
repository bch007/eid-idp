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

package test.unit.be.fedict.eid.idp.protocol.ws_federation;

import be.fedict.eid.applet.service.Address;
import be.fedict.eid.applet.service.Gender;
import be.fedict.eid.applet.service.Identity;
import be.fedict.eid.applet.service.signer.KeyInfoKeySelector;
import be.fedict.eid.idp.protocol.ws_federation.AbstractWSFederationProtocolService;
import be.fedict.eid.idp.protocol.ws_federation.WSFederationProtocolServiceAuthIdent;
import be.fedict.eid.idp.spi.IdentityProviderConfiguration;
import be.fedict.eid.idp.spi.NameValuePair;
import be.fedict.eid.idp.spi.ReturnResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.xml.security.Init;
import org.apache.xml.security.utils.Constants;
import org.apache.xpath.XPathAPI;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.easymock.EasyMock;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.crypto.*;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.*;

import static org.junit.Assert.*;

public class WSFederationProtocolServiceTest {

    private static final Log LOG = LogFactory
            .getLog(WSFederationProtocolServiceTest.class);

    @BeforeClass
    public static void init() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Before
    public void setUp() throws Exception {
        Init.init();
    }

    @Test
    public void testhandleReturnResponse() throws Exception {
        // setup
        WSFederationProtocolServiceAuthIdent testedInstance =
                new WSFederationProtocolServiceAuthIdent();

        KeyPair keyPair = generateKeyPair();
        DateTime notBefore = new DateTime();
        DateTime notAfter = notBefore.plusMonths(1);
        X509Certificate certificate = generateSelfSignedCertificate(keyPair,
                "CN=Test", notBefore, notAfter);
        KeyStore.PrivateKeyEntry idPIdentity =
                new KeyStore.PrivateKeyEntry(keyPair.getPrivate(), new java.security.cert.Certificate[]{certificate});

        HttpSession mockHttpSession = EasyMock.createMock(HttpSession.class);
        String userId = UUID.randomUUID().toString();
        String givenName = "test-given-name";
        String surName = "test-sur-name";
        Identity identity = new Identity();
        identity.name = surName;
        identity.firstName = givenName;
        identity.dateOfBirth = new GregorianCalendar();
        identity.gender = Gender.MALE;
        Address address = new Address();
        address.streetAndNumber = "test-street 1234";
        address.zip = "5678";
        address.municipality = "test-city";
        HttpServletRequest mockRequest = EasyMock
                .createMock(HttpServletRequest.class);
        HttpServletResponse mockResponse = EasyMock
                .createMock(HttpServletResponse.class);
        IdentityProviderConfiguration mockIdentityProviderConfiguration = EasyMock
                .createMock(IdentityProviderConfiguration.class);

        // expectations
        EasyMock
                .expect(
                        mockHttpSession
                                .getAttribute(AbstractWSFederationProtocolService.WTREALM_SESSION_ATTRIBUTE))
                .andStubReturn("http://return.to.here");
        EasyMock
                .expect(
                        mockHttpSession
                                .getAttribute(AbstractWSFederationProtocolService.WCTX_SESSION_ATTRIBUTE))
                .andStubReturn("some-context-identifier");
        EasyMock.expect(mockIdentityProviderConfiguration.findIdentity())
                .andStubReturn(idPIdentity);

        // prepare
        EasyMock.replay(mockHttpSession, mockRequest, mockResponse,
                mockIdentityProviderConfiguration);

        // operate
        testedInstance.init(null, mockIdentityProviderConfiguration);
        ReturnResponse result = testedInstance.handleReturnResponse(
                mockHttpSession, userId, givenName, surName, identity, address,
                mockRequest, mockResponse);

        // verify
        EasyMock.verify(mockHttpSession, mockRequest, mockResponse,
                mockIdentityProviderConfiguration);
        assertEquals("http://return.to.here", result.getActionUrl());
        assertAttribute(result, "wa", "wsignin1.0");
        assertAttribute(result, "wctx", "some-context-identifier");
        String wresult = getAttributeValue(result, "wresult");
        assertNotNull(wresult);
        LOG.debug("wresult: " + wresult);
    }

    // @Test
    public void testSignatureVerification() throws Exception {
        // setup
        InputStream documentInputStream = WSFederationProtocolServiceTest.class
                .getResourceAsStream("/sts-response-message.xml");
        assertNotNull(documentInputStream);

        Document document = loadDocument(documentInputStream);

        NodeList signatureNodeList = document.getElementsByTagNameNS(
                XMLSignature.XMLNS, "Signature");
        assertEquals(1, signatureNodeList.getLength());
        Node signatureNode = signatureNodeList.item(0);

        KeyInfoKeySelector keySelector = new KeyInfoKeySelector();
        DOMValidateContext domValidateContext = new DOMValidateContext(
                keySelector, signatureNode);
        SAMLURIDereferencer dereferencer = new SAMLURIDereferencer(document);
        domValidateContext.setURIDereferencer(dereferencer);

        XMLSignatureFactory xmlSignatureFactory = XMLSignatureFactory
                .getInstance();
        XMLSignature xmlSignature = xmlSignatureFactory
                .unmarshalXMLSignature(domValidateContext);

        // operate
        boolean validity = xmlSignature.validate(domValidateContext);

        // verify
        assertTrue(validity);
    }

    private Document loadDocument(InputStream documentInputStream)
            throws ParserConfigurationException, SAXException, IOException {
        InputSource inputSource = new InputSource(documentInputStream);
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
                .newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        DocumentBuilder documentBuilder = documentBuilderFactory
                .newDocumentBuilder();
        return documentBuilder.parse(inputSource);
    }

    private String getAttributeValue(ReturnResponse returnResponse,
                                     String attributeName) {
        assertNotNull(returnResponse);
        List<NameValuePair> attributes = returnResponse.getAttributes();
        for (NameValuePair attribute : attributes) {
            if (attributeName.equals(attribute.getName())) {
                return attribute.getValue();
            }
        }
        fail("attribute not found: " + attributeName);
        return null;
    }

    private void assertAttribute(ReturnResponse returnResponse,
                                 String attributeName, String attributeValue) {
        assertNotNull(returnResponse);
        List<NameValuePair> attributes = returnResponse.getAttributes();
        for (NameValuePair attribute : attributes) {
            if (attributeName.equals(attribute.getName())) {
                assertEquals(attributeValue, attribute.getValue());
                return;
            }
        }
        fail("attribute not found: " + attributeName);
    }

    public static class SAMLURIDereferencer implements URIDereferencer {

        private static final Log LOG = LogFactory
                .getLog(SAMLURIDereferencer.class);

        private final Document document;

        public SAMLURIDereferencer(Document document) {
            this.document = document;
        }

        @Override
        public Data dereference(URIReference uriReference,
                                XMLCryptoContext context) throws URIReferenceException {
            if (null == uriReference) {
                throw new NullPointerException("URIReference cannot be null");
            }
            if (null == context) {
                throw new NullPointerException("XMLCrytoContext cannot be null");
            }

            String uri = uriReference.getURI();
            try {
                uri = URLDecoder.decode(uri, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LOG.warn("could not URL decode the uri: " + uri);
            }
            LOG.debug("dereference: " + uri);
            String assertionId = uri.substring(1);
            Element nsElement = document.createElement("ns");
            nsElement.setAttributeNS(Constants.NamespaceSpecNS, "xmlns:saml",
                    "urn:oasis:names:tc:SAML:1.0:assertion");
            Node assertionNode;
            try {
                assertionNode = XPathAPI.selectSingleNode(document,
                        "//saml:Assertion[@AssertionID='" + assertionId + "']",
                        nsElement);
            } catch (TransformerException e) {
                throw new URIReferenceException("XPath error: "
                        + e.getMessage(), e);
            }
            if (null == assertionNode) {
                throw new URIReferenceException("SAML Assertion not found");
            }
            DOMNodeSetData nodeSetData = new DOMNodeSetData(assertionNode);
            LOG.debug("returning node set data...");
            return nodeSetData;
        }
    }

    private static class DOMNodeSetData implements NodeSetData {

        private final Node node;

        public DOMNodeSetData(Node node) {
            this.node = node;
        }

        @Override
        public Iterator iterator() {
            return Collections.singletonList(this.node).iterator();
        }
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        SecureRandom random = new SecureRandom();
        keyPairGenerator.initialize(new RSAKeyGenParameterSpec(1024,
                RSAKeyGenParameterSpec.F4), random);
        return keyPairGenerator.generateKeyPair();
    }

    private SubjectKeyIdentifier createSubjectKeyId(PublicKey publicKey)
            throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(publicKey
                .getEncoded());
        SubjectPublicKeyInfo info = new SubjectPublicKeyInfo(
                (ASN1Sequence) new ASN1InputStream(bais).readObject());
        return new SubjectKeyIdentifier(info);
    }

    private AuthorityKeyIdentifier createAuthorityKeyId(PublicKey publicKey)
            throws IOException {

        ByteArrayInputStream bais = new ByteArrayInputStream(publicKey
                .getEncoded());
        SubjectPublicKeyInfo info = new SubjectPublicKeyInfo(
                (ASN1Sequence) new ASN1InputStream(bais).readObject());

        return new AuthorityKeyIdentifier(info);
    }

    private X509Certificate generateSelfSignedCertificate(KeyPair keyPair,
                                                          String subjectDn, DateTime notBefore, DateTime notAfter)
            throws Exception {
        PublicKey subjectPublicKey = keyPair.getPublic();
        PrivateKey issuerPrivateKey = keyPair.getPrivate();
        String signatureAlgorithm = "SHA1WithRSAEncryption";
        X509V3CertificateGenerator certificateGenerator = new X509V3CertificateGenerator();
        certificateGenerator.reset();
        certificateGenerator.setPublicKey(subjectPublicKey);
        certificateGenerator.setSignatureAlgorithm(signatureAlgorithm);
        certificateGenerator.setNotBefore(notBefore.toDate());
        certificateGenerator.setNotAfter(notAfter.toDate());
        X509Principal issuerDN = new X509Principal(subjectDn);
        certificateGenerator.setIssuerDN(issuerDN);
        certificateGenerator.setSubjectDN(new X509Principal(subjectDn));
        certificateGenerator.setSerialNumber(new BigInteger(128,
                new SecureRandom()));

        certificateGenerator.addExtension(X509Extensions.SubjectKeyIdentifier,
                false, createSubjectKeyId(subjectPublicKey));
        PublicKey issuerPublicKey;
        issuerPublicKey = subjectPublicKey;
        certificateGenerator.addExtension(
                X509Extensions.AuthorityKeyIdentifier, false,
                createAuthorityKeyId(issuerPublicKey));

        certificateGenerator.addExtension(X509Extensions.BasicConstraints,
                false, new BasicConstraints(true));

        X509Certificate certificate;
        certificate = certificateGenerator.generate(issuerPrivateKey);

        /*
           * Next certificate factory trick is needed to make sure that the
           * certificate delivered to the caller is provided by the default
           * security provider instead of BouncyCastle. If we don't do this trick
           * we might run into trouble when trying to use the CertPath validator.
           */
        CertificateFactory certificateFactory = CertificateFactory
                .getInstance("X.509");
        certificate = (X509Certificate) certificateFactory
                .generateCertificate(new ByteArrayInputStream(certificate
                        .getEncoded()));
        return certificate;
    }
}