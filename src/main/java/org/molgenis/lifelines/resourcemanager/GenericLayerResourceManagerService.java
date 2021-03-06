package org.molgenis.lifelines.resourcemanager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.log4j.Logger;
import org.molgenis.atom.ContentType;
import org.molgenis.atom.EntryType;
import org.molgenis.atom.FeedType;
import org.molgenis.catalog.CatalogMeta;
import org.molgenis.hl7.ED;
import org.molgenis.hl7.II;
import org.molgenis.hl7.INT;
import org.molgenis.hl7.ObjectFactory;
import org.molgenis.hl7.POQMMT000001UVQualityMeasureDocument;
import org.molgenis.hl7.ST;
import org.molgenis.lifelines.studymanager.QualityMeasureDocumentStudyDefinition;
import org.molgenis.lifelines.utils.GenericLayerDataBinder;
import org.molgenis.lifelines.utils.OutputStreamHttpEntity;
import org.molgenis.study.StudyDefinitionMeta;
import org.w3c.dom.Node;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Connection to the LL Resource Manager REST Service
 * 
 * 
 * @author erwin
 * 
 */
public class GenericLayerResourceManagerService
{
	private static final Logger LOG = Logger.getLogger(GenericLayerResourceManagerService.class);

	private static final JAXBContext JAXB_CONTEXT_ATOM;

	static
	{
		try
		{
			JAXB_CONTEXT_ATOM = JAXBContext.newInstance("org.molgenis.atom");
		}
		catch (JAXBException e)
		{
			throw new RuntimeException(e);
		}
	}

	private final HttpClient httpClient;
	private final String resourceManagerServiceUrl;
	private final GenericLayerDataBinder genericLayerDataBinder;

	public GenericLayerResourceManagerService(HttpClient httpClient, String resourceManagerServiceUrl,
			GenericLayerDataBinder genericLayerDataBinder)
	{
		if (httpClient == null) throw new IllegalArgumentException("HttpClient is null");
		if (resourceManagerServiceUrl == null) throw new IllegalArgumentException("ResourceManagerServiceUrl is null");
		if (genericLayerDataBinder == null) throw new IllegalArgumentException("GenericLayerDataBinder is null");
		this.httpClient = httpClient;
		this.resourceManagerServiceUrl = resourceManagerServiceUrl;
		this.genericLayerDataBinder = genericLayerDataBinder;
	}

	/**
	 * Gets all available StudyDefinitions
	 * 
	 * @return
	 */
	public StudyDefinitionMeta findStudyDefinition(String id)
	{
		CatalogSearchResult catalogSearchResult = findCatalogRelease("/studydefinition/" + id);

		StudyDefinitionMeta studyDefinitionInfo = new StudyDefinitionMeta(catalogSearchResult.getId(),
				catalogSearchResult.getName());
		studyDefinitionInfo.setDescription(catalogSearchResult.getDescription());
		studyDefinitionInfo.setVersion(catalogSearchResult.getVersion());
		studyDefinitionInfo.setAuthors(catalogSearchResult.getAuthors());
		return studyDefinitionInfo;
	}

	/**
	 * Gets all available StudyDefinitions
	 * 
	 * @return
	 */
	public List<StudyDefinitionMeta> findStudyDefinitions()
	{
		List<CatalogSearchResult> catalogs = findCatalogReleases("/studydefinition");
		return Lists.transform(catalogs, new Function<CatalogSearchResult, StudyDefinitionMeta>()
		{
			@Override
			public StudyDefinitionMeta apply(CatalogSearchResult input)
			{
				return new StudyDefinitionMeta(input.getId(), input.getName());
			}
		});
	}

	public POQMMT000001UVQualityMeasureDocument findStudyDefinitionHL7(String id)
	{
		HttpGet httpGet = new HttpGet(resourceManagerServiceUrl + "/studydefinition/" + id);
		InputStream xmlStream = null;
		try
		{
			HttpResponse response = httpClient.execute(httpGet);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode < 200 || statusCode >= 400)
			{
				LOG.error("Error retrieving study definition " + id + " (statuscode: " + statusCode + ")");
				throw new IOException("Error retrieving study definition (statuscode: " + statusCode + ")");
			}
			xmlStream = response.getEntity().getContent();
			return genericLayerDataBinder.createQualityMeasureDocumentUnmarshaller()
					.unmarshal(new StreamSource(xmlStream), POQMMT000001UVQualityMeasureDocument.class).getValue();
		}
		catch (RuntimeException e)
		{
			httpGet.abort();
			throw e;
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		catch (JAXBException e)
		{
			throw new RuntimeException(e);
		}
		finally
		{
			IOUtils.closeQuietly(xmlStream);
		}
	}

