/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.marmotta.platform.ldp.webservices;

import org.apache.commons.lang3.StringUtils;
import org.apache.marmotta.commons.http.ContentType;
import org.apache.marmotta.commons.http.MarmottaHttpUtils;
import org.apache.marmotta.commons.vocabulary.LDP;
import org.apache.marmotta.platform.core.api.config.ConfigurationService;
import org.apache.marmotta.platform.core.api.exporter.ExportService;
import org.apache.marmotta.platform.core.api.io.MarmottaIOService;
import org.apache.marmotta.platform.core.api.triplestore.SesameService;
import org.apache.marmotta.platform.core.events.SesameStartupEvent;
import org.apache.marmotta.platform.ldp.api.LdpBinaryStoreService;
import org.apache.marmotta.platform.ldp.api.LdpService;
import org.apache.marmotta.platform.ldp.api.Preference;
import org.apache.marmotta.platform.ldp.exceptions.IncompatibleResourceTypeException;
import org.apache.marmotta.platform.ldp.exceptions.InvalidInteractionModelException;
import org.apache.marmotta.platform.ldp.exceptions.InvalidModificationException;
import org.apache.marmotta.platform.ldp.patch.InvalidPatchDocumentException;
import org.apache.marmotta.platform.ldp.patch.parser.ParseException;
import org.apache.marmotta.platform.ldp.patch.parser.RdfPatchParser;
import org.apache.marmotta.platform.ldp.util.AbstractResourceUriGenerator;
import org.apache.marmotta.platform.ldp.util.LdpUtils;
import org.apache.marmotta.platform.ldp.util.RandomUriGenerator;
import org.apache.marmotta.platform.ldp.util.SlugUriGenerator;
import org.jboss.resteasy.spi.NoLogWebApplicationException;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.model.impl.ValueFactoryImpl;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.RDFParseException;
import org.openrdf.rio.RDFWriter;
import org.openrdf.rio.RDFWriterRegistry;
import org.openrdf.rio.Rio;
import org.openrdf.rio.UnsupportedRDFormatException;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.Nodes;
import org.semanticweb.yars.turtle.TurtleParseException;
import org.semanticweb.yars.turtle.TurtleParser;
import org.slf4j.Logger;

import edu.kit.aifb.datafu.Binding;
import edu.kit.aifb.datafu.ConstructQuery;
import edu.kit.aifb.datafu.Origin;
import edu.kit.aifb.datafu.Program;
import edu.kit.aifb.datafu.consumer.impl.BindingConsumerCollection;
import edu.kit.aifb.datafu.engine.EvaluateProgram;
import edu.kit.aifb.datafu.io.origins.InternalOrigin;
import edu.kit.aifb.datafu.io.sinks.BindingConsumerSink;
import edu.kit.aifb.datafu.parser.ProgramConsumerImpl;
import edu.kit.aifb.datafu.parser.QueryConsumerImpl;
import edu.kit.aifb.datafu.parser.notation3.Notation3Parser;
import edu.kit.aifb.datafu.parser.sparql.SparqlParser;
import edu.kit.aifb.datafu.planning.EvaluateProgramConfig;
import edu.kit.aifb.datafu.planning.EvaluateProgramGenerator;
import edu.kit.aifb.ldbwebservice.STEP;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/**
 * Linked Data Platform web services.
 *
 * @see <a href="http://www.w3.org/TR/ldp/">http://www.w3.org/TR/ldp/</a>
 *
 * @author Sergio Fernández
 * @author Jakob Frank
 */
@ApplicationScoped
@Path(LdpWebService.PATH + "{local:.*}")
public class LdpWebService {

	public static final String PATH = "/ldp"; //TODO: at some point this will be root ('/') in marmotta
	public static final String LDP_SERVER_CONSTRAINTS = "http://wiki.apache.org/marmotta/LDPImplementationReport/2014-09-16";

	public static final String LINK_REL_DESCRIBEDBY = "describedby";
	public static final String LINK_REL_CONSTRAINEDBY = "http://www.w3.org/ns/ldp#constrainedBy";
	public static final String LINK_REL_CONTENT = "content";
	public static final String LINK_REL_META = "meta";
	public static final String LINK_REL_TYPE = "type";
	public static final String LINK_PARAM_ANCHOR = "anchor";
	public static final String HTTP_HEADER_SLUG = "Slug";
	public static final String HTTP_HEADER_ACCEPT_POST = "Accept-Post";
	public static final String HTTP_HEADER_ACCEPT_PATCH = "Accept-Patch";
	public static final String HTTP_HEADER_PREFER = "Prefer";
	public static final String HTTP_HEADER_PREFERENCE_APPLIED = "Preference-Applied";
	public static final String HTTP_METHOD_PATCH = "PATCH";


	public static final String PROGRAM_TRIPLE = "<http://coordinate> <http://x> \"1\" . "
			+ "<http://coordinate> <http://y> \"2\" . " + "<http://coordinate> <http://z> \"3\" . ";
	public static final String QUERY_CONSTRUCT_SPO = "CONSTRUCT { ?s ?p ?o . } WHERE { ?s ?p ?o . }";


	private Logger log = org.slf4j.LoggerFactory.getLogger(this.getClass());

	@Inject
	private ConfigurationService configurationService;

	@Inject
	private LdpService ldpService;

	@Inject
	private ExportService exportService;

	@Inject
	private SesameService sesameService;

	@Inject
	private MarmottaIOService ioService;

	@Inject
	private LdpBinaryStoreService binaryStore;

	private final List<ContentType> producedRdfTypes;
	private final Resource ldpContext = ValueFactoryImpl.getInstance().createURI(LDP.NAMESPACE);

