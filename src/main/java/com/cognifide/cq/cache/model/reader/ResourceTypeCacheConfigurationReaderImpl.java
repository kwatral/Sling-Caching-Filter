package com.cognifide.cq.cache.model.reader;

import com.cognifide.cq.cache.definition.ResourceTypeCacheDefinition;
import com.cognifide.cq.cache.definition.jcr.JcrResourceTypeCacheDefinition;
import com.cognifide.cq.cache.model.CacheConstants;
import com.cognifide.cq.cache.model.InvalidationPathUtil;
import com.cognifide.cq.cache.model.ResourceTypeCacheConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.ReferenceStrategy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;

/**
 * @author Bartosz Rudnicki
 */
@Component(immediate = true)
@Service
public class ResourceTypeCacheConfigurationReaderImpl implements ResourceTypeCacheConfigurationReader {

	private final static Log log = LogFactory.getLog(ResourceTypeCacheConfigurationReaderImpl.class);

	private static final String INVALIDATION_PATH = "%s.*";

	private static final String RESOURCE_TYPE_PATH = "/apps/%s";

	@Reference(
			referenceInterface = ResourceTypeCacheDefinition.class,
			policy = ReferencePolicy.DYNAMIC,
			cardinality = ReferenceCardinality.MANDATORY_MULTIPLE,
			strategy = ReferenceStrategy.EVENT)
	private final ConcurrentMap<String, ResourceTypeCacheDefinition> resourceTypeCacheDefinitions
			= new ConcurrentHashMap<String, ResourceTypeCacheDefinition>(8);

	@Override
	public ResourceTypeCacheConfiguration readComponentConfiguration(SlingHttpServletRequest request, int defaultTime) {
		Resource requestedResource = request.getResource();

		Resource typeResource = getTypeResource(request);
		Resource cacheResource = request.getResourceResolver().getResource(typeResource, CacheConstants.CACHE_PATH);

		ResourceTypeCacheConfiguration config = createConfiguration(requestedResource, cacheResource, defaultTime);

		if (typeResource != null) {
			config.setResourceTypePath(typeResource.getPath());
		}

		return config;
	}

	public void bindResourceTypeCacheDefinition(ResourceTypeCacheDefinition resourceTypeCacheDefinition) {
		String resourceType = resourceTypeCacheDefinition.getResourceType();
		if (resourceTypeCacheDefinitions.containsKey(resourceType)) {
			log.warn("Resource type cache definition was already defined for " + resourceType);
		}
		resourceTypeCacheDefinitions.putIfAbsent(resourceType, resourceTypeCacheDefinition);
	}

	public void unbindResourceTypeCacheDefinition(ResourceTypeCacheDefinition resourceTypeCacheDefinition) {
		resourceTypeCacheDefinitions.remove(resourceTypeCacheDefinition.getResourceType());
	}

	private ResourceTypeCacheConfiguration createConfiguration(
			Resource requestedResource, Resource cacheResource, int defaultTime) {
		ResourceTypeCacheDefinition resourceTypeCacheDefinition
				= findResourceTypeCacheDefinition(requestedResource, cacheResource, defaultTime);

		return null == resourceTypeCacheDefinition
				? createDefaultResourceTypeCacheConfiguration(requestedResource, defaultTime)
				: readComponentConfiguration(requestedResource, resourceTypeCacheDefinition);
	}

	private ResourceTypeCacheDefinition findResourceTypeCacheDefinition(
			Resource requestedResource, Resource cacheResource, int defaultTime) {
		ResourceTypeCacheDefinition resourceTypeCacheDefinition = null;
		if (resourceTypeCacheDefinitions.containsKey(requestedResource.getResourceType())) {
			resourceTypeCacheDefinition = resourceTypeCacheDefinitions.get(requestedResource.getResourceType());
		} else if (null != cacheResource) {
			resourceTypeCacheDefinition
					= new JcrResourceTypeCacheDefinition(cacheResource, requestedResource, defaultTime);
		}
		return resourceTypeCacheDefinition;
	}

	private ResourceTypeCacheConfiguration createDefaultResourceTypeCacheConfiguration(
			Resource requestedResource, int defaultTime) {
		ResourceTypeCacheConfiguration config
				= new ResourceTypeCacheConfiguration(requestedResource.getResourceType(), defaultTime);
		config.addInvalidatePath(String.format(INVALIDATION_PATH, getPagePath(requestedResource.getPath())));
		return config;
	}

	/**
	 * Reads the component cache configuration.
	 */
	private ResourceTypeCacheConfiguration readComponentConfiguration(Resource requestedResource,
			ResourceTypeCacheDefinition resourceTypeCacheDefinition) {
		ResourceTypeCacheConfiguration config = new ResourceTypeCacheConfiguration(resourceTypeCacheDefinition);
		return readComponentPathsConfiguration(requestedResource, config, resourceTypeCacheDefinition);
	}

	/**
	 * Prepares a list of all paths that should be listened for changes in order to invalidate the cache of given
	 * component.
	 */
	private ResourceTypeCacheConfiguration readComponentPathsConfiguration(Resource requestedResource,
			ResourceTypeCacheConfiguration config, ResourceTypeCacheDefinition resourceTypeCacheDefinition) {
		// self change invalidation
		if (resourceTypeCacheDefinition.isInvalidateOnSelf()) {
			config.addInvalidatePath(getSelfChangeInvalidationPath(requestedResource));
		}

		// reference fields invalidation
		config.addInvalidatePaths(getReferenceFieldInvalidation(requestedResource, resourceTypeCacheDefinition));

		// custom paths invalidation
		config.addInvalidatePaths(getCustomPathInvalidation(resourceTypeCacheDefinition));

		return config;
	}

	private List<String> getCustomPathInvalidation(ResourceTypeCacheDefinition resourceTypeCacheDefinition) {
		return InvalidationPathUtil.getInvalidationPaths(resourceTypeCacheDefinition.getInvalidateOnPaths());
	}

	private List<String> getReferenceFieldInvalidation(
			Resource requestedResource, ResourceTypeCacheDefinition resourceTypeCacheDefinition) {
		List<String> result = new ArrayList<String>();
		ValueMap resourceMap = requestedResource.adaptTo(ValueMap.class);
		if (resourceMap != null) {
			for (String fieldName : resourceTypeCacheDefinition.getInvalidateOnReferencedFields()) {
				if (StringUtils.isNotBlank(fieldName)) {
					String fieldValue = resourceMap.get(fieldName, String.class);
					if (StringUtils.isNotBlank(fieldValue)) {
						result.add(String.format(INVALIDATION_PATH, fieldValue));
					}
				}
			}
		}
		return result;
	}

	private String getSelfChangeInvalidationPath(Resource requestedResource) {
		return String.format(INVALIDATION_PATH, getPagePath(requestedResource.getPath()));
	}

	/**
	 * Returns the Resource of the type of the requested component.
	 */
	private Resource getTypeResource(SlingHttpServletRequest request) {
		return request.getResourceResolver().getResource(
				getAbsoluteTypePath(request.getResource().getResourceType()));
	}

	private String getAbsoluteTypePath(String path) {
		return path.startsWith("/") ? path : String.format(RESOURCE_TYPE_PATH, path);
	}

	private String getPagePath(String componentPath) {
		int jcrContentIndex = componentPath.indexOf("/jcr:content");
		return jcrContentIndex > 0 ? componentPath.substring(0, jcrContentIndex) : componentPath;
	}
}