	/**
	 * Persist a studydefinition
	 * 
	 * @param studyDefinition
	 * @return generic layer study definition id
	 */
	public String persistStudyDefinition(final POQMMT000001UVQualityMeasureDocument studyDefinition)
	{
		HttpPost httpPost = new HttpPost(resourceManagerServiceUrl + "/studydefinition");
		httpPost.setHeader("Content-Type", "application/xml");
		httpPost.setEntity(new OutputStreamHttpEntity()
		{
			@Override
			public void writeTo(final OutputStream outstream) throws IOException
			{
				try
				{
					genericLayerDataBinder.createQualityMeasureDocumentMarshaller().marshal(
							new ObjectFactory().createQualityMeasureDocument(studyDefinition), outstream);
				}
				catch (JAXBException e)
				{
					throw new RuntimeException(e);
				}
				outstream.close();
			}
		});

		InputStream xmlStream = null;
		try
		{
			HttpResponse response = httpClient.execute(httpPost);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode < 200 || statusCode >= 400)
			{
				LOG.error("Error persisting study definition (statuscode: " + statusCode + ")");
				throw new IOException("Error persisting study definition (statuscode: " + statusCode + ")");
			}

			xmlStream = response.getEntity().getContent();
			POQMMT000001UVQualityMeasureDocument qualityMeasureDocument = genericLayerDataBinder
					.createQualityMeasureDocumentUnmarshaller()
					.unmarshal(new StreamSource(xmlStream), POQMMT000001UVQualityMeasureDocument.class).getValue();

			return qualityMeasureDocument.getId().getExtension();
		}
		catch (RuntimeException e)
		{
			httpPost.abort();
			throw e;
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		catch (JAXBException e)
		{
			throw new RuntimeException(e);
		}
		finally
		{
			IOUtils.closeQuietly(xmlStream);
		}
	}

	public void updateStudyDefinition(String studyDefinitionId,
			final POQMMT000001UVQualityMeasureDocument studyDefinition) throws JAXBException
	{
		HttpPut httpPut = new HttpPut(resourceManagerServiceUrl + "/studydefinition/" + studyDefinitionId);
		httpPut.setHeader("Content-Type", "application/xml");
		httpPut.setEntity(new OutputStreamHttpEntity()
		{
			@Override
			public void writeTo(final OutputStream outstream) throws IOException
			{
				try
				{
					genericLayerDataBinder.createQualityMeasureDocumentMarshaller().marshal(
							new ObjectFactory().createQualityMeasureDocument(studyDefinition), outstream);
				}
				catch (JAXBException e)
				{
					throw new RuntimeException(e);
				}
				outstream.close();
			}
		});

		InputStream xmlStream = null;
		try
		{
			HttpResponse response = httpClient.execute(httpPut);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode < 200 || statusCode >= 400)
			{
				LOG.error("Error updating study definition " + studyDefinitionId + " (statuscode: " + statusCode + ")");
				throw new IOException("Error updating study definition (statuscode: " + statusCode + ")");
			}
		}
		catch (RuntimeException e)
		{
			httpPut.abort();
			throw e;
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		finally
		{
			IOUtils.closeQuietly(xmlStream);
		}
	}

	/**
	 * Get the catalog with the given id
	 * 
	 * @return List of CatalogInfo
	 */
	public CatalogMeta findCatalog(String id)
	{
		CatalogSearchResult catalogSearchResult = findCatalogRelease("/catalogrelease/" + id);

		CatalogMeta catalogInfo = new CatalogMeta(catalogSearchResult.getId(), catalogSearchResult.getName());
		catalogInfo.setDescription(catalogSearchResult.getDescription());
		catalogInfo.setVersion(catalogSearchResult.getVersion());
		catalogInfo.setAuthors(catalogSearchResult.getAuthors());
		return catalogInfo;
	}

	/**
	 * Gets all available catalogs
	 * 
	 * @return List of CatalogInfo
	 */
	public List<CatalogMeta> findCatalogs()
	{
		List<CatalogSearchResult> catalogs = findCatalogReleases("/catalogrelease");
		return Lists.transform(catalogs, new Function<CatalogSearchResult, CatalogMeta>()
		{
			@Override
			public CatalogMeta apply(CatalogSearchResult input)
			{
				return new CatalogMeta(input.getId(), input.getName());
			}
		});
	}

	private CatalogSearchResult findCatalogRelease(String uri)
	{
		HttpGet httpGet = new HttpGet(resourceManagerServiceUrl + uri);
		InputStream xmlStream = null;
		try
		{
			HttpResponse response = httpClient.execute(httpGet);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode < 200 || statusCode >= 400)
			{
				LOG.error("Error retrieving catalog " + uri + " (statuscode: " + statusCode + ")");
				throw new IOException("Error retrieving catalog (statuscode: " + statusCode + ")");
			}
			xmlStream = response.getEntity().getContent();
			POQMMT000001UVQualityMeasureDocument qualityMeasureDocument = genericLayerDataBinder
					.createQualityMeasureDocumentUnmarshaller()
					.unmarshal(new StreamSource(xmlStream), POQMMT000001UVQualityMeasureDocument.class).getValue();
			return createCatalogSearchResult(qualityMeasureDocument);
		}
		catch (RuntimeException e)
		{
			httpGet.abort();
			throw e;
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		catch (JAXBException e)
		{
			throw new RuntimeException(e);
		}
		finally
		{
			IOUtils.closeQuietly(xmlStream);
		}
	}

