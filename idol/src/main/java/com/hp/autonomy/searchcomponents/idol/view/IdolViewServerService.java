/*
 * Copyright 2015 Hewlett-Packard Development Company, L.P.
 * Licensed under the MIT License (the "License"); you may not use this file except in compliance with the License.
 */

package com.hp.autonomy.searchcomponents.idol.view;


import com.autonomy.aci.client.services.AciConstants;
import com.autonomy.aci.client.services.AciErrorException;
import com.autonomy.aci.client.services.AciService;
import com.autonomy.aci.client.services.AciServiceException;
import com.autonomy.aci.client.services.Processor;
import com.autonomy.aci.client.util.AciParameters;
import com.hp.autonomy.frontend.configuration.ConfigService;
import com.hp.autonomy.frontend.configuration.ServerConfig;
import com.hp.autonomy.idolutils.processors.AciResponseJaxbProcessorFactory;
import com.hp.autonomy.idolutils.processors.CopyResponseProcessor;
import com.hp.autonomy.searchcomponents.core.view.ViewServerService;
import com.hp.autonomy.searchcomponents.idol.search.HavenSearchAciParameterHandler;
import com.hp.autonomy.searchcomponents.idol.view.configuration.ViewCapable;
import com.hp.autonomy.searchcomponents.idol.view.configuration.ViewConfig;
import com.hp.autonomy.searchcomponents.idol.view.configuration.ViewingMode;
import com.hp.autonomy.types.idol.DocContent;
import com.hp.autonomy.types.idol.GetContentResponseData;
import com.hp.autonomy.types.idol.Hit;
import com.hp.autonomy.types.requests.idol.actions.connector.ConnectorActions;
import com.hp.autonomy.types.requests.idol.actions.connector.params.ConnectorViewParams;
import com.hp.autonomy.types.requests.idol.actions.query.QueryActions;
import com.hp.autonomy.types.requests.idol.actions.view.ViewActions;
import com.hp.autonomy.types.requests.idol.actions.view.params.OutputTypeParam;
import com.hp.autonomy.types.requests.idol.actions.view.params.ViewParams;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Service
public class IdolViewServerService implements ViewServerService<String, AciErrorException> {

    public static final String AUTN_IDENTIFIER = "AUTN_IDENTIFIER";
    public static final String AUTN_GROUP = "AUTN_GROUP";

    private final AciService contentAciService;
    private final AciService viewAciService;
    private final HavenSearchAciParameterHandler parameterHandler;
    private final Processor<GetContentResponseData> getContentResponseProcessor;
    private final ConfigService<? extends ViewCapable> configService;

    @Autowired
    public IdolViewServerService(final AciService contentAciService, final AciService viewAciService, final AciResponseJaxbProcessorFactory processorFactory, final HavenSearchAciParameterHandler parameterHandler, final ConfigService<? extends ViewCapable> configService) {
        this.contentAciService = contentAciService;
        this.viewAciService = viewAciService;
        this.parameterHandler = parameterHandler;
        this.configService = configService;

        getContentResponseProcessor = processorFactory.createAciResponseProcessor(GetContentResponseData.class);
    }

    /**
     * Provides an HTML rendering of the given IDOL document reference. This first performs a GetContent to make sure the
     * document exists, then reads the configured reference field and passes the value of the field to ViewServer.
     *
     * @param documentReference The IDOL document reference of the document to view
     * @param database          The IDOL databases containing the reference (no restriction set on getContent query if this is an empty collection)
     * @param outputStream      The ViewServer output
     * @throws ViewDocumentNotFoundException If the given document reference does not exist in IDOL
     * @throws ViewNoReferenceFieldException If the document with the given reference does not have the required reference field
     * @throws ReferenceFieldBlankException  If the configured reference field name is blank
     * @throws ViewServerErrorException      If ViewServer returns a status code outside the 200 range
     */
    @Override
    public void viewDocument(final String documentReference, final String database, final String highlightExpression, final OutputStream outputStream) throws ViewDocumentNotFoundException, ViewNoReferenceFieldException, ReferenceFieldBlankException {
        final String reference = getReferenceFieldValue(documentReference, database);

        final AciParameters viewParameters = new AciParameters(ViewActions.View.name());
        viewParameters.add(ViewParams.NoACI.name(), true);
        viewParameters.add(ViewParams.Reference.name(), reference);
        viewParameters.add(ViewParams.EmbedImages.name(), true);
        viewParameters.add(ViewParams.StripScript.name(), true);
        viewParameters.add(ViewParams.OriginalBaseURL.name(), true);

        if (highlightExpression != null) {
            viewParameters.add(ViewParams.Links.name(), highlightExpression);
            viewParameters.add(ViewParams.StartTag.name(), HIGHLIGHT_START_TAG);
            viewParameters.add(ViewParams.EndTag.name(), HIGHLIGHT_END_TAG);

            // we need this because we're sending query text, not a csv of stemmed terms
            viewParameters.add(ViewParams.Boolean.name(), true);
        }

        // this prevents ViewServer from returning the raw file
        viewParameters.add(ViewParams.OutputType.name(), OutputTypeParam.HTML);
        
        try {
            viewAciService.executeAction(viewParameters, new CopyResponseProcessor(outputStream));
        } catch (final AciServiceException e) {
            throw new ViewServerErrorException(documentReference, e);
        }
    }

