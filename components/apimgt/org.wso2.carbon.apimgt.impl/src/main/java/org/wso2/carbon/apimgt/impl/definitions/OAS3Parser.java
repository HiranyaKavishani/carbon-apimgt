/*
 *   Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.apimgt.impl.definitions;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIDefinition;
import org.wso2.carbon.apimgt.api.APIDefinitionValidationResponse;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.ErrorItem;
import org.wso2.carbon.apimgt.api.ExceptionCodes;
import org.wso2.carbon.apimgt.api.model.Scope;
import org.wso2.carbon.apimgt.api.model.SwaggerData;
import org.wso2.carbon.apimgt.api.model.URITemplate;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Models API definition using OAS (OpenAPI 3.0) parser
 */
public class OAS3Parser extends APIDefinition {
    private static final Log log = LogFactory.getLog(OAS3Parser.class);

    /**
     * This method returns URI templates according to the given swagger file
     *
     * @param swaggerData                 API
     * @param resourceConfigsJSON swaggerJSON
     * @return URI Templates
     * @throws APIManagementException
     */
    @Override
    public Set<URITemplate> getURITemplates(SwaggerData swaggerData, String resourceConfigsJSON) throws APIManagementException {
        OpenAPIV3Parser openAPIV3Parser = new OpenAPIV3Parser();
        SwaggerParseResult parseAttemptForV3 = openAPIV3Parser.readContents(resourceConfigsJSON, null, null);
        if (CollectionUtils.isNotEmpty(parseAttemptForV3.getMessages())) {
            throw new APIManagementException("Error Occurred while parsing OpenAPI3 definition.");
        }
        OpenAPI openAPI = parseAttemptForV3.getOpenAPI();
        Set<URITemplate> urlTemplates = new LinkedHashSet<>();
        Set<Scope> scopes = getScopes(resourceConfigsJSON);
        String oauth2SchemeKey = getOAuth2SecuritySchemeKey(openAPI);

        for (String pathKey : openAPI.getPaths().keySet()) {
            PathItem pathItem = openAPI.getPaths().get(pathKey);
            for (Map.Entry<PathItem.HttpMethod, Operation> entry : pathItem.readOperationsMap().entrySet()) {
                Operation operation = entry.getValue();
                URITemplate template = new URITemplate();
                if (APIConstants.SUPPORTED_METHODS.contains(entry.getKey().name().toLowerCase())) {
                    template.setHTTPVerb(entry.getKey().name().toUpperCase());
                    template.setHttpVerbs(entry.getKey().name().toUpperCase());
                    template.setUriTemplate(pathKey);
                    List<String> opScopes = getScopeOfOperations(oauth2SchemeKey, operation);
                    if (!opScopes.isEmpty()) {
                        if (opScopes.size() == 1) {
                            String firstScope = opScopes.get(0);
                            Scope scope = APIUtil.findScopeByKey(scopes, firstScope);
                            if (scope == null) {
                                throw new APIManagementException("Scope '" + firstScope + "' not found.");
                            }
                            template.setScope(scope);
                        } else {
                            template = OASParserUtil.setScopesToTemplate(template, opScopes);
                        }
                    }
                    Map<String, Object> extensios = operation.getExtensions();
                    if (extensios != null) {
                        if (extensios.containsKey(APIConstants.SWAGGER_X_AUTH_TYPE)) {
                            String scopeKey = (String) extensios.get(APIConstants.SWAGGER_X_AUTH_TYPE);
                            template.setAuthType(scopeKey);
                            template.setAuthTypes(scopeKey);
                        } else {
                            template.setAuthType("Any");
                            template.setAuthTypes("Any");
                        }
                        if (extensios.containsKey(APIConstants.SWAGGER_X_THROTTLING_TIER)) {
                            String throttlingTier = (String) extensios.get(APIConstants.SWAGGER_X_THROTTLING_TIER);
                            template.setThrottlingTier(throttlingTier);
                            template.setThrottlingTiers(throttlingTier);
                        }
                        if (extensios.containsKey(APIConstants.SWAGGER_X_MEDIATION_SCRIPT)) {
                            String mediationScript = (String) extensios.get(APIConstants.SWAGGER_X_MEDIATION_SCRIPT);
                            template.setMediationScript(mediationScript);
                            template.setMediationScripts(template.getHTTPVerb(), mediationScript);
                        }
                    }
                    urlTemplates.add(template);
                }
            }
        }
        return urlTemplates;
    }