	private List<CatalogSearchResult> findCatalogReleases(String uri)
	{
		try
		{
			FeedType feed = getFeed(uri);

			List<CatalogSearchResult> catalogs = new ArrayList<CatalogSearchResult>();

			for (Object entryElementObj : feed.getAuthorOrCategoryOrContributor())
			{

				@SuppressWarnings("unchecked")
				EntryType entry = ((JAXBElement<EntryType>) entryElementObj).getValue();

				for (Object obj : entry.getAuthorOrCategoryOrContent())
				{
					Unmarshaller jaxbUnmarshaller = genericLayerDataBinder.createQualityMeasureDocumentUnmarshaller();

					JAXBElement<?> element = (JAXBElement<?>) obj;
					if (element.getDeclaredType() == ContentType.class)
					{
						ContentType content = (ContentType) element.getValue();
						Node qualityMeasureDocumentNode = (Node) content.getContent().get(0);

						JAXBElement<POQMMT000001UVQualityMeasureDocument> qualityMeasureDocumentElement = jaxbUnmarshaller
								.unmarshal(qualityMeasureDocumentNode, POQMMT000001UVQualityMeasureDocument.class);

						POQMMT000001UVQualityMeasureDocument qualityMeasureDocument = qualityMeasureDocumentElement
								.getValue();
						if (qualityMeasureDocument.getId() != null)
						{
							CatalogSearchResult catalogSearchResult = createCatalogSearchResult(qualityMeasureDocument);
							catalogs.add(catalogSearchResult);
						}
						else
						{
							LOG.error("Found QualityMeasureDocument without an id");
						}
					}
				}
			}

			return catalogs;
		}
		catch (JAXBException e)
		{
			LOG.error("JAXBException findCatalogs()", e);
			throw new RuntimeException(e);
		}
		catch (IOException e)
		{
			LOG.error("JAXBException findCatalogs()", e);
			throw new RuntimeException(e);
		}

	}

	private CatalogSearchResult createCatalogSearchResult(POQMMT000001UVQualityMeasureDocument qualityMeasureDocument)
	{
		// id and title
		II id = qualityMeasureDocument.getId();
		ST title = qualityMeasureDocument.getTitle();
		CatalogSearchResult catalogSearchResult = new CatalogSearchResult(id.getExtension(), title != null ? title
				.getContent().toString() : "");

		// description
		ED description = qualityMeasureDocument.getText();
		if (description != null)
		{
			catalogSearchResult.setDescription(description.getContent().toString());
		}

		// version
		INT versionNumber = qualityMeasureDocument.getVersionNumber();
		if (versionNumber != null)
		{
			catalogSearchResult.setVersion(versionNumber.getValue().toString());
		}

		// author(s)
		List<String> authors = new QualityMeasureDocumentStudyDefinition(qualityMeasureDocument).getAuthors();
		if (authors != null)
		{
			for (String author : authors)
				catalogSearchResult.addAuthor(author);
		}
		return catalogSearchResult;
	}

	private FeedType getFeed(String uri) throws JAXBException, IOException
	{
		Unmarshaller jaxbUnmarshaller = JAXB_CONTEXT_ATOM.createUnmarshaller();

		HttpGet httpGet = new HttpGet(resourceManagerServiceUrl + uri);
		InputStream xmlStream = null;
		try
		{
			HttpResponse response = httpClient.execute(httpGet);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode < 200 || statusCode >= 400)
			{
				LOG.error("Error retrieving catalogs or study definitions " + uri + " (statuscode: " + statusCode + ")");
				throw new IOException("Error retrieving catalogs or study definitions (statuscode: " + statusCode + ")");
			}
			xmlStream = response.getEntity().getContent();
			return jaxbUnmarshaller.unmarshal(new StreamSource(xmlStream), FeedType.class).getValue();
		}
		catch (RuntimeException e)
		{
			httpGet.abort();
			throw e;
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
		catch (JAXBException e)
		{
			throw new RuntimeException(e);
		}
		finally
		{
			IOUtils.closeQuietly(xmlStream);
		}
	}

	private static class CatalogSearchResult
	{
		private final String id;
		private final String name;
		private String description;
		private String version;
		private List<String> authors;

		public CatalogSearchResult(String id, String name)
		{
			this.id = id;
			this.name = name;
		}

		public String getId()
		{
			return id;
		}

		public String getName()
		{
			return name;
		}

		public String getDescription()
		{
			return description;
		}

		public void setDescription(String description)
		{
			this.description = description;
		}

		public String getVersion()
		{
			return version;
		}

		public void setVersion(String version)
		{
			this.version = version;
		}

		public List<String> getAuthors()
		{
			return authors != null ? authors : Collections.<String> emptyList();
		}

		public void addAuthor(String author)
		{
			if (authors == null) authors = new ArrayList<String>();
			authors.add(author);
		}
	}
}