	public LdpWebService() {
		producedRdfTypes = new ArrayList<>();

		final List<RDFFormat> availableWriters = LdpUtils.filterAvailableWriters(LdpService.SERVER_PREFERED_RDF_FORMATS);
		for(RDFFormat format : RDFWriterRegistry.getInstance().getKeys()) {
			final String primaryQ;
			final int idx = availableWriters.indexOf(format);
			if (idx < 0) {
				// not a prefered format
				primaryQ = ";q=0.5";
			} else {
				// a prefered format
				primaryQ = String.format(Locale.ENGLISH, ";q=%.1f", Math.max(1.0-(idx*0.1), 0.55));
			}

			final String secondaryQ = ";q=0.3";
			final List<String> mimeTypes = format.getMIMETypes();
			for (int i = 0; i < mimeTypes.size(); i++) {
				final String mime = mimeTypes.get(i);
				if (i == 0) {
					// first mimetype is the default
					producedRdfTypes.add(MarmottaHttpUtils.parseContentType(mime + primaryQ));
				} else {
					producedRdfTypes.add(MarmottaHttpUtils.parseContentType(mime + secondaryQ));
				}
			}
		}
		Collections.sort(producedRdfTypes);

		log.debug("Available RDF Serializer: {}", producedRdfTypes);
	}

	protected void initialize(@Observes SesameStartupEvent event) {
		log.info("Starting up LDP WebService Endpoint");
		String root = UriBuilder.fromUri(configurationService.getBaseUri()).path(LdpWebService.PATH).build().toASCIIString();
		try {
			final RepositoryConnection conn = sesameService.getConnection();
			try {
				conn.begin();
				ldpService.init(conn, conn.getValueFactory().createURI(root));
				log.debug("Created LDP root container <{}>", root);
				conn.commit();
			} finally {
				conn.close();
			}
		} catch (RepositoryException e) {
			log.error("Error creating LDP root container <{}>: {}", root, e.getMessage(), e);
		}
	}

	@GET
	public Response GET(@Context final UriInfo uriInfo,
			@HeaderParam(HttpHeaders.ACCEPT) @DefaultValue(MediaType.WILDCARD) String type,
			@HeaderParam(HTTP_HEADER_PREFER) PreferHeader preferHeader)
					throws RepositoryException {
		final String resource = ldpService.getResourceUri(uriInfo);
		log.debug("GET to LDPR <{}>", resource);
		return buildGetResponse(resource, MarmottaHttpUtils.parseAcceptHeader(type), preferHeader).build();
	}

	@HEAD
	public Response HEAD(@Context final UriInfo uriInfo,
			@HeaderParam(HttpHeaders.ACCEPT) @DefaultValue(MediaType.WILDCARD) String type,
			@HeaderParam(HTTP_HEADER_PREFER) PreferHeader preferHeader)
					throws RepositoryException {
		final String resource = ldpService.getResourceUri(uriInfo);
		log.debug("HEAD to LDPR <{}>", resource);
		return buildGetResponse(resource, MarmottaHttpUtils.parseAcceptHeader(type), preferHeader).entity(null).build();
	}

	private Response.ResponseBuilder buildGetResponse(final String resource, List<ContentType> acceptedContentTypes, PreferHeader preferHeader) throws RepositoryException {
		log.trace("LDPR requested media type {}", acceptedContentTypes);
		final RepositoryConnection conn = sesameService.getConnection();
		try {
			conn.begin();

			log.trace("Checking existence of {}", resource);
			if (!ldpService.exists(conn, resource)) {
				log.debug("{} does not exist", resource);
				final Response.ResponseBuilder resp;
				if (ldpService.isReusedURI(conn, resource)) {
					resp = createResponse(conn, Response.Status.GONE, resource);
				} else {
					resp = createResponse(conn, Response.Status.NOT_FOUND, resource);
				}
				conn.rollback();
				return resp;
			} else {
				log.trace("{} exists, continuing", resource);
			}

			// Content-Neg
			if (ldpService.isNonRdfSourceResource(conn, resource)) {
				log.trace("<{}> is marked as LDP-NR", resource);
				// LDP-NR
				final ContentType realType = MarmottaHttpUtils.parseContentType(ldpService.getMimeType(conn, resource));
				if (realType == null) {
					log.debug("<{}> has no format information - try some magic...");
					final ContentType rdfContentType = MarmottaHttpUtils.bestContentType(producedRdfTypes, acceptedContentTypes);
					if (MarmottaHttpUtils.bestContentType(MarmottaHttpUtils.parseAcceptHeader("*/*"), acceptedContentTypes) != null) {
						log.trace("Unknown type of LDP-NR <{}> is compatible with wildcard - sending back LDP-NR without Content-Type", resource);
						// Client will accept anything, send back LDP-NR
						final Response.ResponseBuilder resp = buildGetResponseBinaryResource(conn, resource, preferHeader);
						conn.commit();
						return resp;
					} else if (rdfContentType == null) {
						log.trace("LDP-NR <{}> has no type information, sending HTTP 409 with hint for wildcard 'Accept: */*'", resource);
						// Client does not look for a RDF Serialisation, send back 409 Conflict.
						log.debug("No corresponding LDP-RS found for <{}>, sending HTTP 409 with hint for wildcard 'Accept: */*'", resource);
						final Response.ResponseBuilder resp = build406Response(conn, resource, Collections.<ContentType>emptyList());
						conn.commit();
						return resp;
					} else {
						log.debug("Client is asking for a RDF-Serialisation of LDP-NS <{}>, sending meta-data", resource);
						final Response.ResponseBuilder resp = buildGetResponseSourceResource(conn, resource, Rio.getWriterFormatForMIMEType(rdfContentType.getMime(), RDFFormat.TURTLE), preferHeader);
						conn.commit();
						return resp;
					}
				} else if (MarmottaHttpUtils.bestContentType(Collections.singletonList(realType), acceptedContentTypes) == null) {
					log.trace("Client-accepted types {} do not include <{}>-s available type {} - trying some magic...", acceptedContentTypes, resource, realType);
					// requested types do not match the real type - maybe an rdf-type is accepted?
					final ContentType rdfContentType = MarmottaHttpUtils.bestContentType(producedRdfTypes, acceptedContentTypes);
					if (rdfContentType == null) {
						log.debug("Can't send <{}> ({}) in any of the accepted formats: {}, sending 406", resource, realType, acceptedContentTypes);
						final Response.ResponseBuilder resp = build406Response(conn, resource, Collections.singletonList(realType));
						conn.commit();
						return resp;
					} else {
						log.debug("Client is asking for a RDF-Serialisation of LDP-NS <{}>, sending meta-data", resource);
						final Response.ResponseBuilder resp = buildGetResponseSourceResource(conn, resource, Rio.getWriterFormatForMIMEType(rdfContentType.getMime(), RDFFormat.TURTLE), preferHeader);
						conn.commit();
						return resp;
					}
				} else {
					final Response.ResponseBuilder resp = buildGetResponseBinaryResource(conn, resource, preferHeader);
					conn.commit();
					return resp;
				}
			} else {
				// Requested Resource is a LDP-RS
				final ContentType bestType = MarmottaHttpUtils.bestContentType(producedRdfTypes, acceptedContentTypes);
				if (bestType == null) {
					log.trace("Available formats {} do not match any of the requested formats {} for <{}>, sending 406", producedRdfTypes, acceptedContentTypes, resource);
					final Response.ResponseBuilder resp = build406Response(conn, resource, producedRdfTypes);
					conn.commit();
					return resp;
				} else {
					final Response.ResponseBuilder resp = buildGetResponseSourceResource(conn, resource, Rio.getWriterFormatForMIMEType(bestType.getMime(), RDFFormat.TURTLE), preferHeader);
					conn.commit();
					return resp;
				}
			}
		} catch (final Throwable t) {
			conn.rollback();
			throw t;
		} finally {
			conn.close();
		}
	}