    /**
     * This method returns the oauth scopes according to the given swagger
     *
     * @param resourceConfigsJSON resource json
     * @return scope set
     * @throws APIManagementException
     */
    @Override
    public Set<Scope> getScopes(String resourceConfigsJSON) throws APIManagementException {
        OpenAPIV3Parser openAPIV3Parser = new OpenAPIV3Parser();
        SwaggerParseResult parseAttemptForV3 = openAPIV3Parser.readContents(resourceConfigsJSON, null, null);
        if (CollectionUtils.isNotEmpty(parseAttemptForV3.getMessages())) {
            throw new APIManagementException("Error Occurred while parsing OpenAPI3 definition.");
        }
        OpenAPI openAPI = parseAttemptForV3.getOpenAPI();
        String oauth2SchemeKey = getOAuth2SecuritySchemeKey(openAPI);
        Map<String, SecurityScheme> securitySchemes;
        SecurityScheme securityScheme;
        OAuthFlow oAuthFlow;
        Scopes scopes;
        if (openAPI.getComponents() != null && (securitySchemes = openAPI.getComponents().getSecuritySchemes()) != null
                && (securityScheme = securitySchemes.get(oauth2SchemeKey)) != null
                && (oAuthFlow = securityScheme.getFlows().getImplicit()) != null
                && (scopes = oAuthFlow.getScopes()) != null) {
            Set<Scope> scopeSet = new HashSet<>();
            for (Map.Entry<String, String> entry : scopes.entrySet()) {
                Scope scope = new Scope();
                scope.setKey(entry.getKey());
                scope.setName(entry.getKey());
                scope.setDescription(entry.getValue());
                scopeSet.add(scope);
                Map<String, String> scopeBindings;
                if (oAuthFlow.getExtensions() != null &&
                        (scopeBindings = (Map<String, String>) oAuthFlow.getExtensions()
                                .get(APIConstants.SWAGGER_X_SCOPES_BINDINGS)) != null) {
                    if (scopeBindings.get(scope.getKey()) != null) {
                        scope.setRoles(scopeBindings.get(scope.getKey()));
                    }
                }
            }
            return scopeSet;
        } else {
            return getScopesFromExtensions(openAPI);
        }
    }

    /**
     * This method generates API definition to the given api
     *
     * @param swaggerData api
     * @return API definition in string format
     * @throws APIManagementException
     */
    @Override
    public String generateAPIDefinition(SwaggerData swaggerData) throws APIManagementException {
        OpenAPI openAPI = new OpenAPI();

        //Create info object
        Info info = new Info();
        info.setTitle(swaggerData.getTitle());
        if (swaggerData.getDescription() != null) {
            info.setDescription(swaggerData.getDescription());
        }

        Contact contact = new Contact();
        //Create contact object and map business owner info
        if (swaggerData.getContactName() != null) {
            contact.setName(swaggerData.getContactName());
        }
        if (swaggerData.getContactEmail() != null) {
            contact.setEmail(swaggerData.getContactEmail());
        }
        if (swaggerData.getContactName() != null || swaggerData.getContactEmail() != null) {
            //put contact object to info object
            info.setContact(contact);
        }

        info.setVersion(swaggerData.getVersion());
        openAPI.setInfo(info);
        updateSwaggerSecurityDefinition(openAPI, swaggerData);
        for (SwaggerData.Resource resource : swaggerData.getResources()) {
            addOrUpdatePathToSwagger(openAPI, resource);
        }
        return Json.pretty(openAPI);
    }

