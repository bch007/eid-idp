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

package test.integ.be.fedict.eid.idp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.awt.Component;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.CookieStore;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.easymock.classextension.EasyMock;
import org.junit.After;
import org.junit.Test;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.jetty.testing.ServletTester;

import be.fedict.eid.applet.Applet;
import be.fedict.eid.applet.Controller;
import be.fedict.eid.applet.Messages;
import be.fedict.eid.applet.Runtime;
import be.fedict.eid.applet.Status;
import be.fedict.eid.applet.View;
import be.fedict.eid.idp.sp.protocol.openid.AuthenticationRequestServlet;

/**
 * Integration tests for the OpenID protocol.
 * 
 * @author Frank Cornelis
 * 
 */
public class OpenIDTest {

	private static final Log LOG = LogFactory.getLog(OpenIDTest.class);

	private ServletTester servletTester;

	@After
	public void tearDown() throws Exception {
		if (null != this.servletTester) {
			this.servletTester.stop();
		}
	}

	@Test
	public void testOpenID() throws Exception {
		LOG.debug("OpenID integration test");

		// make sure that the session cookies are passed during conversations
		// required to be able to run the Controller here
		MyCookieManager cookieManager = new MyCookieManager();
		cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
		CookieHandler.setDefault(cookieManager);

		// setup
		this.servletTester = new ServletTester();
		ServletHolder reqServletHolder = this.servletTester.addServlet(
				AuthenticationRequestServlet.class, "/openid-request");
		reqServletHolder.setInitParameter("ParametersFromRequest", "true");
		reqServletHolder.setInitParameter("SPDestination",
				"http://localhost/openid-response");
		reqServletHolder.setInitParameter("UserIdentifier",
				"https://localhost/eid-idp/endpoints/openid-identity");
		reqServletHolder.setInitParameter("TrustServer", "true");

		this.servletTester.start();
		String location = this.servletTester.createSocketConnector(true);
		LOG.debug("location: " + location);

		HttpState httpState = new HttpState();
		HttpClient httpClient = new HttpClient();
		httpClient.setState(httpState);
		// httpClient.getParams().setCookiePolicy(
		// org.apache.commons.httpclient.cookie.CookiePolicy.RFC_2109);
		GetMethod getMethod = new GetMethod(
				location
						+ "/openid-request?SPDestination="
						+ location
						+ "/openid-response&UserIdentifier=https://localhost/eid-idp/endpoints/openid-identity");

		ProtocolSocketFactory protocolSocketFactory = new MyProtocolSocketFactory();
		Protocol myProtocol = new Protocol("https", protocolSocketFactory, 443);
		Protocol.registerProtocol("https", myProtocol);

		// operate
		int statusCode = httpClient.executeMethod(getMethod);

		// verify
		LOG.debug("status code: " + statusCode);
		assertEquals(HttpServletResponse.SC_OK, statusCode);
		LOG.debug("response body: " + getMethod.getResponseBodyAsString());

		Messages messages = new Messages(Locale.getDefault());
		Runtime runtime = new TestRuntime();
		View view = new TestView();
		Controller controller = new Controller(view, runtime, messages);

		String sessionCookie = null;
		Cookie[] cookies = httpClient.getState().getCookies();
		for (Cookie cookie : cookies) {
			LOG.debug("cookie: " + cookie.getName() + "=" + cookie.getValue()
					+ " (" + cookie.getPath() + ")");
			if ("JSESSIONID".equals(cookie.getName())
					&& "/eid-idp".equals(cookie.getPath())) {
				sessionCookie = cookie.getValue();
			}
		}
		assertNotNull(sessionCookie);
		cookieManager.setSessionCookie(sessionCookie);

		// operate
		controller.run();

		// httpState.addCookie(new Cookie("localhost", "JSESSIONID",
		// sessionCookie, "/eid-idp", -1, false));
		// httpClient.setState(httpState);
		LOG.debug("continue to eID IdP exit page...");
		getMethod = new GetMethod("https://localhost/eid-idp/protocol-exit");
		getMethod.addRequestHeader("Cookie", "JSESSIONID=" + sessionCookie
				+ "; Path=/eid-idp; Secure");
		statusCode = httpClient.executeMethod(getMethod);
		LOG.debug("status code: " + statusCode);
		assertEquals(HttpServletResponse.SC_OK, statusCode);
		LOG.debug("response body: " + getMethod.getResponseBodyAsString());
	}

	private static class MyCookieManager extends CookieManager {

		private static final Log LOG = LogFactory.getLog(MyCookieManager.class);

		private String sessionCookie;

		public void setSessionCookie(String sessionCookie) {
			this.sessionCookie = sessionCookie;
		}

