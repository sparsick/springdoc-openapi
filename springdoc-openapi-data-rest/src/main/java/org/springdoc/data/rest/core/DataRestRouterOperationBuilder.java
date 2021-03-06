/*
 *
 *  *
 *  *  *
 *  *  *  * Copyright 2019-2020 the original author or authors.
 *  *  *  *
 *  *  *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  *  *  * you may not use this file except in compliance with the License.
 *  *  *  * You may obtain a copy of the License at
 *  *  *  *
 *  *  *  *      https://www.apache.org/licenses/LICENSE-2.0
 *  *  *  *
 *  *  *  * Unless required by applicable law or agreed to in writing, software
 *  *  *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  *  *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *  *  * See the License for the specific language governing permissions and
 *  *  *  * limitations under the License.
 *  *  *
 *  *
 *
 *
 */

package org.springdoc.data.rest.core;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.swagger.v3.core.util.PathUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.MethodAttributes;
import org.springdoc.core.SpringDocConfigProperties;
import org.springdoc.core.fn.RouterOperation;
import org.springdoc.data.rest.DataRestHalProvider;

import org.springframework.data.rest.core.Path;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.core.mapping.HttpMethods;
import org.springframework.data.rest.core.mapping.MethodResourceMapping;
import org.springframework.data.rest.core.mapping.ResourceMetadata;
import org.springframework.data.rest.core.mapping.ResourceType;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

public class DataRestRouterOperationBuilder {

	private static final List<RequestMethod> UNDOCUMENTED_REQUEST_METHODS = Arrays.asList(RequestMethod.OPTIONS, RequestMethod.HEAD);

	private static final String REPOSITORY_PATH = AntPathMatcher.DEFAULT_PATH_SEPARATOR + "{repository}";

	private static final String SEARCH_PATH = AntPathMatcher.DEFAULT_PATH_SEPARATOR + "{search}";

	private DataRestOperationBuilder dataRestOperationBuilder;

	private SpringDocConfigProperties springDocConfigProperties;

	public DataRestRouterOperationBuilder(DataRestOperationBuilder dataRestOperationBuilder, SpringDocConfigProperties springDocConfigProperties,
			RepositoryRestConfiguration repositoryRestConfiguration, DataRestHalProvider dataRestHalProvider) {
		this.dataRestOperationBuilder = dataRestOperationBuilder;
		this.springDocConfigProperties = springDocConfigProperties;
		if (dataRestHalProvider.isHalEnabled())
			springDocConfigProperties.setDefaultProducesMediaType(repositoryRestConfiguration.getDefaultMediaType().toString());
	}

	public void buildEntityRouterOperationList(List<RouterOperation> routerOperationList,
			Map<RequestMappingInfo, HandlerMethod> handlerMethodMap, ResourceMetadata resourceMetadata,
			Class<?> domainType, OpenAPI openAPI) {
		String path = resourceMetadata.getPath().toString();
		for (Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethodMap.entrySet()) {
			buildRouterOperationList(routerOperationList, resourceMetadata, domainType, openAPI, path, entry, null, ControllerType.ENTITY, null);
		}
	}

	public void buildSearchRouterOperationList(List<RouterOperation> routerOperationList,
			Map<RequestMappingInfo, HandlerMethod> handlerMethodMap, ResourceMetadata resourceMetadata,
			Class<?> domainType, OpenAPI openAPI, MethodResourceMapping methodResourceMapping) {
		String path = resourceMetadata.getPath().toString();
		Path subPath = methodResourceMapping.getPath();
		Optional<Entry<RequestMappingInfo, HandlerMethod>> entryOptional = getSearchEntry(handlerMethodMap);
		if (entryOptional.isPresent()) {
			Entry<RequestMappingInfo, HandlerMethod> entry = entryOptional.get();
			buildRouterOperationList(routerOperationList, null, domainType, openAPI, path, entry, subPath.toString(), ControllerType.SEARCH, methodResourceMapping);
		}
	}

