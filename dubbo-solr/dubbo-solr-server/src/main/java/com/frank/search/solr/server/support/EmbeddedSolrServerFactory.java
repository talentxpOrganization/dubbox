/*
 * Copyright 2012 - 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.frank.search.solr.server.support;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.core.CoreContainer;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.DisposableBean;
import com.frank.search.solr.server.SolrClientFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ResourceUtils;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The EmbeddedSolrServerFactory allows hosting of an SolrServer instance in
 * embedded mode. Configuration files are loaded via
 * {@link org.springframework.util.ResourceUtils}, therefore it is possible to
 * place them in classpath. Use this class for Testing. It is not recommended
 * for production.
 *
 * @author Christoph Strobl
 */
public class EmbeddedSolrServerFactory implements SolrClientFactory, DisposableBean {

	private static final String SOLR_HOME_SYSTEM_PROPERTY = "solr.solr.home";

	private String solrHome;
	private EmbeddedSolrServer solrServer;

	protected EmbeddedSolrServerFactory() {

	}

	/**
	 * @param solrHome
	 *            Any Path expression valid for use with
	 *            {@link org.springframework.util.ResourceUtils} that points to
	 *            the {@code solr.solr.home} directory
	 * @throws javax.xml.parsers.ParserConfigurationException
	 * @throws java.io.IOException
	 * @throws org.xml.sax.SAXException
	 */
	public EmbeddedSolrServerFactory(String solrHome) throws ParserConfigurationException, IOException, SAXException {
		Assert.hasText(solrHome);
		this.solrHome = solrHome;
	}

	@Override
	public EmbeddedSolrServer getSolrClient() {
		if (this.solrServer == null) {
			initSolrServer();
		}
		return this.solrServer;
	}

	protected void initSolrServer() {
		try {
			this.solrServer = createPathConfiguredSolrServer(this.solrHome);
		} catch (ParserConfigurationException e) {
			throw new BeanInstantiationException(EmbeddedSolrServer.class, e.getMessage(), e);
		} catch (IOException e) {
			throw new BeanInstantiationException(EmbeddedSolrServer.class, e.getMessage(), e);
		} catch (SAXException e) {
			throw new BeanInstantiationException(EmbeddedSolrServer.class, e.getMessage(), e);
		}
	}

	/**
	 * @param path
	 *            Any Path expression valid for use with
	 *            {@link org.springframework.util.ResourceUtils}
	 * @return new instance of
	 *         {@link org.apache.solr.client.solrj.embedded.EmbeddedSolrServer}
	 * @throws javax.xml.parsers.ParserConfigurationException
	 * @throws java.io.IOException
	 * @throws org.xml.sax.SAXException
	 */
	public final EmbeddedSolrServer createPathConfiguredSolrServer(String path)
			throws ParserConfigurationException, IOException, SAXException {
		String solrHomeDirectory = System.getProperty(SOLR_HOME_SYSTEM_PROPERTY);

		if (StringUtils.isBlank(solrHomeDirectory)) {
			solrHomeDirectory = ResourceUtils.getURL(path).getPath();
		}

		solrHomeDirectory = URLDecoder.decode(solrHomeDirectory, "utf-8");
		return new EmbeddedSolrServer(createCoreContainer(solrHomeDirectory), "collection1");
	}

	private CoreContainer createCoreContainer(String solrHomeDirectory) {
		File solrXmlFile = new File(solrHomeDirectory + "/solr.xml");
		if (ClassUtils.hasConstructor(CoreContainer.class, String.class, File.class)) {
			return createCoreContainerViaConstructor(solrHomeDirectory, solrXmlFile);
		}
		return createCoreContainer(solrHomeDirectory, solrXmlFile);
	}

	/**
	 * Create {@link org.apache.solr.core.CoreContainer} via its constructor
	 * (Solr 3.6.0 - 4.3.1)
	 *
	 * @param solrHomeDirectory
	 * @param solrXmlFile
	 * @return
	 */
	private CoreContainer createCoreContainerViaConstructor(String solrHomeDirectory, File solrXmlFile) {
		Constructor<CoreContainer> constructor = ClassUtils.getConstructorIfAvailable(CoreContainer.class, String.class,
				File.class);
		return BeanUtils.instantiateClass(constructor, solrHomeDirectory, solrXmlFile);
	}

	/**
	 * Create {@link org.apache.solr.core.CoreContainer} for Solr version 4.4+
	 * 
	 * @param solrHomeDirectory
	 * @param solrXmlFile
	 * @return
	 */
	private CoreContainer createCoreContainer(String solrHomeDirectory, File solrXmlFile) {
		return CoreContainer.createAndLoad(solrHomeDirectory, solrXmlFile);
	}

	public void shutdownSolrServer() {
		if (this.solrServer != null && solrServer.getCoreContainer() != null) {
			solrServer.getCoreContainer().shutdown();
		}
	}

	@Override
	public List<String> getCores() {
		if (this.solrServer != null && solrServer.getCoreContainer() != null) {
			return new ArrayList<String>(this.solrServer.getCoreContainer().getCoreNames());
		}
		return Collections.emptyList();
	}

	public void setSolrHome(String solrHome) {
		Assert.hasText(solrHome);
		this.solrHome = solrHome;
	}

	@Override
	public void destroy() throws Exception {
		shutdownSolrServer();
	}

	@Override
	public SolrClient getSolrClient(String core) {
		return getSolrClient();
	}

}