		@Override
		public Map<String, List<String>> get(URI uri,
				Map<String, List<String>> requestHeaders) throws IOException {
			LOG.debug("get: " + uri + ": " + requestHeaders);
			Map<String, List<String>> result = super.get(uri, requestHeaders);
			if (uri.toString().contains("/eid-idp")) {
				if (null != this.sessionCookie) {
					result.get("Cookie")
							.add("JSESSIONID=" + this.sessionCookie);
				}
			}
			LOG.debug("result: " + result);
			return result;
		}

		@Override
		public CookieStore getCookieStore() {
			LOG.debug("getCookieStore");
			return super.getCookieStore();
		}

		@Override
		public void put(URI uri, Map<String, List<String>> responseHeaders)
				throws IOException {
			LOG.debug("put: " + uri + "; " + responseHeaders);
			super.put(uri, responseHeaders);
		}

		@Override
		public void setCookiePolicy(CookiePolicy cookiePolicy) {
			LOG.debug("setCookiePolicy");
			super.setCookiePolicy(cookiePolicy);
		}
	}

	private static class MyTrustManager implements X509TrustManager {

		private static final Log LOG = LogFactory.getLog(MyTrustManager.class);

		public void checkClientTrusted(X509Certificate[] chain, String authType)
				throws CertificateException {
			LOG.error("checkClientTrusted");
			throw new UnsupportedOperationException();
		}

		public void checkServerTrusted(X509Certificate[] chain, String authType)
				throws CertificateException {
			LOG.debug("check server trusted");
			LOG.debug("auth type: " + authType);
		}

		public X509Certificate[] getAcceptedIssuers() {
			LOG.error("getAcceptedIssuers");
			throw new UnsupportedOperationException();
		}
	}

	public static class MyProtocolSocketFactory implements
			ProtocolSocketFactory {
		private final SSLContext sslContext;

		public MyProtocolSocketFactory() throws NoSuchAlgorithmException,
				KeyManagementException {
			this.sslContext = SSLContext.getInstance("SSL");
			TrustManager trustManager = new MyTrustManager();
			TrustManager[] trustManagers = { trustManager };
			this.sslContext.init(null, trustManagers, null);
		}

		public Socket createSocket(String host, int port) throws IOException,
				UnknownHostException {
			return this.sslContext.getSocketFactory().createSocket(host, port);
		}

		public Socket createSocket(String host, int port,
				InetAddress localHost, int localPort) throws IOException,
				UnknownHostException {
			return this.sslContext.getSocketFactory().createSocket(host, port,
					localHost, localPort);
		}

		public Socket createSocket(String host, int port,
				InetAddress localHost, int localPort,
				HttpConnectionParams params) throws IOException,
				UnknownHostException, ConnectTimeoutException {
			return this.sslContext.getSocketFactory().createSocket(host, port,
					localHost, localPort);
		}

	}

	private class TestRuntime implements Runtime {

		private Applet applet;

		@Override
		public URL getDocumentBase() {
			LOG.debug("getDocumentBase()");
			try {
				return new URL(
						"https://localhost/eid-idp/authentication-identification.seam");
			} catch (MalformedURLException e) {
				throw new RuntimeException("URL error");
			}
		}

		@Override
		public String getParameter(String name) {
			LOG.debug("getParameter(\"" + name + "\")");
			if ("AppletService".equals(name)) {
				return "applet-authentication-identification-service";
			}
			return null;
		}

		@Override
		public void gotoTargetPage() {
			LOG.debug("gotoTargetPage()");
		}

		@Override
		public Applet getApplet() {
			if (null == this.applet) {
				Applet mockApplet = EasyMock.createMock(Applet.class);
				EasyMock.expect(mockApplet.getParameter("Language"))
						.andStubReturn(null);
				EasyMock.replay(mockApplet);
				this.applet = mockApplet;
			}
			return this.applet;
		}
	}

	private static class TestView implements View {

		@Override
		public void addDetailMessage(String detailMessage) {
			LOG.debug("detail message: " + detailMessage);
		}

		@Override
		public Component getParentComponent() {
			LOG.debug("getParentComponent()");
			return null;
		}

		@Override
		public boolean privacyQuestion(boolean includeAddress,
				boolean includePhoto, String identityDataUsage) {
			LOG.debug("privacyQuestion()");
			return true;
		}

		@Override
		public void setStatusMessage(Status status, String statusMessage) {
			LOG.debug("status message: " + status + ": " + statusMessage);
			if (Status.ERROR == status) {
				// throw new RuntimeException("status ERROR received");
			}
		}

		@Override
		public void progressIndication(int max, int current) {
			LOG.debug("progress: " + current + "/" + max);
		}
	}
}