	private Response.ResponseBuilder build406Response(RepositoryConnection connection, String resource, List<ContentType> availableContentTypes) throws RepositoryException {
		final Response.ResponseBuilder response = createResponse(connection, Response.Status.NOT_ACCEPTABLE, resource);
		if (availableContentTypes.isEmpty()) {
			response.entity(String.format("%s is not available in the requested format%n", resource));
		} else {
			response.entity(String.format("%s is only available in the following formats: %s%n", resource, availableContentTypes));
		}
		// Sec. 4.2.2.2
		return addOptionsHeader(connection, resource, response);
	}

	private Response.ResponseBuilder buildGetResponseBinaryResource(RepositoryConnection connection, final String resource, PreferHeader preferHeader) throws RepositoryException {
		final String realType = ldpService.getMimeType(connection, resource);
		log.debug("Building response for LDP-NR <{}> with format {}", resource, realType);
		final Preference preference = LdpUtils.parsePreferHeader(preferHeader);
		final StreamingOutput entity = new StreamingOutput() {
			@Override
			public void write(OutputStream out) throws IOException, WebApplicationException {
				try {
					final RepositoryConnection outputConn = sesameService.getConnection();
					try {
						outputConn.begin();
						ldpService.exportBinaryResource(outputConn, resource, out);
						outputConn.commit();
					} catch (RepositoryException | IOException e) {
						outputConn.rollback();
						throw new WebApplicationException(e, createResponse(Response.status(Response.Status.INTERNAL_SERVER_ERROR)).entity(e).build());
					} finally {
						outputConn.close();
					}
				} catch (RepositoryException e) {
					throw new WebApplicationException(e, createResponse(Response.status(Response.Status.INTERNAL_SERVER_ERROR)).entity(e).build());
				}
			}
		};
		// Sec. 4.2.2.2
		final Response.ResponseBuilder resp = addOptionsHeader(connection, resource, createResponse(connection, Response.Status.OK, resource).entity(entity).type(realType));
		if (preferHeader != null) {
			if (preference.isMinimal()) {
				resp.status(Response.Status.NO_CONTENT).entity(null).header(HTTP_HEADER_PREFERENCE_APPLIED, PreferHeader.fromPrefer(preferHeader).parameters(null).build());
			}
		}
		return resp;
	}

	private Response.ResponseBuilder buildGetResponseSourceResource(RepositoryConnection conn, final String resource, final RDFFormat format, final PreferHeader preferHeader) throws RepositoryException {
		// Deliver all triples from the <subject> context.
		log.debug("Building response for LDP-RS <{}> with RDF format {}", resource, format.getDefaultMIMEType());
		final Preference preference = LdpUtils.parsePreferHeader(preferHeader);
		final StreamingOutput entity = new StreamingOutput() {
			@Override
			public void write(OutputStream output) throws IOException, WebApplicationException {
				try {
					final RepositoryConnection outputConn = sesameService.getConnection();
					try {
						outputConn.begin();
						ldpService.exportResource(outputConn, resource, output, format, preference);
						outputConn.commit();
					} catch (RDFHandlerException e) {
						outputConn.rollback();
						throw new NoLogWebApplicationException(e, createResponse(Response.status(Response.Status.INTERNAL_SERVER_ERROR)).entity(e.getMessage()).build());
					} catch (final Throwable t) {
						outputConn.rollback();
						throw t;
					} finally {
						outputConn.close();
					}
				} catch (RepositoryException e) {
					throw new WebApplicationException(e, createResponse(Response.status(Response.Status.INTERNAL_SERVER_ERROR)).entity(e).build());
				}
			}
		};
		// Sec. 4.2.2.2
		final Response.ResponseBuilder resp = addOptionsHeader(conn, resource, createResponse(conn, Response.Status.OK, resource).entity(entity).type(format.getDefaultMIMEType()));
		if (preference != null) {
			if (preference.isMinimal()) {
				resp.status(Response.Status.NO_CONTENT).entity(null);
			}
			resp.header(HTTP_HEADER_PREFERENCE_APPLIED, PreferHeader.fromPrefer(preferHeader).parameters(null).build());
		}
		return resp;
	}