    /**
     * This method generates API definition using the given api's URI templates and the swagger.
     * It will alter the provided swagger definition based on the URI templates. For example: if there is a new
     * URI template which is not included in the swagger, it will be added to the swagger as a basic resource. Any
     * additional resources inside the swagger will be removed from the swagger. Changes to scopes, throtting policies,
     * on the resource will be updated on the swagger
     *
     * @param swaggerData            api
     * @param swagger        swagger definition
     * @param syncOperations whether to sync operations between API and swagger. If true, the operations of the swagger
     *                       will be synced from the API's operations. Additional operations of the swagger will be
     *                       removed and new operations of API will be added. If false, all the operations will be
     *                       taken from swagger.
     * @return API definition in string format
     * @throws APIManagementException if error occurred when generating API Definition
     */
    @Override
    public String generateAPIDefinition(SwaggerData swaggerData, String swagger, boolean syncOperations) throws APIManagementException {
        OpenAPIV3Parser openAPIV3Parser = new OpenAPIV3Parser();
        SwaggerParseResult parseAttemptForV3 = openAPIV3Parser.readContents(swagger, null, null);
        OpenAPI openAPI = parseAttemptForV3.getOpenAPI();
        Set<SwaggerData.Resource> copy = new HashSet<>(swaggerData.getResources());

        for (String pathKey : openAPI.getPaths().keySet()) {
            PathItem pathItem = openAPI.getPaths().get(pathKey);
            for (Map.Entry<PathItem.HttpMethod, Operation> entry : pathItem.readOperationsMap().entrySet()) {
                Operation operation = entry.getValue();
                boolean operationFound = false;
                for (SwaggerData.Resource resource : swaggerData.getResources()) {
                    if (pathKey.equalsIgnoreCase(resource.getPath()) && entry.getKey().name()
                            .equalsIgnoreCase(resource.getVerb())) {
                        //update operations in definition
                        operationFound = true;
                        copy.remove(resource);
                        updateOperationManagedInfo(resource, operation);
                        break;
                    }
                }
                // remove operation from definition
                if (!operationFound) {
                    pathItem.readOperationsMap().remove(entry.getKey());
                }
            }
            //remove path
        }

        //adding new opeations to the deinifition
        for (SwaggerData.Resource resource : copy) {
            addOrUpdatePathToSwagger(openAPI, resource);
        }
        updateSwaggerSecurityDefinition(openAPI, swaggerData);
        return Json.pretty(openAPI);
    }

    /**
     * Construct path parameters to the Operation
     *
     * @param operation OpenAPI operation
     * @param pathName  pathname
     */
    private void populatePathParameters(Operation operation, String pathName) {
        List<String> pathParams = getPathParamNames(pathName);
        Parameter parameter;
        if (pathParams.size() > 0) {
            for (String pathParam : pathParams) {
                parameter = new Parameter();
                parameter.setName(pathParam);
                parameter.setRequired(true);
                parameter.setIn("path");
                Schema schema = new Schema();
                schema.setType("string");
                parameter.setSchema(schema);
                operation.addParametersItem(parameter);
            }
        }
    }

    /**
     * This method validates the given OpenAPI definition by content
     *
     * @param apiDefinition     OpenAPI Definition content
     * @param returnJsonContent whether to return the converted json form of the OpenAPI definition
     * @return APIDefinitionValidationResponse object with validation information
     */
    @Override
    public APIDefinitionValidationResponse validateAPIDefinition(String apiDefinition, boolean returnJsonContent)
            throws APIManagementException {
        APIDefinitionValidationResponse validationResponse = new APIDefinitionValidationResponse();
        OpenAPIV3Parser openAPIV3Parser = new OpenAPIV3Parser();
        SwaggerParseResult parseAttemptForV3 = openAPIV3Parser.readContents(apiDefinition, null, null);
        if (CollectionUtils.isNotEmpty(parseAttemptForV3.getMessages())) {
            validationResponse.setValid(false);
            for (String message : parseAttemptForV3.getMessages()) {
                OASParserUtil.addErrorToValidationResponse(validationResponse, message);
                if (message.contains(APIConstants.OPENAPI_IS_MISSING_MSG)) {
                    ErrorItem errorItem = new ErrorItem();
                    errorItem.setErrorCode(ExceptionCodes.INVALID_OAS3_FOUND.getErrorCode());
                    errorItem.setMessage(ExceptionCodes.INVALID_OAS3_FOUND.getErrorMessage());
                    errorItem.setDescription(ExceptionCodes.INVALID_OAS3_FOUND.getErrorMessage());
                    validationResponse.getErrorItems().add(errorItem);
                }
            }
        } else {
            OpenAPI openAPI = parseAttemptForV3.getOpenAPI();
            io.swagger.v3.oas.models.info.Info info = openAPI.getInfo();
            OASParserUtil.updateValidationResponseAsSuccess(validationResponse, apiDefinition, openAPI.getOpenapi(),
                    info.getTitle(), info.getVersion(), null, info.getDescription());
            validationResponse.setParser(this);
            if (returnJsonContent) {
                validationResponse.setJsonContent(Json.pretty(parseAttemptForV3.getOpenAPI()));
            }
        }
        return validationResponse;
    }

    /**
     * Populate definition with wso2 APIM specific information
     *
     * @param oasDefinition OAS definition
     * @param swaggerData   API related Swagger data
     * @return Generated OAS definition
     * @throws APIManagementException If an error occurred
     */
    @Override
    public String populateCustomManagementInfo(String oasDefinition, SwaggerData swaggerData) throws APIManagementException {
        return generateAPIDefinition(swaggerData, oasDefinition, true);
    }