	private void buildRouterOperationList(List<RouterOperation> routerOperationList, ResourceMetadata resourceMetadata,
			Class<?> domainType, OpenAPI openAPI, String path, Entry<RequestMappingInfo, HandlerMethod> entry,
			String subPath, ControllerType entity, MethodResourceMapping methodResourceMapping) {
		RequestMappingInfo requestMappingInfo = entry.getKey();
		HandlerMethod handlerMethod = entry.getValue();
		Set<RequestMethod> requestMethods = requestMappingInfo.getMethodsCondition().getMethods();
		if (resourceMetadata != null) {
			HttpMethods httpMethodsItem = resourceMetadata.getSupportedHttpMethods().getMethodsFor(ResourceType.ITEM);
			Set<RequestMethod> requestMethodsItem = requestMethods.stream().filter(requestMethod -> httpMethodsItem.contains(HttpMethod.valueOf(requestMethod.toString())))
					.collect(Collectors.toSet());
			HttpMethods httpMethodsCollection = resourceMetadata.getSupportedHttpMethods().getMethodsFor(ResourceType.COLLECTION);
			Set<RequestMethod> requestMethodsCollection = requestMethods.stream().filter(requestMethod -> httpMethodsCollection.contains(HttpMethod.valueOf(requestMethod.toString())))
					.collect(Collectors.toSet());
			requestMethodsItem.addAll(requestMethodsCollection);
			requestMethods = requestMethodsItem;
		}


		for (RequestMethod requestMethod : requestMethods) {
			if (!UNDOCUMENTED_REQUEST_METHODS.contains(requestMethod)) {
				PatternsRequestCondition patternsRequestCondition = requestMappingInfo.getPatternsCondition();
				Set<String> patterns = patternsRequestCondition.getPatterns();
				Map<String, String> regexMap = new LinkedHashMap<>();
				String operationPath = calculateOperationPath(path, subPath, patterns, regexMap, entity);
				buildRouterOperation(routerOperationList, domainType, openAPI, methodResourceMapping,
						handlerMethod, requestMethod, resourceMetadata, operationPath, entity);
			}
		}
	}

	private String calculateOperationPath(String path, String subPath, Set<String> patterns,
			Map<String, String> regexMap, ControllerType controllerType) {
		String operationPath = null;
		for (String pattern : patterns) {
			operationPath = PathUtils.parsePath(pattern, regexMap);
			operationPath = operationPath.replace(REPOSITORY_PATH, path);
			if (ControllerType.SEARCH.equals(controllerType))
				operationPath = operationPath.replace(SEARCH_PATH, subPath);
		}
		return operationPath;
	}

	private void buildRouterOperation(List<RouterOperation> routerOperationList, Class<?> domainType, OpenAPI openAPI,
			MethodResourceMapping methodResourceMapping, HandlerMethod handlerMethod,
			RequestMethod requestMethod, ResourceMetadata resourceMetadata, String operationPath, ControllerType controllerType) {
		RouterOperation routerOperation = new RouterOperation(operationPath, new RequestMethod[] { requestMethod });
		MethodAttributes methodAttributes = new MethodAttributes(springDocConfigProperties.getDefaultConsumesMediaType(), springDocConfigProperties.getDefaultProducesMediaType());
		methodAttributes.calculateConsumesProduces(handlerMethod.getMethod());
		routerOperation.setConsumes(methodAttributes.getMethodConsumes());
		routerOperation.setProduces(methodAttributes.getMethodProduces());
		Operation operation = dataRestOperationBuilder.buildOperation(handlerMethod, domainType,
				openAPI, requestMethod, operationPath, methodAttributes, resourceMetadata, methodResourceMapping, controllerType);
		routerOperation.setOperationModel(operation);
		routerOperationList.add(routerOperation);
	}


	private Optional<Entry<RequestMappingInfo, HandlerMethod>> getSearchEntry(Map<RequestMappingInfo, HandlerMethod> handlerMethodMap) {
		return handlerMethodMap.entrySet().stream().filter(
				requestMappingInfoHandlerMethodEntry -> {
					RequestMappingInfo requestMappingInfo = requestMappingInfoHandlerMethodEntry.getKey();
					HandlerMethod handlerMethod = requestMappingInfoHandlerMethodEntry.getValue();
					Set<RequestMethod> requestMethods = requestMappingInfo.getMethodsCondition().getMethods();
					for (RequestMethod requestMethod : requestMethods) {
						if (isSearchControllerPresent(requestMappingInfo, handlerMethod, requestMethod))
							return true;
					}
					return false;
				}).findAny();
	}

	private boolean isSearchControllerPresent(RequestMappingInfo requestMappingInfo, HandlerMethod handlerMethod, RequestMethod requestMethod) {
		if (!UNDOCUMENTED_REQUEST_METHODS.contains(requestMethod)) {
			PatternsRequestCondition patternsRequestCondition = requestMappingInfo.getPatternsCondition();
			Set<String> patterns = patternsRequestCondition.getPatterns();
			Map<String, String> regexMap = new LinkedHashMap<>();
			String operationPath;
			for (String pattern : patterns) {
				operationPath = PathUtils.parsePath(pattern, regexMap);
				if (operationPath.contains(REPOSITORY_PATH) && operationPath.contains(SEARCH_PATH)) {
					MethodAttributes methodAttributes = new MethodAttributes(springDocConfigProperties.getDefaultConsumesMediaType(), springDocConfigProperties.getDefaultProducesMediaType());
					methodAttributes.calculateConsumesProduces(handlerMethod.getMethod());
					if (springDocConfigProperties.getDefaultProducesMediaType().equals(methodAttributes.getMethodProduces()[0]))
						return true;
				}
			}
		}
		return false;
	}

}