	/**********************************************************************************************************************
	 * LDP Post Request.
	 *
	 * @see <a href="https://dvcs.w3.org/hg/ldpwg/raw-file/default/ldp.html#ldpr-HTTP_POST">5.4 LDP-R POST</a>
	 * @see <a href="https://dvcs.w3.org/hg/ldpwg/raw-file/default/ldp.html#ldpc-HTTP_POST">6.4 LDP-C POST</a>
	 **********************************************************************************************************************/
	@POST
	public Response POST(@Context UriInfo uriInfo, @HeaderParam(HTTP_HEADER_SLUG) String slug,
			@HeaderParam(HttpHeaders.LINK) List<Link> linkHeaders,
			InputStream postBody, @HeaderParam(HttpHeaders.CONTENT_TYPE) MediaType type)
					throws RepositoryException {

		final String container = ldpService.getResourceUri(uriInfo);
		log.debug("POST to LDPC <{}>", container);

		final RepositoryConnection conn = sesameService.getConnection();
		try {
			conn.begin();

			if (!ldpService.exists(conn, container)) {
				final Response.ResponseBuilder resp;
				if (ldpService.isReusedURI(conn, container)) {
					log.debug("<{}> has been deleted, can't POST to it!", container);
					resp = createResponse(conn, Response.Status.GONE, container);
				} else {
					log.debug("<{}> does not exists, can't POST to it!", container);
					resp = createResponse(conn, Response.Status.NOT_FOUND, container);
				}
				conn.rollback();
				return resp.build();
			}



			if ( ldpService.isStartAPI(conn, container) ) {
				log.debug("<{}> exists and is a LinkedDataWebService, so this triggers the service", container);


				//RepositoryResult<Statement> statements = conn.getStatements( ValueFactoryImpl.getInstance().createURI(resource), ValueFactoryImpl.getInstance().createURI("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"), null, true, new Resource[0]);

				final Response.ResponseBuilder resp = createWebServiceResponse(conn, 200, container, postBody);

				log.debug("PUT update for <{}> successful", container);
				conn.commit();
				return resp.build();

			}



			// Check that the target container supports the LDPC Interaction Model
			final LdpService.InteractionModel containerModel = ldpService.getInteractionModel(conn, container);
			if (containerModel != LdpService.InteractionModel.LDPC) {
				final Response.ResponseBuilder response = createResponse(conn, Response.Status.METHOD_NOT_ALLOWED, container);
				conn.commit();
				return response.entity(String.format("%s only supports %s Interaction Model", container, containerModel)).build();
			}

			// Get the LDP-Interaction Model (Sec. 5.2.3.4 and Sec. 4.2.1.4)
			final LdpService.InteractionModel ldpInteractionModel = ldpService.getInteractionModel(linkHeaders);

			if (ldpService.isNonRdfSourceResource(conn, container)) {
				log.info("POSTing to a NonRdfSource is not allowed ({})", container);
				final Response.ResponseBuilder response = createResponse(conn, Response.Status.METHOD_NOT_ALLOWED, container).entity("POST to NonRdfSource is not allowed\n");
				conn.commit();
				return response.build();
			}

			final AbstractResourceUriGenerator uriGenerator;
			if (StringUtils.isBlank(slug)) {
				/* Sec. 5.2.3.8) */
				uriGenerator = new RandomUriGenerator(ldpService, container, conn);
			} else {
				// Honor client wishes from Slug-header (Sec. 5.2.3.10)
				//    http://www.ietf.org/rfc/rfc5023.txt
				log.trace("Slug-Header is '{}'", slug);
				uriGenerator = new SlugUriGenerator(ldpService, container, slug, conn);
			}

			final String newResource = uriGenerator.generateResourceUri();

			log.debug("POST to <{}> will create new LDP-R <{}>", container, newResource);
			// connection is closed by buildPostResponse
			return buildPostResponse(conn, container, newResource, ldpInteractionModel, postBody, type);
		} catch (InvalidInteractionModelException e) {
			log.debug("POST with invalid interaction model <{}> to <{}>", e.getHref(), container);
			final Response.ResponseBuilder response = createResponse(conn, Response.Status.BAD_REQUEST, container);
			conn.commit();
			return response.entity(e.getMessage()).build();
		} catch (IllegalArgumentException e) {
			log.debug("POST with invalid body content: {}", container);
			final Response.ResponseBuilder response = createResponse(conn, Response.Status.BAD_REQUEST, container);
			conn.commit();
			return response.entity(e.getMessage()).build();
		} catch (final Throwable t) {
			conn.rollback();
			throw t;
		} finally {
			conn.close();
		}
	}

	/**
	 * @param connection the RepositoryConnection (with active transaction) to read extra data from. WILL BE COMMITTED OR ROLLBACKED
	 * @throws RepositoryException
	 */
	private Response buildPostResponse(RepositoryConnection connection, String container, String newResource, LdpService.InteractionModel interactionModel, InputStream requestBody, MediaType type) throws RepositoryException {
		final String mimeType = LdpUtils.getMimeType(type);
		//checking if resource (container) exists is done later in the service
		try {
			String location = ldpService.addResource(connection, container, newResource, interactionModel, mimeType, requestBody);
			final Response.ResponseBuilder response = createResponse(connection, Response.Status.CREATED, container).location(java.net.URI.create(location));
			if (newResource.compareTo(location) != 0) {
				response.links(Link.fromUri(newResource).rel(LINK_REL_DESCRIBEDBY).param(LINK_PARAM_ANCHOR, location).build());
			}
			connection.commit();
			return response.build();
		} catch (IOException | RDFParseException e) {
			final Response.ResponseBuilder resp = createResponse(connection, Response.Status.BAD_REQUEST, container).entity(e.getClass().getSimpleName() + ": " + e.getMessage());
			connection.rollback();
			return resp.build();
		} catch (UnsupportedRDFormatException e) {
			final Response.ResponseBuilder resp = createResponse(connection, Response.Status.UNSUPPORTED_MEDIA_TYPE, container).entity(e);
			connection.rollback();
			return resp.build();
		}
	}