    @Override
    public void viewStaticContentPromotion(final String documentReference, final OutputStream outputStream) throws IOException, AciErrorException {
        throw new NotImplementedException("Viewing static content promotions on premise is not yet possible");
    }

    private String getReferenceFieldValue(final String documentReference, final String database) throws ReferenceFieldBlankException, ViewDocumentNotFoundException, ViewNoReferenceFieldException {
        final ViewConfig viewConfig = configService.getConfig().getViewConfig();
        final String referenceField = viewConfig.getReferenceField();

        final ViewingMode viewingMode = viewConfig.getViewingMode();

        // fail fast if there's a misconfiguration
        if (viewingMode == ViewingMode.FIELD && StringUtils.isEmpty(referenceField)) {
            throw new ReferenceFieldBlankException();
        }

        // do a GetContent to check for document visibility and to read out required fields
        final AciParameters parameters = new AciParameters(QueryActions.GetContent.name());
        parameterHandler.addGetContentOutputParameters(parameters, database, documentReference, referenceField);

        final GetContentResponseData queryResponse;
        try {
            queryResponse = contentAciService.executeAction(parameters, getContentResponseProcessor);
        } catch (final AciErrorException e) {
            throw new ViewDocumentNotFoundException(documentReference, e);
        }

        final List<Hit> documents = queryResponse.getHits();
        if (documents.isEmpty()) {
            throw new ViewDocumentNotFoundException(documentReference);
        }

        return getReference(documentReference, viewConfig, referenceField, viewingMode, documents);
    }

    private String getReference(final String documentReference, final ViewConfig viewConfig, final String referenceField, final ViewingMode viewingMode, final List<Hit> documents) {
        final String reference;

        switch (viewingMode) {
            case CONNECTOR:
                final String identifier = parseFieldValue(AUTN_IDENTIFIER, documents);
                final String group = parseFieldValue(AUTN_GROUP, documents);
                final ServerConfig connectorConfig = viewConfig.getConnector();

                try {
                    final URI uri = new URIBuilder()
                        .setScheme(connectorConfig.getProtocol().name().toLowerCase())
                        .setHost(connectorConfig.getHost())
                        .setPort(connectorConfig.getPort())
                        // need to set the path because of ACI's weird format
                        .setPath("/")
                        .addParameter(AciConstants.PARAM_ACTION, ConnectorActions.View.name())
                        .addParameter(ConnectorViewParams.Identifier.name(), identifier)
                        .addParameter(ConnectorViewParams.Group.name(), group)
                        .build();

                    reference = uri.toString();
                } catch (final URISyntaxException e) {
                    // this should never happen
                    throw new ConnectorUriSyntaxException("Error constructing Connector URI", e);
                }
                break;
            case FIELD:
            default:
                reference = parseFieldValue(referenceField, documents);
                if (reference == null) {
                    throw new ViewNoReferenceFieldException(documentReference, referenceField);
                }
        }

        return reference;
    }

    private String parseFieldValue(final String referenceField, final List<Hit> documents) {
        final Hit document = documents.get(0);

        String referenceFieldValue = null;
        // Assume the field names are case insensitive
        final DocContent documentContent = document.getContent();
        if (documentContent != null && CollectionUtils.isNotEmpty(documentContent.getContent())) {
            final NodeList fields = ((Node) documentContent.getContent().get(0)).getChildNodes();
            for (int i = 0; i < fields.getLength(); i++) {
                final Node field = fields.item(i);
                if (field.getLocalName().equalsIgnoreCase(referenceField)) {
                    referenceFieldValue = field.getFirstChild() == null ? null : field.getFirstChild().getNodeValue();
                    break;
                }
            }
        }
        return referenceFieldValue;
    }

}
