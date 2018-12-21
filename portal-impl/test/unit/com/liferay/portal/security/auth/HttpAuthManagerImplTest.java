/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.security.auth;

import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.CompanyWrapper;
import com.liferay.portal.kernel.security.auth.http.HttpAuthorizationHeader;
import com.liferay.portal.kernel.service.BaseLocalService;
import com.liferay.portal.kernel.service.CompanyLocalServiceUtil;
import com.liferay.portal.kernel.service.CompanyLocalServiceWrapper;
import com.liferay.portal.kernel.service.UserLocalServiceUtil;
import com.liferay.portal.kernel.service.UserLocalServiceWrapper;
import com.liferay.portal.kernel.servlet.HttpHeaders;
import com.liferay.portal.kernel.test.ReflectionTestUtil;
import com.liferay.portal.kernel.util.Base64;
import com.liferay.portal.kernel.util.DigesterUtil;
import com.liferay.portal.kernel.util.HttpUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.WebKeys;
import com.liferay.portal.security.auth.http.HttpAuthManagerImpl;
import com.liferay.portal.servlet.filters.secure.NonceUtil;
import com.liferay.portal.util.DigesterImpl;
import com.liferay.portal.util.HttpImpl;

import java.lang.reflect.Constructor;

import java.util.Map;

import jodd.util.StringUtil;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/**
 * @author Tomas Polesovsky
 */
public class HttpAuthManagerImplTest {

	@BeforeClass
	public static void setUpClass() {
		HttpUtil httpUtil = new HttpUtil();

		httpUtil.setHttp(new HttpImpl());
	}

	@Test
	public void testKnownNonceRetrievesUserIdOnlyOnce() throws Exception {
		long userId = 7;

		_setUpUserLocalService(userId);

		MockHttpSession mockHttpSession = new MockHttpSession();

		String nonce = "dcd98b7102dd2f0e8b11d0f600bfb0c093";

		mockHttpSession.setAttribute(WebKeys.NONCE, _getNonceObject(nonce));

		MockHttpServletRequest mockHttpServletRequest =
			_generateDigestNonceRequest(mockHttpSession);

		HttpAuthorizationHeader httpAuthorizationHeader =
			_httpAuthManagerImpl.parse(mockHttpServletRequest);

		long authorizedUserId = _httpAuthManagerImpl.getUserId(
			mockHttpServletRequest, httpAuthorizationHeader);

		Assert.assertEquals(userId, authorizedUserId);

		mockHttpServletRequest = _generateDigestNonceRequest(mockHttpSession);

		httpAuthorizationHeader = _httpAuthManagerImpl.parse(
			mockHttpServletRequest);

		authorizedUserId = _httpAuthManagerImpl.getUserId(
			mockHttpServletRequest, httpAuthorizationHeader);

		Assert.assertEquals(0L, authorizedUserId);
	}