	/*************************************************************************************************************
	 * 
	 * Handle PUT (Sec. 4.2.4, Sec. 5.2.4)
	 * 
	 *************************************************************************************************************/
	@PUT
	public Response PUT(@Context UriInfo uriInfo, @Context Request request,
			@HeaderParam(HttpHeaders.LINK) List<Link> linkHeaders,
			@HeaderParam(HttpHeaders.IF_MATCH) EntityTag eTag,
			@HeaderParam(HttpHeaders.CONTENT_TYPE) MediaType type, InputStream postBody)
					throws RepositoryException, IOException, InvalidModificationException, RDFParseException, IncompatibleResourceTypeException, URISyntaxException {
		final String resource = ldpService.getResourceUri(uriInfo);
		log.debug("PUT to <{}>", resource);

		final RepositoryConnection conn = sesameService.getConnection();
		try {
			conn.begin();

			final String mimeType = LdpUtils.getMimeType(type);
			final Response.ResponseBuilder resp;
			final String newResource;  // NOTE: newResource == resource for now, this might change in the future
			if (ldpService.exists(conn, resource) ) {


				log.debug("<{}> exists and is a DataResource, so this is an UPDATE", resource);

				if (eTag == null) {
					// check for If-Match header (ETag) -> 428 Precondition Required (Sec. 4.2.4.5)
					log.trace("No If-Match header, but that's a MUST");
					resp = createResponse(conn, 428, resource);
					conn.rollback();
					return resp.build();
				} else {
					// check ETag -> 412 Precondition Failed (Sec. 4.2.4.5)
					log.trace("Checking If-Match: {}", eTag);
					EntityTag hasTag = ldpService.generateETag(conn, resource);
					if (!eTag.equals(hasTag)) {
						log.trace("If-Match header did not match, expected {}", hasTag);
						resp = createResponse(conn, Response.Status.PRECONDITION_FAILED, resource);
						conn.rollback();
						return resp.build();
					}
				}

				newResource = ldpService.updateResource(conn, resource, postBody, mimeType);
				log.debug("PUT update for <{}> successful", newResource);
				resp = createResponse(conn, Response.Status.OK, resource);
				conn.commit();
				return resp.build();



			} else if (ldpService.isReusedURI(conn, resource)) {
				log.debug("<{}> has been deleted, we should not re-use the URI!", resource);
				resp = createResponse(conn, Response.Status.GONE, resource);
				conn.commit();
				return resp.build();
			} else {
				log.debug("<{}> does not exist, so this is a CREATE", resource);
				//LDP servers may allow resource creation using PUT (Sec. 4.2.4.6)

				final String container = LdpUtils.getContainer(resource);
				try {
					// Check that the target container supports the LDPC Interaction Model
					final LdpService.InteractionModel containerModel = ldpService.getInteractionModel(conn, container);
					if (containerModel != LdpService.InteractionModel.LDPC) {
						final Response.ResponseBuilder response = createResponse(conn, Response.Status.METHOD_NOT_ALLOWED, container);
						conn.commit();
						return response.entity(String.format("%s only supports %s Interaction Model", container, containerModel)).build();
					}

					// Get the LDP-Interaction Model (Sec. 5.2.3.4 and Sec. 4.2.1.4)
					final LdpService.InteractionModel ldpInteractionModel = ldpService.getInteractionModel(linkHeaders);

					// connection is closed by buildPostResponse
					return buildPostResponse(conn, container, resource, ldpInteractionModel, postBody, type);
				} catch (InvalidInteractionModelException e) {
					log.debug("PUT with invalid interaction model <{}> to <{}>", e.getHref(), container);
					final Response.ResponseBuilder response = createResponse(conn, Response.Status.BAD_REQUEST, container);
					conn.commit();
					return response.entity(e.getMessage()).build();
				}
			}
		} catch (IOException | RDFParseException e) {
			final Response.ResponseBuilder resp = createResponse(conn, Response.Status.BAD_REQUEST, resource).entity(e.getClass().getSimpleName() + ": " + e.getMessage());
			conn.rollback();
			return resp.build();
		} catch (InvalidModificationException | IncompatibleResourceTypeException e) {
			final Response.ResponseBuilder resp = createResponse(conn, Response.Status.CONFLICT, resource).entity(e.getClass().getSimpleName() + ": " + e.getMessage());
			conn.rollback();
			return resp.build();
		} catch (final Throwable t) {
			conn.rollback();
			throw t;
		} finally {
			conn.close();
		}
	}





	/**
	 * Handle delete (Sec. 4.2.5, Sec. 5.2.5)
	 */
	@DELETE
	public Response DELETE(@Context UriInfo uriInfo) throws RepositoryException {
		final String resource = ldpService.getResourceUri(uriInfo);
		log.debug("DELETE to <{}>", resource);

		final RepositoryConnection con = sesameService.getConnection();
		try {
			con.begin();

			if (!ldpService.exists(con, resource)) {
				final Response.ResponseBuilder resp;
				if (ldpService.isReusedURI(con, resource)) {
					resp = createResponse(con, Response.Status.GONE, resource);
				} else {
					resp = createResponse(con, Response.Status.NOT_FOUND, resource);
				}
				con.rollback();
				return resp.build();
			}

			ldpService.deleteResource(con, resource);
			final Response.ResponseBuilder resp = createResponse(con, Response.Status.NO_CONTENT, resource);
			con.commit();
			return resp.build();
		} catch (final Throwable e) {
			log.error("Error deleting LDP-R: {}: {}", resource, e.getMessage());
			con.rollback();
			throw e;
		} finally {
			con.close();
		}

	}

	@PATCH
	public Response PATCH(@Context UriInfo uriInfo,
			@HeaderParam(HttpHeaders.IF_MATCH) EntityTag eTag,
			@HeaderParam(HttpHeaders.CONTENT_TYPE) MediaType type, InputStream postBody) throws RepositoryException {
		final String resource = ldpService.getResourceUri(uriInfo);
		log.debug("PATCH to <{}>", resource);

		final RepositoryConnection con = sesameService.getConnection();
		try {
			con.begin();

			if (!ldpService.exists(con, resource)) {
				final Response.ResponseBuilder resp;
				if (ldpService.isReusedURI(con, resource)) {
					resp = createResponse(con, Response.Status.GONE, resource);
				} else {
					resp = createResponse(con, Response.Status.NOT_FOUND, resource);
				}
				con.rollback();
				return resp.build();
			}

			if (eTag != null) {
				// check ETag if present
				log.trace("Checking If-Match: {}", eTag);
				EntityTag hasTag = ldpService.generateETag(con, resource);
				if (!eTag.equals(hasTag)) {
					log.trace("If-Match header did not match, expected {}", hasTag);
					final Response.ResponseBuilder resp = createResponse(con, Response.Status.PRECONDITION_FAILED, resource);
					con.rollback();
					return resp.build();
				}
			}

			// Check for the supported mime-type
			if (!type.toString().equals(RdfPatchParser.MIME_TYPE)) {
				log.trace("Incompatible Content-Type for PATCH: {}", type);
				final Response.ResponseBuilder resp = createResponse(con, Response.Status.UNSUPPORTED_MEDIA_TYPE, resource).entity("Unknown Content-Type: " + type + "\n");
				con.rollback();
				return resp.build();
			}

			try {
				ldpService.patchResource(con, resource, postBody, false);
				final Response.ResponseBuilder resp = createResponse(con, Response.Status.NO_CONTENT, resource);
				con.commit();
				return resp.build();
			} catch (ParseException | InvalidPatchDocumentException e) {
				final Response.ResponseBuilder resp = createResponse(con, Response.Status.BAD_REQUEST, resource).entity(e.getMessage() + "\n");
				con.rollback();
				return resp.build();
			} catch (InvalidModificationException e) {
				final Response.ResponseBuilder resp = createResponse(con, 422, resource).entity(e.getMessage() + "\n");
				con.rollback();
				return resp.build();
			}

		} catch (final Throwable t) {
			con.rollback();
			throw t;
		} finally {
			con.close();
		}
	}

