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

package test.unit.be.fedict.eid.idp.sp.protocol.saml2;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mortbay.jetty.SessionManager;
import org.mortbay.jetty.servlet.HashSessionManager;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.servlet.SessionHandler;
import org.mortbay.jetty.testing.ServletTester;

import be.fedict.eid.idp.sp.protocol.saml2.AuthenticationResponseServlet;

public class AuthenticationResponseServletTest {

	private static final Log LOG = LogFactory
			.getLog(AuthenticationResponseServletTest.class);

	private ServletTester servletTester;

	private String location;

	@Before
	public void setUp() throws Exception {
		this.servletTester = new ServletTester();
		ServletHolder servletHolder = this.servletTester.addServlet(
				AuthenticationResponseServlet.class, "/");
		servletHolder.setInitParameter("IdentifierSessionAttribute",
				"identifier");
		servletHolder.setInitParameter("RedirectPage", "target-page");
		servletHolder.setInitParameter("NameSessionAttribute", "name");
		this.servletTester.start();
		this.location = this.servletTester.createSocketConnector(true);
	}

	@After
	public void tearDown() throws Exception {
		this.servletTester.stop();
	}

	@Test
	public void testDoGet() throws Exception {
		// setup
		LOG.debug("URL: " + this.location);
		HttpClient httpClient = new HttpClient();
		GetMethod getMethod = new GetMethod(this.location);

		// operate
		int result = httpClient.executeMethod(getMethod);

		// verify
		LOG.debug("result: " + result);
		assertEquals(HttpServletResponse.SC_METHOD_NOT_ALLOWED, result);
	}

	@Test
	public void testPostSamlResponse() throws Exception {
		// setup
		InputStream samlResponseInputStream = AuthenticationResponseServletTest.class
				.getResourceAsStream("/saml-response.xml");
		String samlResponse = IOUtils.toString(samlResponseInputStream);
		String encodedSamlResponse = Base64.encodeBase64String(samlResponse
				.getBytes());

		LOG.debug("URL: " + this.location);
		HttpClient httpClient = new HttpClient();
		PostMethod postMethod = new PostMethod(this.location);
		NameValuePair[] body = { new NameValuePair("SAMLResponse",
				encodedSamlResponse) };
		postMethod.setRequestBody(body);

		// operate
		int resultStatusCode = httpClient.executeMethod(postMethod);

		// verify
		LOG.debug("result: " + resultStatusCode);
		assertEquals(HttpServletResponse.SC_MOVED_TEMPORARILY, resultStatusCode);
		Header locationHeader = postMethod.getResponseHeader("Location");
		String value = locationHeader.getValue();
		assertEquals(this.location + "/target-page", value);

		SessionHandler sessionHandler = this.servletTester.getContext()
				.getSessionHandler();
		SessionManager sessionManager = sessionHandler.getSessionManager();
		LOG.debug("session manager type: "
				+ sessionManager.getClass().getName());
		HashSessionManager hashSessionManager = (HashSessionManager) sessionManager;
		LOG.debug("# sessions: " + hashSessionManager.getSessions());
		assertEquals(1, hashSessionManager.getSessions());
		Map<String, HttpSession> sessionMap = hashSessionManager
				.getSessionMap();
		LOG.debug("session map: " + sessionMap);
		Entry<String, HttpSession> sessionEntry = sessionMap.entrySet()
				.iterator().next();
		HttpSession httpSession = sessionEntry.getValue();

		String identifierValue = (String) httpSession
				.getAttribute("identifier");
		assertEquals("authn-id", identifierValue);

		String nameValue = (String) httpSession.getAttribute("name");
		assertEquals("test-name", nameValue);
	}
}