	@Test
	public void testLPS88011() {
		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		mockHttpServletRequest.addHeader(
			HttpHeaders.AUTHORIZATION,
			"Basic " + Base64.encode("test@liferay.com:te:st".getBytes()));

		HttpAuthorizationHeader httpAuthorizationHeader =
			_httpAuthManagerImpl.parse(mockHttpServletRequest);

		Assert.assertEquals(
			"te:st",
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_PASSWORD));
	}

	@Test
	public void testParseBasic() {
		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		mockHttpServletRequest.addHeader(
			HttpHeaders.AUTHORIZATION,
			"Basic " + Base64.encode("test@liferay.com:test".getBytes()));

		HttpAuthorizationHeader httpAuthorizationHeader =
			_httpAuthManagerImpl.parse(mockHttpServletRequest);

		Assert.assertEquals(
			HttpAuthorizationHeader.SCHEME_BASIC,
			httpAuthorizationHeader.getScheme());

		Map<String, String> authParameters =
			httpAuthorizationHeader.getAuthParameters();

		Assert.assertEquals(
			authParameters.toString(), 2, authParameters.size());

		Assert.assertEquals(
			"test@liferay.com",
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_USERNAME));

		Assert.assertEquals(
			"test",
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_PASSWORD));
	}

	@Test
	public void testParseBasicNoCredentials() {
		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		mockHttpServletRequest.addHeader(
			HttpHeaders.AUTHORIZATION,
			"Basic " + Base64.encode("test@liferay.com".getBytes()));

		HttpAuthorizationHeader httpAuthorizationHeader =
			_httpAuthManagerImpl.parse(mockHttpServletRequest);

		Assert.assertEquals(
			HttpAuthorizationHeader.SCHEME_BASIC,
			httpAuthorizationHeader.getScheme());

		Map<String, String> authParameters =
			httpAuthorizationHeader.getAuthParameters();

		Assert.assertEquals(
			authParameters.toString(), 2, authParameters.size());

		Assert.assertEquals(
			"test@liferay.com",
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_USERNAME));

		Assert.assertEquals(
			null,
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_PASSWORD));

		mockHttpServletRequest = new MockHttpServletRequest();

		mockHttpServletRequest.addHeader(
			HttpHeaders.AUTHORIZATION,
			"Basic " + Base64.encode("test@liferay.com:".getBytes()));

		httpAuthorizationHeader = _httpAuthManagerImpl.parse(
			mockHttpServletRequest);

		Assert.assertEquals(
			StringPool.BLANK,
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_PASSWORD));

		mockHttpServletRequest = new MockHttpServletRequest();

		mockHttpServletRequest.addHeader(
			HttpHeaders.AUTHORIZATION,
			"Basic " + Base64.encode(":".getBytes()));

		httpAuthorizationHeader = _httpAuthManagerImpl.parse(
			mockHttpServletRequest);

		Assert.assertEquals(
			StringPool.BLANK,
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_USERNAME));

		Assert.assertEquals(
			StringPool.BLANK,
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_PASSWORD));
	}

	@Test
	public void testParseBasicTrimValues() {
		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		mockHttpServletRequest.addHeader(
			HttpHeaders.AUTHORIZATION,
			"Basic " + Base64.encode(" test@liferay.com : test ".getBytes()));

		HttpAuthorizationHeader httpAuthorizationHeader =
			_httpAuthManagerImpl.parse(mockHttpServletRequest);

		Assert.assertEquals(
			HttpAuthorizationHeader.SCHEME_BASIC,
			httpAuthorizationHeader.getScheme());

		Map<String, String> authParameters =
			httpAuthorizationHeader.getAuthParameters();

		Assert.assertEquals(
			authParameters.toString(), 2, authParameters.size());

		Assert.assertEquals(
			"test@liferay.com",
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_USERNAME));

		Assert.assertEquals(
			"test",
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_PASSWORD));
	}

	@Test
	public void testParseBasicURLDecode() {
		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		mockHttpServletRequest.addHeader(
			HttpHeaders.AUTHORIZATION,
			"Basic " +
				Base64.encode("test%40liferay%253ecom:test%40".getBytes()));

		HttpAuthorizationHeader httpAuthorizationHeader =
			_httpAuthManagerImpl.parse(mockHttpServletRequest);

		Assert.assertEquals(
			HttpAuthorizationHeader.SCHEME_BASIC,
			httpAuthorizationHeader.getScheme());

		Map<String, String> authParameters =
			httpAuthorizationHeader.getAuthParameters();

		Assert.assertEquals(
			authParameters.toString(), 2, authParameters.size());

		Assert.assertEquals(
			"test@liferay%3ecom",
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_USERNAME));

		Assert.assertEquals(
			"test%40",
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_PASSWORD));
	}

	@Test
	public void testParseDigest() {
		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		String[] digestParams = {
			"cnonce=\"0a4f113b\"", "nc=00000001",
			"nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\"",
			"opaque=\"5ccc069c403ebaf9f0171e9517f40e41", "qop=auth",
			"realm=\"testrealm@host.com\"",
			"response=\"6629fae49393a05397450978507c4ef1\"",
			"uri=\"/dir/index.html\"", "username=\"Mufasa\""
		};

		mockHttpServletRequest.addHeader(
			HttpHeaders.AUTHORIZATION,
			"DIGEST " + StringUtil.join(digestParams, ",\n"));

		HttpAuthorizationHeader httpAuthorizationHeader =
			_httpAuthManagerImpl.parse(mockHttpServletRequest);

		Assert.assertEquals(
			HttpAuthorizationHeader.SCHEME_DIGEST,
			httpAuthorizationHeader.getScheme());

		Map<String, String> authParameters =
			httpAuthorizationHeader.getAuthParameters();

		Assert.assertEquals(
			authParameters.toString(), digestParams.length,
			authParameters.size());

		Assert.assertEquals(
			"dcd98b7102dd2f0e8b11d0f600bfb0c093",
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_NONCE));

		Assert.assertEquals(
			"testrealm@host.com",
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_REALM));

		Assert.assertEquals(
			"/dir/index.html",
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_URI));

		Assert.assertEquals(
			"Mufasa",
			httpAuthorizationHeader.getAuthParameter(
				HttpAuthorizationHeader.AUTH_PARAMETER_NAME_USERNAME));
	}

	@Test
	public void testParseNullAuthorization() {
		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		Assert.assertEquals(
			null, _httpAuthManagerImpl.parse(mockHttpServletRequest));

		mockHttpServletRequest = new MockHttpServletRequest();

		mockHttpServletRequest.addHeader(
			HttpHeaders.AUTHORIZATION, StringPool.BLANK);

		Assert.assertEquals(
			null, _httpAuthManagerImpl.parse(mockHttpServletRequest));
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testParseUnsupportedAuthorizationHeader() {
		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		mockHttpServletRequest.addHeader(
			HttpHeaders.AUTHORIZATION, "Unsupported");

		_httpAuthManagerImpl.parse(mockHttpServletRequest);
	}

	@Test
	public void testUnknownNonceDoesNotRetrieveUserId() throws Exception {
		_setUpCompany();

		_setUpDigesterUtil();

		MockHttpSession mockHttpSession = new MockHttpSession();

		MockHttpServletRequest mockHttpServletRequest = _generateDigestRequest(
			mockHttpSession);

		HttpAuthorizationHeader httpAuthorizationHeader =
			new HttpAuthorizationHeader(HttpAuthorizationHeader.SCHEME_DIGEST);

		_httpAuthManagerImpl.generateChallenge(
			mockHttpServletRequest, new MockHttpServletResponse(),
			httpAuthorizationHeader);

		mockHttpServletRequest = _generateDigestNonceRequest(mockHttpSession);

		httpAuthorizationHeader = _httpAuthManagerImpl.parse(
			mockHttpServletRequest);

		long userId = _httpAuthManagerImpl.getUserId(
			mockHttpServletRequest, httpAuthorizationHeader);

		Assert.assertEquals(0, userId);
	}

	private MockHttpServletRequest _generateDigestNonceRequest(
		MockHttpSession mockHttpSession) {

		String uri = "/dir/index.html";

		String[] digestParams = {
			"cnonce=\"0a4f113b\"", "nc=00000001",
			"nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\"",
			"opaque=\"5ccc069c403ebaf9f0171e9517f40e41", "qop=auth",
			"realm=\"" + Portal.PORTAL_REALM + "\"",
			"response=\"6629fae49393a05397450978507c4ef1\"",
			"uri=\"" + uri + "\"", "username=\"Mufasa\""
		};

		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		mockHttpServletRequest.setAttribute(WebKeys.COMPANY_ID, 0L);

		mockHttpServletRequest.setSession(mockHttpSession);

		mockHttpServletRequest.addHeader(
			HttpHeaders.AUTHORIZATION,
			"DIGEST " + StringUtil.join(digestParams, ",\n"));

		mockHttpServletRequest.setRequestURI(uri);

		return mockHttpServletRequest;
	}

	private MockHttpServletRequest _generateDigestRequest(
		MockHttpSession mockHttpSession) {

		MockHttpServletRequest mockHttpServletRequest =
			new MockHttpServletRequest();

		mockHttpServletRequest.setSession(mockHttpSession);

		mockHttpServletRequest.setAttribute(WebKeys.COMPANY_ID, 0L);

		return mockHttpServletRequest;
	}

	private Object _getNonceObject(String nonce) throws Exception {
		Class<?> inner = Class.forName(NonceUtil.class.getName() + "$Nonce");

		Constructor<?> constructor = inner.getDeclaredConstructor(String.class);

		constructor.setAccessible(true);

		return constructor.newInstance(nonce);
	}

	private void _setService(Class<?> clazz, BaseLocalService service) {
		ReflectionTestUtil.setFieldValue(clazz, "_service", service);
	}

	private void _setUpCompany() {
		_setService(
			CompanyLocalServiceUtil.class,
			new CompanyLocalServiceWrapper(null) {

				@Override
				public Company getCompanyById(long companyId) {
					return new CompanyWrapper(null) {

						@Override
						public String getKey() {
							return "key";
						}

					};
				}

			});
	}

	private void _setUpDigesterUtil() {
		DigesterUtil digesterUtil = new DigesterUtil();

		digesterUtil.setDigester(new DigesterImpl());
	}

	private void _setUpUserLocalService(long userId) {
		_setService(
			UserLocalServiceUtil.class,
			new UserLocalServiceWrapper(null) {

				public long authenticateForDigest(
					long companyId, String username, String realm, String nonce,
					String method, String uri, String response) {

					return userId;
				}

			});
	}

	private final HttpAuthManagerImpl _httpAuthManagerImpl =
		new HttpAuthManagerImpl();

}