	/**
	 * Handle OPTIONS (Sec. 4.2.8, Sec. 5.2.8)
	 */
	@OPTIONS
	public Response OPTIONS(@Context final UriInfo uriInfo) throws RepositoryException {
		final String resource = ldpService.getResourceUri(uriInfo);
		log.debug("OPTIONS to <{}>", resource);

		final RepositoryConnection con = sesameService.getConnection();
		try {
			con.begin();

			if (!ldpService.exists(con, resource)) {
				final Response.ResponseBuilder resp;
				if (ldpService.isReusedURI(con, resource)) {
					resp = createResponse(con, Response.Status.GONE, resource);
				} else {
					resp = createResponse(con, Response.Status.NOT_FOUND, resource);
				}
				con.rollback();
				return resp.build();
			}


			Response.ResponseBuilder builder = createResponse(con, Response.Status.OK, resource);

			addOptionsHeader(con, resource, builder);

			con.commit();
			return builder.build();
		} catch (final Throwable t) {
			con.rollback();
			throw t;
		} finally {
			con.close();
		}

	}

	private Response.ResponseBuilder addOptionsHeader(RepositoryConnection connection, String resource, Response.ResponseBuilder builder) throws RepositoryException {
		log.debug("Adding required LDP Headers (OPTIONS, GET); see Sec. 8.2.8 and Sec. 4.2.2.2");
		if (ldpService.isNonRdfSourceResource(connection, resource)) {
			// Sec. 4.2.8.2
			log.trace("<{}> is an LDP-NR: GET, HEAD, PUT and OPTIONS allowed", resource);
			builder.allow(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.PUT, HttpMethod.OPTIONS);
		} else if (ldpService.isRdfSourceResource(connection, resource)) {
			if (ldpService.getInteractionModel(connection, resource) == LdpService.InteractionModel.LDPR) {
				log.trace("<{}> is a LDP-RS (LDPR interaction model): GET, HEAD, PUT, PATCH and OPTIONS allowed", resource);
				// Sec. 4.2.8.2
				builder.allow(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.PUT, HTTP_METHOD_PATCH, HttpMethod.OPTIONS);
			} else {
				// Sec. 4.2.8.2
				log.trace("<{}> is a LDP-RS (LDPC interaction model): GET, HEAD, POST, PUT, PATCH and OPTIONS allowed", resource);
				builder.allow(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.POST, HttpMethod.PUT, HTTP_METHOD_PATCH, HttpMethod.OPTIONS);
				// Sec. 4.2.3 / Sec. 5.2.3
				builder.header(HTTP_HEADER_ACCEPT_POST, LdpUtils.getAcceptPostHeader("*/*"));
			}
			// Sec. 4.2.7.1
			builder.header(HTTP_HEADER_ACCEPT_PATCH, RdfPatchParser.MIME_TYPE);
		}

		return builder;
	}

	/**
	 * Add all the default headers specified in LDP to the Response
	 *
	 * @param connection the RepositoryConnection (with active transaction) to read extra data from
	 * @param status the StatusCode
	 * @param resource the iri/uri/url of the resource
	 * @return the provided ResponseBuilder for chaining
	 */
	protected Response.ResponseBuilder createResponse(RepositoryConnection connection, Response.Status status, String resource) throws RepositoryException {
		return createResponse(connection, status.getStatusCode(), resource);
	}

	/**
	 * Add all the default headers specified in LDP to the Response
	 *
	 * @param connection the RepositoryConnection (with active transaction) to read extra data from
	 * @param status the status code
	 * @param resource the uri/url of the resource
	 * @return the provided ResponseBuilder for chaining
	 */
	protected Response.ResponseBuilder createResponse(RepositoryConnection connection, int status, String resource) throws RepositoryException {
		return createResponse(connection, Response.status(status), resource);
	}

	/**
	 * Add all the default headers specified in LDP to the Response
	 *
	 * @param connection the RepositoryConnection (with active transaction) to read extra data from
	 * @param rb the ResponseBuilder
	 * @param resource the uri/url of the resource
	 * @return the provided ResponseBuilder for chaining
	 */
	protected Response.ResponseBuilder createResponse(RepositoryConnection connection, Response.ResponseBuilder rb, String resource) throws RepositoryException {
		createResponse(rb);

		if (ldpService.exists(connection, resource)) {
			// Link rel='type' (Sec. 4.2.1.4, 5.2.1.4)
			List<Statement> statements = ldpService.getLdpTypes(connection, resource);
			for (Statement stmt : statements) {
				Value o = stmt.getObject();
				if (o instanceof URI && o.stringValue().startsWith(LDP.NAMESPACE)) {
					rb.link(o.stringValue(), LINK_REL_TYPE);
				}
			}

			final URI rdfSource = ldpService.getRdfSourceForNonRdfSource(connection, resource);
			if (rdfSource != null) {
				// Sec. 5.2.8.1 and 5.2.3.12
				rb.link(rdfSource.stringValue(), LINK_REL_DESCRIBEDBY);
				// This is not covered by the Spec, but is very convenient to have
				rb.link(rdfSource.stringValue(), LINK_REL_META);
			}
			final URI nonRdfSource = ldpService.getNonRdfSourceForRdfSource(connection, resource);
			if (nonRdfSource != null) {
				// This is not covered by the Spec, but is very convenient to have
				rb.link(nonRdfSource.stringValue(), LINK_REL_CONTENT);
			}

			// ETag (Sec. 4.2.1.3)
			rb.tag(ldpService.generateETag(connection, resource));

			// Last modified date
			rb.lastModified(ldpService.getLastModified(connection, resource));
		}

		return rb;
	}

