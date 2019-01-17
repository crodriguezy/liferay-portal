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

package com.liferay.journal.service.test;

import com.liferay.arquillian.extension.junit.bridge.junit.Arquillian;
import com.liferay.asset.kernel.service.AssetEntryLocalServiceUtil;
import com.liferay.asset.kernel.service.AssetTagLocalServiceUtil;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.service.JournalArticleLocalServiceUtil;
import com.liferay.journal.test.util.JournalTestUtil;
import com.liferay.portal.kernel.dao.orm.EntityCache;
import com.liferay.portal.kernel.dao.orm.FinderCache;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.persistence.impl.TableMapper;
import com.liferay.portal.kernel.test.rule.AggregateTestRule;
import com.liferay.portal.kernel.test.util.GroupTestUtil;
import com.liferay.portal.kernel.test.util.ServiceContextTestUtil;
import com.liferay.portal.kernel.transaction.Propagation;
import com.liferay.portal.kernel.transaction.TransactionConfig;
import com.liferay.portal.kernel.transaction.TransactionInvokerUtil;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.test.rule.LiferayIntegrationTestRule;
import com.liferay.portal.util.PropsUtil;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Mariano Álvaro Sáiz
 * @author Cristina Rodríguez Yrezábal
 */
@RunWith(Arquillian.class)
public class JournalArticleLocalServiceAssetTagTest {

	@ClassRule
	@Rule
	public static final AggregateTestRule aggregateTestRule =
		new LiferayIntegrationTestRule();

	public void queryAssetTagEntriesTwiceSpreadInTime(long tagId)
		throws Exception {

		try {
			TransactionInvokerUtil.invoke(
				_transactionConfig,
				() -> {
					AssetEntryLocalServiceUtil.getAssetTagAssetEntries(tagId);

					Thread.sleep(100);

					AssetEntryLocalServiceUtil.getAssetTagAssetEntries(tagId);

					return null;
				});
		}
		catch (Throwable t) {
			_failMessage = t.getMessage();
		}
	}

	@Before
	public void setUp() throws Exception {
		_configureProperties();

		_group = GroupTestUtil.addGroup();
		_journalArticle = _createJournalArticle();
	}

	@After
	public void tearDown() throws Exception {
		AssetTagLocalServiceUtil.deleteAssetTag(_tagId);
		JournalArticleLocalServiceUtil.deleteArticles(_group.getGroupId());
		GroupTestUtil.deleteGroup(_group);

		_restoreProperties();
	}

	@Test
	public void testAssetTagsCanBeRetrievedAfterDifferentTransactions()
		throws Exception {

		_journalArticle = _saveArticle(_journalArticle);

		long tagId = _getTagId();

		for (int i = 0; i < _SEVERAL_TIMES; i++) {
			_journalArticle = _saveArticle(_journalArticle);

			Thread threadQuery = _backgroundExecution(
				() -> _ignoreException(
					() -> queryAssetTagEntriesTwiceSpreadInTime(tagId)));

			Thread threadPublish = _backgroundExecution(
				() -> _ignoreException(
					() -> _journalArticle = _publishArticle(_journalArticle)));

			threadQuery.join();
			threadPublish.join();

			Assert.assertNull(_failMessage);
		}
	}

	private Thread _backgroundExecution(Runnable runnable) throws Exception {
		Thread thread = new Thread(runnable);

		thread.start();

		return thread;
	}

	private void _configureProperties() {
		_originalTransactionalCacheEnabled = PropsUtil.get(
			PropsKeys.TRANSACTIONAL_CACHE_ENABLED);
		_originalValueObjectEntityThreadLocalCacheMaxSize = PropsUtil.get(
			PropsKeys.VALUE_OBJECT_ENTITY_THREAD_LOCAL_CACHE_MAX_SIZE);
		_originalTransactionalCacheNames = PropsUtil.get(
			PropsKeys.TRANSACTIONAL_CACHE_NAMES);

		PropsUtil.set(PropsKeys.TRANSACTIONAL_CACHE_ENABLED, "true");
		PropsUtil.set(
			PropsKeys.VALUE_OBJECT_ENTITY_THREAD_LOCAL_CACHE_MAX_SIZE, "100");
		PropsUtil.set(
			PropsKeys.TRANSACTIONAL_CACHE_NAMES,
			EntityCache.class.getName() + "*," + FinderCache.class.getName() +
				"*," + TableMapper.class.getName() + "-*");
	}

	private JournalArticle _createJournalArticle() throws Exception {
		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		serviceContext.setAssetTagNames(new String[] {_TAG});

		return JournalTestUtil.addArticleWithWorkflow(
			_group.getGroupId(), _FOLDER_ID, _TITLE, _CONTENT, _PUBLISH,
			serviceContext);
	}

	private long _getTagId() throws PortalException {
		if (_tagId == 0) {
			long[] tagsId = AssetTagLocalServiceUtil.getTagIds(_TAG);

			_tagId = tagsId[0];
		}

		return _tagId;
	}

	private void _ignoreException(RunnableWithException runnable) {
		try {
			runnable.run();
		}
		catch (Exception e) {
		}
	}

	private JournalArticle _publishArticle(JournalArticle journalArticle)
		throws Exception {

		return _updateArticle(journalArticle, _PUBLISH);
	}

	private void _restoreProperties() {
		PropsUtil.set(
			PropsKeys.TRANSACTIONAL_CACHE_ENABLED,
			_originalTransactionalCacheEnabled);
		PropsUtil.set(
			PropsKeys.VALUE_OBJECT_ENTITY_THREAD_LOCAL_CACHE_MAX_SIZE,
			_originalValueObjectEntityThreadLocalCacheMaxSize);
		PropsUtil.set(
			PropsKeys.TRANSACTIONAL_CACHE_NAMES,
			_originalTransactionalCacheNames);
	}

	private JournalArticle _saveArticle(JournalArticle journalArticle)
		throws Exception {

		return _updateArticle(journalArticle, _DRAFT);
	}

	private JournalArticle _updateArticle(
			JournalArticle journalArticle, boolean publish)
		throws Exception {

		ServiceContext serviceContext =
			ServiceContextTestUtil.getServiceContext(_group.getGroupId());

		serviceContext.setAssetTagNames(new String[] {_TAG});

		return JournalTestUtil.updateArticle(
			journalArticle, journalArticle.getTitle(),
			journalArticle.getContent(), true, publish, serviceContext);
	}

	private static final String _CONTENT = "false";

	private static final boolean _DRAFT = false;

	private static final long _FOLDER_ID = 0L;

	private static final boolean _PUBLISH = true;

	private static final int _SEVERAL_TIMES = 10;

	private static final String _TAG = "tag";

	private static final String _TITLE = "title";

	private String _failMessage;
	private Group _group;
	private JournalArticle _journalArticle;
	private String _originalTransactionalCacheEnabled;
	private String _originalTransactionalCacheNames;
	private String _originalValueObjectEntityThreadLocalCacheMaxSize;
	private long _tagId;
	private final TransactionConfig _transactionConfig =
		TransactionConfig.Factory.create(
			Propagation.REQUIRED, new Class<?>[] {Exception.class});

	@FunctionalInterface
	private interface RunnableWithException {

		public void run() throws Exception;

	}

}