    /**
     * Retrieves the "Auth2" security scheme key
     *
     * @param openAPI OpenAPI object
     * @return "Auth2" security scheme key
     */
    private String getOAuth2SecuritySchemeKey(OpenAPI openAPI) {
        return APIConstants.SWAGGER_APIM_DEFAULT_SECURITY;
    }

    /**
     * Gets a list of scopes using the security requirements
     *
     * @param oauth2SchemeKey OAuth2 security element key
     * @param operation       Swagger path operation
     * @return list of scopes using the security requirements
     */
    private List<String> getScopeOfOperations(String oauth2SchemeKey, Operation operation) {
        List<SecurityRequirement> security = operation.getSecurity();
        if (security != null) {
            for (Map<String, List<String>> requirement : security) {
                if (requirement.get(oauth2SchemeKey) != null) {
                    return requirement.get(oauth2SchemeKey);
                }
            }
        }
        return getScopeOfOperationsFromExtensions(operation);
    }

    /**
     * Get scope of operation
     *
     * @param operation
     * @return
     */
    private List<String> getScopeOfOperationsFromExtensions(Operation operation) {
        Map<String, Object> extensions = operation.getExtensions();
        if (extensions.containsKey(APIConstants.SWAGGER_X_SCOPE)) {
            String scopeKey = (String) extensions.get(APIConstants.SWAGGER_X_SCOPE);
            return Collections.singletonList(scopeKey);
        }
        return Collections.emptyList();
    }

    /**
     * Get scope information from the extensions
     *
     * @param openAPI openAPI object
     * @return Scope set
     * @throws APIManagementException if an error occurred
     */
    private Set<Scope> getScopesFromExtensions(OpenAPI openAPI) throws APIManagementException {
        Set<Scope> scopeList = new LinkedHashSet<>();
        Map<String, Object> extensions = openAPI.getExtensions();
        if (extensions != null && extensions.containsKey(APIConstants.SWAGGER_X_WSO2_SECURITY)) {
            Map<String, Object> securityDefinitions = (Map<String, Object>) extensions
                    .get(APIConstants.SWAGGER_X_WSO2_SECURITY);
            for (Map.Entry<String, Object> entry : securityDefinitions.entrySet()) {
                Map<String, Object> securityDefinition = (Map<String, Object>) entry.getValue();
                if (securityDefinition.containsKey(APIConstants.SWAGGER_X_WSO2_SCOPES)) {
                    List<Map<String, String>> oauthScope = (List<Map<String, String>>) securityDefinition
                            .get(APIConstants.SWAGGER_X_WSO2_SCOPES);
                    for (Map<String, String> anOauthScope : oauthScope) {
                        Scope scope = new Scope();
                        scope.setKey(anOauthScope.get(APIConstants.SWAGGER_SCOPE_KEY));
                        scope.setName(anOauthScope.get(APIConstants.SWAGGER_NAME));
                        scope.setDescription(anOauthScope.get(APIConstants.SWAGGER_DESCRIPTION));
                        scope.setRoles(anOauthScope.get(APIConstants.SWAGGER_ROLES));
                        scopeList.add(scope);
                    }
                }
            }
        }
        return scopeList;
    }

    /**
     * Include Scope details to the definition
     *
     * @param openAPI openapi definition
     * @param swaggerData Swagger related API data
     */
    private void updateSwaggerSecurityDefinition(OpenAPI openAPI, SwaggerData swaggerData) {

        String oauth2SchemeKey = getOAuth2SecuritySchemeKey(openAPI);
        Map<String, SecurityScheme> securitySchemes = openAPI.getComponents().getSecuritySchemes();
        if (securitySchemes == null) {
            securitySchemes = new HashMap<>();
            openAPI.getComponents().setSecuritySchemes(securitySchemes);
        }
        SecurityScheme securityScheme = securitySchemes.get(oauth2SchemeKey);
        if (securityScheme == null) {
            securityScheme = new SecurityScheme();
            securitySchemes.put(oauth2SchemeKey, securityScheme);
        }
        OAuthFlow oAuthFlow = securityScheme.getFlows().getImplicit();
        if (oAuthFlow == null) {
            oAuthFlow = new OAuthFlow();
            oAuthFlow.setTokenUrl("https://test.com");
            securityScheme.getFlows().setImplicit(oAuthFlow);
        }
        Scopes oas3Scopes = new Scopes();
        Set<Scope> scopes = swaggerData.getScopes();
        if (scopes != null && !scopes.isEmpty()) {
            Map<String, String> scopeBindings = new HashMap<>();
            for (Scope scope : scopes) {
                oas3Scopes.put(scope.getName(), scope.getDescription());
                oAuthFlow.setScopes(oas3Scopes);
                scopeBindings.put(scope.getName(), scope.getRoles());
            }
            oAuthFlow.addExtension(APIConstants.SWAGGER_X_SCOPES_BINDINGS, scopeBindings);
        }
        removeLegacyScopesFromSwagger(openAPI);
    }