	/**
	 * Add the non-resource related headers specified in LDP to the provided ResponseBuilder
	 * @param rb the ResponseBuilder to decorate
	 * @return the updated ResponseBuilder for chaining
	 */
	protected Response.ResponseBuilder createResponse(Response.ResponseBuilder rb) {
		// Link rel='http://www.w3.org/ns/ldp#constrainedBy' (Sec. 4.2.1.6)
		rb.link(LDP_SERVER_CONSTRAINTS, LINK_REL_CONSTRAINEDBY);

		return rb;
	}




	/**
	 * Add all the default headers specified in LDP to the Response
	 *
	 * @param connection the RepositoryConnection (with active transaction) to read extra data from
	 * @param status the StatusCode
	 * @param resource the iri/uri/url of the resource
	 * @return the provided ResponseBuilder for chaining
	 */
	protected Response.ResponseBuilder createWebServiceResponse(RepositoryConnection connection, Response.Status status, String resource, InputStream input_data) throws RepositoryException {
		return createWebServiceResponse(connection, status.getStatusCode(), resource, input_data);
	}


	protected Response.ResponseBuilder createWebServiceResponse(RepositoryConnection connection, int status, String resource, InputStream body) throws RepositoryException {
		return createWebServiceResponse(connection, Response.status(status), resource, body);
	}


	/**
	 * 
	 *
	 * @param connection the RepositoryConnection (with active transaction) to read extra data from
	 * @param rb the ResponseBuilder
	 * @param resource the uri/url of the resource
	 * @return the provided ResponseBuilder for chaining
	 */
	protected Response.ResponseBuilder createWebServiceResponse(RepositoryConnection connection, Response.ResponseBuilder rb, String resource, InputStream body) throws RepositoryException, IllegalArgumentException {
		createWebServiceResponse(rb);

		if (ldpService.exists(connection, resource)) {

			try {

				RepositoryResult<Statement> services = connection.getStatements( 
						null, 
						STEP.hasStartAPI, 
						ValueFactoryImpl.getInstance().createURI(resource), 
						true, 
						new Resource[0]);

				if (!services.hasNext()) {
					log.debug("Could not find any connected service to <{}>", resource);
					return rb.status(Response.Status.EXPECTATION_FAILED).entity("Could not find any connected service!");
				}
				URI service = cleanURI((URI) services.next().getSubject() );
				if (services.hasNext()) {
					// do nothing yet
					// TODO: handle multiple services with same startAPI
				}

				RepositoryResult<Statement> programs = connection.getStatements(service, STEP.hasProgram, null, true, new Resource[0]);
				if (!programs.hasNext()) {
					log.debug("Could not find any connected service to <{}>", resource);
					return rb.status(Response.Status.EXPECTATION_FAILED).entity("Could not find any connected program!");
				}
				// TODO get Program as file
				//OutputStream program_data = new ByteArrayOutputStream();
				URI program = new URIImpl(programs.next().getObject().stringValue());
				InputStream program_data = binaryStore.read(program);
				//ldpService.exportBinaryResource(connection, program, program_data);
				if (programs.hasNext()) {
					// do nothing yet
					// TODO: handle multiple programs with same WebService
				}

				final Collection<Statement> output_data = executeWebService(service, program_data, null, body);

				StreamingOutput entity = new StreamingOutput() {
					@Override
					public void write(OutputStream output) throws IOException, WebApplicationException {

						RDFFormat serializer = ioService.getSerializer("text/turtle");
						RDFWriter handler = Rio.createWriter(serializer,output);
						try {
							handler.startRDF();
							for (Statement statement : output_data) {
								handler.handleStatement(statement);
							}
							handler.endRDF();
						} catch (RDFHandlerException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}


					}
				};

				rb.entity(entity);

			} catch (RepositoryException | IOException e) {
				return rb.status(Response.Status.EXPECTATION_FAILED).entity("Necessary preconditions (N3-Program, Query) missing.");
			}

		}



		return rb;
	}


	/**
	 * returns an URI without an ending slash 
	 * @param uri
	 * @return
	 */
	private URI cleanURI(URI uri) {
		String str = uri.toString();

		if (str.endsWith("/")) {
			String clean_uri = str.substring(0, str.length() - 1);
			return new URIImpl(clean_uri);
		} else {
			return uri;
		}
	}

	private Collection<Statement> executeWebService(Resource resource, InputStream program_data, String query, InputStream body) throws IllegalArgumentException {

		Collection<Statement> results = new ArrayList<Statement>();
		

		ValueFactory factory = ValueFactoryImpl.getInstance();

		/*
		 * Write HTTP request body input to request output
		 *
		BufferedReader br = new BufferedReader(new InputStreamReader(input_data) );
		RDFFormat serializer = ioService.getSerializer("text/turtle");
		try {
			Model model = Rio.parse(input_data, resource.stringValue(), RDFFormat.TURTLE, new Resource[0]);
			Collection<Statement> result = new HashSet<Statement>();
			Iterator<Statement> iter = model.iterator();
			while (iter.hasNext()) {
				result.add(iter.next());
			}
			return result;
		} catch (RDFParseException | UnsupportedRDFormatException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		 */


		/*
		 * Write "_:this a step:Output . _:this step:hasValue "48" ."
		 * 
		ValueFactory factory = ValueFactoryImpl.getInstance();
		Collection<Statement> result = new HashSet<Statement>();

		Resource this_resource = factory.createBNode("this");
		result.add(factory.createStatement(this_resource, RDF.TYPE, STEP.Output));
		result.add(factory.createStatement(this_resource, STEP.hasValue, factory.createLiteral(48)));

		return result;
		 */

		/*
		 * Linked Data-Fu execution
		 */

		try {

			/*
			 * Generate a Program Object
			 */
			Origin program_origin = new InternalOrigin("programOriginTriple");
			ProgramConsumerImpl programConsumer = new ProgramConsumerImpl(program_origin);
			
			Notation3Parser notation3Parser = new Notation3Parser(program_data);
			notation3Parser.parse(programConsumer, program_origin);
			Program program = programConsumer.getProgram(program_origin);
			

			
			/*
			 * Generate a Graph Object
			 */
			try {
			TurtleParser turtleParser = new TurtleParser();
			turtleParser.parse(body, Charset.defaultCharset(), new java.net.URI( resource.stringValue() ) );
			
			
			while(turtleParser.hasNext()) {
				Node[] node = turtleParser.next();
				Nodes nodes = new Nodes(node);
				program.addTriple(nodes);
			}
			

			
			} catch (TurtleParseException | org.semanticweb.yars.turtle.ParseException e) {
				throw new IllegalArgumentException();
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException();
			}

			
			/*
			 *  Register a Query		
			 */
			QueryConsumerImpl qc = new QueryConsumerImpl(new InternalOrigin("query_consumer_1"));
			String s = new String("CONSTRUCT { ?s ?p ?o . } WHERE { ?s ?p ?o . }");
			SparqlParser sp = new SparqlParser(new StringReader(s));

			sp.parse(qc, new InternalOrigin("SparqlConstructDummy"));
			ConstructQuery sq = qc.getConstructQueries().iterator().next();


			BindingConsumerCollection bc = new BindingConsumerCollection(); 
			BindingConsumerSink sink = new BindingConsumerSink(bc);

			program.registerConstructQuery(sq, sink);
			
			
			
			/*
			 * 	Create an EvaluateProgram Object
			 */
			EvaluateProgramConfig config = new EvaluateProgramConfig();
			EvaluateProgramGenerator ep = new EvaluateProgramGenerator(program, config);
			EvaluateProgram epg = ep.getEvaluateProgram();

			
			
			/*
			 * 	Evaluate the Program
			 */
			epg.start();
			
			epg.awaitIdleAndFinish();

			epg.shutdown();
			
			
			for (Binding binding : bc.getCollection() ) {

				Nodes nodes = binding.getNodes();
				Node[] node = nodes.getNodeArray();

				String subj_string = node[0].toString().replace("<", "").replace(">", "").replace("\"", "");
				Resource subject;
				
				
				if (subj_string.startsWith("_")) {
					
					// is BlankNode
					subject = factory.createBNode( subj_string.replace("_:", "") );
					
				} else {
					
					subject = factory.createURI( subj_string ); 
					
				}

				
				String predicate_string = node[1].toString().replace("<", "").replace(">", "").replace("\"", "");
				URI predicate = factory.createURI( predicate_string ); 


				String object_string = node[2].toString().replace("<", "").replace(">", "").replace("\"", "");
				try {
					
					
					Value object = factory.createURI( object_string ); 
					results.add( factory.createStatement(subject, predicate, object) );
					
					
				} catch (IllegalArgumentException e) {
					
					Value object = factory.createLiteral( object_string ); 
					results.add( factory.createStatement(subject, predicate, object) );
					
				}

				
				
				
			}






			//************************************************************************************************//




			// Parse programs
			//			Origin program_origin = new InternalOrigin("programOriginTriple");
			//			ProgramConsumerImpl programConsumer = new ProgramConsumerImpl(program_origin);
			//			Notation3Parser notation3Parser = new Notation3Parser(new ByteArrayInputStream(pro.getBytes()));
			//			//			Notation3Parser notation3Parser = new Notation3Parser(program_data);
			//			notation3Parser.parse(programConsumer, program_origin);
			//			//Program program = programConsumer.getProgram(program_origin);
			//			program = programConsumer.getProgram(program_origin);

			// Parse query
			/*			Origin origin  = new InternalOrigin("queryOrigin");

			QueryConsumerImpl queryConsumer = new QueryConsumerImpl(origin);
			SparqlParser parser = new SparqlParser(new ByteArrayInputStream(QUERY_CONSTRUCT_SPO.getBytes()));
			parser.parse(queryConsumer, origin);
			Set<Query> queries = new HashSet<Query>();
			Set<ConstructQuery> constructQueries = queryConsumer.getConstructQueries();
			queries.addAll(constructQueries);
			Set<SelectQuery> selectQueries = queryConsumer.getSelectQueries();
			queries.addAll(selectQueries);

			// Create instance
			Instance instance = new Instance();
			instance.setDelay(1000);

			// Add program to instance
			instance.putProgram("programTest", program);
			instance.putConstructQueries("constructQueries", constructQueries);

			// BindingConsumerCollection bindingConsumerCollection =
			// instance.evaluateQueries(queries);
			BindingConsumerCollection bindingConsumerCollection = instance
					.getConstructQueryConsumer("constructQueries");

			for (Binding binding : bindingConsumerCollection.getCollection() ) {

				Nodes nodes = binding.getNodes();
				Node[] node = nodes.getNodeArray();

				URI subject = ValueFactoryImpl.getInstance().createURI( node[0].toString() ); 
				URI predicate = ValueFactoryImpl.getInstance().createURI( node[1].toString() ); 
				try {
					Value object = ValueFactoryImpl.getInstance().createURI( node[2].toString() ); 
					results.add( ValueFactoryImpl.getInstance().createStatement(subject, predicate, object) );
				} catch (IllegalArgumentException e) {
					Value object = ValueFactoryImpl.getInstance().createLiteral( node[2].toString() ); 
					results.add( ValueFactoryImpl.getInstance().createStatement(subject, predicate, object) );
				}

			}
			 */

		} catch (edu.kit.aifb.datafu.parser.sparql.ParseException e) {
			// TODO: handle exception
		} catch (edu.kit.aifb.datafu.parser.notation3.ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		return results;
	}

	protected Response.ResponseBuilder createWebServiceResponse(Response.ResponseBuilder rb) {
		// Link rel='http://www.w3.org/ns/ldp#constrainedBy' (Sec. 4.2.1.6)
		rb.link(LDP_SERVER_CONSTRAINTS, LINK_REL_CONSTRAINEDBY);

		return rb;
	}

	public String getStringFromInputStream(InputStream stream) {
		String pro = "";
		Scanner scanner = new Scanner(stream,"UTF-8");
		while (scanner.hasNextLine()) {
			pro += scanner.nextLine() + "\n";
		}
		scanner.close();
		return pro;
	} 



	public static Program getProgramTriple() throws ParseException, edu.kit.aifb.datafu.parser.notation3.ParseException {
		Origin origin = new InternalOrigin("programOriginTriple");
		ProgramConsumerImpl programConsumer = new ProgramConsumerImpl(origin);
		Notation3Parser notation3Parser = new Notation3Parser(new ByteArrayInputStream(PROGRAM_TRIPLE.getBytes()));
		notation3Parser.parse(programConsumer, origin);
		return programConsumer.getProgram(origin);
	}
}