    /**
     * Remove legacy scope from swagger
     *
     * @param openAPI
     */
    private void removeLegacyScopesFromSwagger(OpenAPI openAPI) {
        if (isLegacyExtensionsPreserved()) {
            log.debug("preserveLegacyExtensions is enabled.");
            return;
        }
        Map<String, Object> extensions = openAPI.getExtensions();
        if (extensions != null && extensions.containsKey(APIConstants.SWAGGER_X_WSO2_SECURITY)) {
            extensions.remove(APIConstants.SWAGGER_X_WSO2_SECURITY);
        }
    }

    /**
     * Add a new path based on the provided URI template to swagger if it does not exists. If it exists,
     * adds the respective operation to the existing path
     *
     * @param openAPI     swagger object
     * @param resource API resource data
     */
    private void addOrUpdatePathToSwagger(OpenAPI openAPI, SwaggerData.Resource resource) {
        PathItem path;
        if (openAPI.getPaths().get(resource.getPath()) != null) {
            path = openAPI.getPaths().get(resource.getPath());
        } else {
            path = new PathItem();
        }

        Operation operation = createOperation(resource);
        PathItem.HttpMethod httpMethod = PathItem.HttpMethod.valueOf(resource.getVerb());
        path.operation(httpMethod, operation);
        openAPI.getPaths().addPathItem(resource.getPath(), path);
    }

    /**
     * Creates a new operation object using the URI template object
     *
     * @param resource API resource data
     * @return a new operation object using the URI template object
     */
    private Operation createOperation(SwaggerData.Resource resource) {
        Operation operation = new Operation();
        populatePathParameters(operation, resource.getPath());
        updateOperationManagedInfo(resource, operation);

        ApiResponses apiResponses = new ApiResponses();
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.description("OK");
        apiResponses.addApiResponse(APIConstants.SWAGGER_RESPONSE_200, apiResponse);
        operation.setResponses(apiResponses);
        return operation;
    }

    /**
     * Updates managed info of a provided operation such as auth type and throttling
     *
     * @param resource API resource data
     * @param operation   swagger operation
     */
    private void updateOperationManagedInfo(SwaggerData.Resource resource, Operation operation) {
        String authType = resource.getAuthType();
        if (APIConstants.AUTH_APPLICATION_OR_USER_LEVEL_TOKEN.equals(authType)) {
            authType = "Application & Application User";
        }
        if (APIConstants.AUTH_APPLICATION_USER_LEVEL_TOKEN.equals(authType)) {
            authType = "Application User";
        }
        if (APIConstants.AUTH_APPLICATION_LEVEL_TOKEN.equals(authType)) {
            authType = "Application";
        }
        operation.addExtension(APIConstants.SWAGGER_X_AUTH_TYPE, authType);
        operation.addExtension(APIConstants.SWAGGER_X_THROTTLING_TIER, resource.getPolicy());

        removeLegacyScopesFromOperation(operation);
        if (resource.getScope() != null) {
            String oauth2SchemeKey = APIConstants.SWAGGER_APIM_DEFAULT_SECURITY;
            List<SecurityRequirement> security = operation.getSecurity();
            if (security == null) {
                security = new ArrayList<>();
                operation.setSecurity(security);
            }
            for (Map<String, List<String>> requirement : security) {
                if (requirement.get(oauth2SchemeKey) != null) {
                    requirement.put(oauth2SchemeKey, Arrays.asList(resource.getScope().getKey()));
                    return;
                }
            }
            // if oauth2SchemeKey not present, add a new
            SecurityRequirement defaultRequirement = new SecurityRequirement();
            defaultRequirement.put(oauth2SchemeKey, Arrays.asList(resource.getScope().getKey()));
            security.add(defaultRequirement);
        }
    }

    /**
     * Remove legacy scope information from swagger operation
     *
     * @param operation
     */
    private void removeLegacyScopesFromOperation(Operation operation) {
        if (isLegacyExtensionsPreserved()) {
            log.debug("preserveLegacyExtensions is enabled.");
            return;
        }
        Map<String, Object> extensions = operation.getExtensions();
        if (extensions != null && extensions.containsKey(APIConstants.SWAGGER_X_SCOPE)) {
            extensions.remove(APIConstants.SWAGGER_X_SCOPE);
        }
    }
}
